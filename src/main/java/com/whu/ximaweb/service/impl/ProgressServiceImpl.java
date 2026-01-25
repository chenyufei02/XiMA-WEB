package com.whu.ximaweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.ximaweb.dto.Coordinate;
import com.whu.ximaweb.mapper.*;
import com.whu.ximaweb.model.*;
import com.whu.ximaweb.service.ProgressService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 进度管理服务核心实现 (V12 - 最终完整融合版)
 * 包含：
 * 1. 上传进度追踪 (保留原业务)
 * 2. 施工进度智能计算算法 (V12新算法：混合清洗[中位数/P25] -> 距离分层 -> 均值迭代 -> H2智能校验 -> 棘轮修正)
 * 3. Navisworks 状态分析与楼层换算 (保留原业务)
 */
@Service
public class ProgressServiceImpl implements ProgressService {

    // =========================================================
    // 1. 上传进度管理 (保留原业务逻辑)
    // =========================================================
    private final Map<String, Integer> progressMap = new ConcurrentHashMap<>();
    private final Map<String, String> statusMap = new ConcurrentHashMap<>();

    @Autowired
    private ProjectPhotoMapper projectPhotoMapper;

    @Autowired
    private SysBuildingMapper sysBuildingMapper;

    @Autowired
    private ActualProgressMapper actualProgressMapper;

    @Autowired
    private SysProjectMapper sysProjectMapper;

    @Autowired
    private PlanProgressMapper planProgressMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BuildingFloorInfoMapper floorInfoMapper;

    @Override
    public void updateProgress(String key, int percent) {
        progressMap.put(key, percent);
    }

    @Override
    public Integer getProgress(String key) {
        return progressMap.getOrDefault(key, 0);
    }

    @Override
    public void removeProgress(String key) {
        progressMap.remove(key);
        statusMap.remove(key);
    }

    @Override
    public void updateStatus(String key, String status) {
        statusMap.put(key, status);
    }

    @Override
    public String getStatus(String key) {
        return statusMap.getOrDefault(key, "");
    }

    // =========================================================
    // 2. 核心业务：全自动施工进度计算 (V12 升级版)
    // =========================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateProjectProgress(Integer projectId) {
        System.out.println(">>> 开始执行进度计算 (V12)，项目ID: " + projectId);
        SysProject project = sysProjectMapper.selectById(projectId);
        String projectName = (project != null) ? project.getProjectName() : "未知项目";

        List<SysBuilding> buildings = sysBuildingMapper.selectList(
            new QueryWrapper<SysBuilding>().eq("project_id", projectId)
        );
        if (buildings == null || buildings.isEmpty()) return;

        // 1. 获取照片
        QueryWrapper<ProjectPhoto> photoQuery = new QueryWrapper<>();
        photoQuery.eq("project_id", projectId);
        photoQuery.isNotNull("laser_distance").isNotNull("absolute_altitude");
        photoQuery.and(w -> w.eq("is_marker", 0).or().isNull("is_marker"));
        photoQuery.orderByAsc("shoot_time");

        List<ProjectPhoto> photos = projectPhotoMapper.selectList(photoQuery);
        if (photos.isEmpty()) {
            System.out.println(">>> 没有找到有效照片，计算结束。");
            return;
        }

        Map<Integer, Map<String, List<RawData>>> aggregation = new HashMap<>();

        // 2. 空间初筛
        for (SysBuilding building : buildings) {
            String boundaryJson = building.getBoundaryCoords();
            if (boundaryJson == null || boundaryJson.isEmpty()) continue;

            List<Coordinate> fence;
            try {
                fence = objectMapper.readValue(boundaryJson, new TypeReference<List<Coordinate>>() {});
            } catch (Exception e) { continue; }
            if (fence.size() < 3) continue;

            for (ProjectPhoto photo : photos) {
                double lat = (photo.getLrfTargetLat() != null) ? photo.getLrfTargetLat().doubleValue() : photo.getGpsLat().doubleValue();
                double lng = (photo.getLrfTargetLng() != null) ? photo.getLrfTargetLng().doubleValue() : photo.getGpsLng().doubleValue();

                // 缓冲区判定 (保持你原来的25.0米)
                if (isInsideOrBuffered(lat, lng, fence, 20.0)) {
                    String dateStr = photo.getShootTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                    RawData data = new RawData();
                    data.id = photo.getId();
                    data.dist = photo.getLaserDistance().doubleValue();
                    data.droneAlt = photo.getAbsoluteAltitude().doubleValue();
                    // 这里的 initialType 仅作标记，不参与核心计算
                    data.initialType = isPointInPolygon(lat, lng, fence) ? DataType.H1_ROOF : DataType.H2_GROUND;

                    aggregation
                        .computeIfAbsent(building.getId(), k -> new HashMap<>())
                        .computeIfAbsent(dateStr, k -> new ArrayList<>())
                        .add(data);
                }
            }
        }

        // 3. 计算逻辑
        for (Map.Entry<Integer, Map<String, List<RawData>>> buildingEntry : aggregation.entrySet()) {
            Integer buildingId = buildingEntry.getKey();
            SysBuilding buildingInfo = sysBuildingMapper.selectById(buildingId);
            String planName = buildingInfo.getPlanBuildingName();
            System.out.println("--- 正在计算楼栋: " + buildingInfo.getName() + " ---");

            Map<String, List<RawData>> dailyData = buildingEntry.getValue();
            List<BuildingFloorInfo> floorRuler = floorInfoMapper.selectList(
                new QueryWrapper<BuildingFloorInfo>().eq("building_id", buildingId).orderByAsc("floor_number")
            );

            List<String> sortedDates = new ArrayList<>(dailyData.keySet());
            Collections.sort(sortedDates);

            for (String dateStr : sortedDates) {
                List<RawData> allCandidates = dailyData.get(dateStr);
                if (allCandidates.isEmpty()) continue;

                LocalDate measureDate = LocalDate.parse(dateStr);
                double avgDroneAlt = allCandidates.stream().mapToDouble(d -> d.droneAlt).average().orElse(0.0);

                // 记录照片数量 (用于前端展示和算法分支)
                int photoCount = allCandidates.size();

                // =============================================================
                // 🔥 步骤 A: 混合清洗策略 (V12 新增核心 - P25/中位数)
                // =============================================================
                if (photoCount > 0) {
                    // 1. 提取所有距离并排序
                    List<Double> distances = allCandidates.stream()
                            .map(d -> d.dist).sorted().collect(Collectors.toList());

                    double benchmark;

                    // 2. 确定基准锚点 (Benchmark)
                    if (photoCount <= 3) {
                        // 小样本 -> 使用【中位数】
                        if (photoCount % 2 == 0) {
                            benchmark = (distances.get(photoCount/2 - 1) + distances.get(photoCount/2)) / 2.0;
                        } else {
                            benchmark = distances.get(photoCount/2);
                        }
                    } else {
                        // 大样本 -> 使用【P25 分位数】 (防止地面点干扰)
                        int p25Index = (int) Math.ceil(photoCount * 0.25) - 1;
                        if (p25Index < 0) p25Index = 0;
                        benchmark = distances.get(p25Index);
                    }

                    // 3. 设定阈值：剔除比基准值还小 5米 的突兀点 (塔吊/干扰)
                    double safeThreshold = benchmark - 5.0;

                    // 4. 执行过滤
                    List<RawData> cleanCandidates = allCandidates.stream()
                            .filter(d -> d.dist >= safeThreshold)
                            .collect(Collectors.toList());

                    if (!cleanCandidates.isEmpty()) {
                        if (cleanCandidates.size() < allCandidates.size()) {
                            System.out.println("   [清洗] 剔除高空噪点 " + (allCandidates.size() - cleanCandidates.size()) + " 个 (Benchmark=" + benchmark + ")");
                        }
                        allCandidates = cleanCandidates;
                    }
                }

                // =============================================================
                // 步骤 B: 距离分层 (Distance Clustering)
                // =============================================================

                // 1. 找 D_min
                double dMin = allCandidates.stream().mapToDouble(d -> d.dist).min().orElse(0);

                // 2. 强制分层
                List<RawData> h1List = new ArrayList<>();
                List<RawData> h2List = new ArrayList<>();

                for (RawData d : allCandidates) {
                    if (d.dist <= dMin + 5.0) {
                        h1List.add(d);
                    } else if (d.dist >= dMin + 10.0) {
                        h2List.add(d);
                    }
                }

                // 调试日志
                System.out.printf("[%s] D_min=%.2f | H1数量=%d | H2数量=%d%n", dateStr, dMin, h1List.size(), h2List.size());

                // --- 步骤 C: H1 均值清洗 (剔除小杂物) ---
                if (h1List.size() > 2) {
                    for (int i = 0; i < 3; i++) {
                        double avgH1 = h1List.stream().mapToDouble(d -> d.dist).average().orElse(0);
                        List<RawData> noisePoints = new ArrayList<>();
                        for (RawData d : h1List) {
                            if (Math.abs(d.dist - avgH1) > 2.0) noisePoints.add(d);
                        }
                        if (noisePoints.isEmpty()) break;
                        h1List.removeAll(noisePoints);
                    }
                }

                // --- 步骤 D: 最终计算 (含 H2 智能校验锁) ---

                // 1. 计算 H1
                double finalH1 = -1;
                if (!h1List.isEmpty()) {
                    finalH1 = h1List.stream().mapToDouble(d -> d.dist).average().orElse(-1);
                }

                // 2. 准备计算 H2 的参数
                // 3. 计算【理论 H2】 (历史回推)
                double theoreticalH2 = -1;
                ActualProgress lastRecord = actualProgressMapper.selectOne(new QueryWrapper<ActualProgress>()
                        .eq("building_id", buildingId)
                        .eq("is_h2_measured", true) // 必须是以前实测过的真地面
                        .isNotNull("h2_val").isNotNull("drone_alt")
                        .lt("measurement_date", measureDate)
                        .orderByDesc("measurement_date").last("LIMIT 1"));

                if (lastRecord != null) {
                    double baseH2 = lastRecord.getH2Val().doubleValue();
                    double baseAlt = lastRecord.getDroneAlt().doubleValue();
                    // 公式：理论H2 = 历史H2 + (今日飞高 - 历史飞高)
                    theoreticalH2 = baseH2 + (avgDroneAlt - baseAlt);
                }

                // 4. 计算【实测 H2】 (如果 H2List 不为空)
                double measuredH2 = -1;
                if (!h2List.isEmpty()) {
                    double tmpAvg = h2List.stream().mapToDouble(d -> d.dist).average().orElse(0);
                    measuredH2 = h2List.stream().mapToDouble(d -> d.dist)
                            .filter(d -> Math.abs(d - tmpAvg) < 5.0).average().orElse(tmpAvg);
                }

                // 5. 关键决策：H2 校验
                double finalH2 = -1;
                boolean isH2Measured = false;

                if (measuredH2 != -1) {
                    // 如果有历史数据，且 实测H2 远小于 理论H2 (差距 > 10m)
                    // 说明：实测到的距离太短了，打到了裙楼或别的楼顶，不是真地面
                    if (theoreticalH2 != -1 && (theoreticalH2 - measuredH2) > 10.0) {
                        System.out.println("   [警告] 剔除伪地面数据(串扰/裙楼)! 实测H2=" + measuredH2 + " 理论H2=" + theoreticalH2);
                        // 强制使用理论值
                        finalH2 = theoreticalH2;
                        isH2Measured = false; // 标记为非实测
                    } else {
                        // 正常情况，采纳实测值
                        finalH2 = measuredH2;
                        isH2Measured = true;
                    }
                } else {
                    // 没测到 H2，直接用理论值
                    if (theoreticalH2 != -1) {
                        finalH2 = theoreticalH2;
                        System.out.println("   -> 使用历史基准补偿 H2: " + finalH2);
                    } else {
                        System.out.println("   -> ⚠️ 无 H2 数据且无历史基准！");
                    }
                }

                // 入库
                double actualHeight = 0.0;
                if (finalH1 != -1 && finalH2 != -1) {
                    actualHeight = finalH2 - finalH1;
                }
                if (actualHeight < 0) actualHeight = 0;

                int preciseFloor = calculateFloorLevel(actualHeight, floorRuler);

                // 🔴 注意：此处增加了 photoCount 参数，如果实体类未更新，请在这一行去掉 photoCount 参数
                saveOrUpdateProgress(projectId, projectName, buildingId, LocalDate.parse(dateStr),
                                     actualHeight, finalH1, finalH2, avgDroneAlt, preciseFloor, isH2Measured, photoCount);

                if (planName != null && !planName.isEmpty()) {
                    analyzeAndSaveStatus(planName, LocalDate.parse(dateStr), preciseFloor);
                }
            }
        }
        System.out.println(">>> 进度计算完成。");
    }

    // =========================================================
    // 3. 辅助计算方法 (保留原业务逻辑 + 新增缓冲区判定)
    // =========================================================

    private boolean isInsideOrBuffered(double lat, double lng, List<Coordinate> polygon, double bufferMeters) {
        if (polygon == null || polygon.size() < 3) return false;
        if (isPointInPolygon(lat, lng, polygon)) return true;
        return getMinDistanceToBoundary(lat, lng, polygon) <= bufferMeters;
    }

    private boolean isPointInPolygon(double lat, double lng, List<Coordinate> polygon) {
        boolean result = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            if ((polygon.get(i).getLat() > lat) != (polygon.get(j).getLat() > lat) &&
                (lng < (polygon.get(j).getLng() - polygon.get(i).getLng()) * (lat - polygon.get(i).getLat()) / (polygon.get(j).getLat() - polygon.get(i).getLat()) + polygon.get(i).getLng())) {
                result = !result;
            }
        }
        return result;
    }

    private double getMinDistanceToBoundary(double lat, double lng, List<Coordinate> polygon) {
        double minDistance = Double.MAX_VALUE;
        double mPerLat = 111132.92;
        double mPerLng = 111412.84 * Math.cos(Math.toRadians(lat));
        for (int i = 0; i < polygon.size(); i++) {
            Coordinate p1 = polygon.get(i);
            Coordinate p2 = polygon.get((i + 1) % polygon.size());
            double x1 = (p1.getLng() - lng) * mPerLng;
            double y1 = (p1.getLat() - lat) * mPerLat;
            double x2 = (p2.getLng() - lng) * mPerLng;
            double y2 = (p2.getLat() - lat) * mPerLat;
            double dist = pointToSegmentDistance(0, 0, x1, y1, x2, y2);
            if (dist < minDistance) minDistance = dist;
        }
        return minDistance;
    }

    private double pointToSegmentDistance(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) return Math.hypot(px - x1, py - y1);
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        double nearestX = x1 + t * dx;
        double nearestY = y1 + t * dy;
        return Math.hypot(px - nearestX, py - nearestY);
    }

    private int calculateFloorLevel(double currentHeight, List<BuildingFloorInfo> ruler) {
        if (ruler == null || ruler.isEmpty()) return Math.max(0, (int) (currentHeight / 3.0));
        if (currentHeight <= 0) return 0;
        int floor = 0;
        for (BuildingFloorInfo info : ruler) {
             if (currentHeight >= (info.getCumulativeHeight().doubleValue() - 0.5)) floor = info.getFloorNumber();
             else break;
        }
        return floor;
    }

    /**
     * 状态分析：对比实际楼层与计划楼层 (保留原业务)
     */
    public String analyzeStatus(String navisworksName, int actualFloor, LocalDate date) {
        List<PlanProgress> plans = planProgressMapper.selectList(new QueryWrapper<PlanProgress>()
                .eq("Building", navisworksName).le("PlannedEnd", date.atTime(23, 59, 59)));
        int plannedFloor = 0;
        for (PlanProgress p : plans) {
            try {
                String fStr = p.getFloor().replaceAll("[^0-9]", "");
                if(!fStr.isEmpty()){
                    int f = Integer.parseInt(fStr);
                    if (f > plannedFloor) plannedFloor = f;
                }
            } catch (NumberFormatException e) {}
        }
        if (plans.isEmpty()) return "暂无计划";
        int diff = actualFloor - plannedFloor;
        if (diff >= 0) return diff == 0 ? "正常" : "超前 " + diff + " 层";
        else return Math.abs(diff) > 2 ? "严重滞后" : "滞后 " + Math.abs(diff) + " 层";
    }

    private void analyzeAndSaveStatus(String planName, LocalDate date, int actualFloor) {
        String status = analyzeStatus(planName, actualFloor, date);
        // 这里可以扩展将状态存入数据库
    }


    /**
     * 保存进度记录 (带"单调递增"棘轮修正)
     * 修复：防止因无人机GPS误差导致出现"楼层变矮"的异常数据
     * 修复：记录照片数量 photoCount (V12新增)
     */
    private void saveOrUpdateProgress(Integer projectId, String projectName, Integer buildingId, LocalDate date,
                                      double rawHeight, double h1, double h2, double droneAlt, Integer rawFloor, boolean isH2Measured,
                                      Integer photoCount) { // 👈 新增参数

        // 1. 获取该楼栋截止到昨天的"历史最大高度"
        // 我们查出该楼栋所有日期的记录，按高度降序排，取第一条
        QueryWrapper<ActualProgress> maxQuery = new QueryWrapper<>();
        maxQuery.eq("building_id", buildingId)
                .lt("measurement_date", date) // 只看今天之前的
                .orderByDesc("actual_height") // 找最高的
                .last("LIMIT 1");

        ActualProgress maxRecord = actualProgressMapper.selectOne(maxQuery);

        double finalHeight = rawHeight;
        int finalFloor = rawFloor;

        // 2. 棘轮修正逻辑 (Ratchet Correction)
        if (maxRecord != null && maxRecord.getActualHeight() != null) {
            double maxH = maxRecord.getActualHeight().doubleValue();

            // 如果今天算出来的高度，比历史最高还低
            if (rawHeight < maxH) {
                // 判断一下差距，如果是巨大的错误（比如差了50米），可能是测量事故，就不强制拉平了，保留错误供排查
                // 但如果是小范围误差（比如差 3米以内），则强制拉平
                if ((maxH - rawHeight) < 5.0) {
                    System.out.println("   [修正] 检测到高度回撤: " + rawHeight + " -> 修正为历史最高: " + maxH);
                    finalHeight = maxH;

                    // 楼层也对应修正，取两者最大值
                    if (maxRecord.getFloorLevel() != null) {
                        finalFloor = Math.max(rawFloor, maxRecord.getFloorLevel());
                    }
                }
            }
        }

        // 3. 执行数据库更新或插入 (保持原有逻辑)
        QueryWrapper<ActualProgress> query = new QueryWrapper<>();
        query.eq("building_id", buildingId).eq("measurement_date", date);
        ActualProgress exist = actualProgressMapper.selectOne(query);

        if (exist != null) {
            exist.setProjectName(projectName);
            exist.setFloorLevel(finalFloor); // 使用修正后的楼层
            exist.setActualHeight(BigDecimal.valueOf(finalHeight).setScale(2, RoundingMode.HALF_UP)); // 使用修正后的高度

            // 【关键】保留原始的测量数据 h1/h2 供排查，但 actual_height 存修正后的
            exist.setH1Val(h1 != -1 ? BigDecimal.valueOf(h1).setScale(2, RoundingMode.HALF_UP) : null);
            exist.setH2Val(h2 != -1 ? BigDecimal.valueOf(h2).setScale(2, RoundingMode.HALF_UP) : null);
            exist.setDroneAlt(BigDecimal.valueOf(droneAlt).setScale(2, RoundingMode.HALF_UP));
            exist.setIsH2Measured(isH2Measured);

            // 🔴 关键修改：保存 photoCount。如果你的实体类没加字段，这行会报红，请去实体类加字段
            exist.setPhotoCount(photoCount);

            actualProgressMapper.updateById(exist);
        } else {
            ActualProgress progress = new ActualProgress();
            progress.setProjectId(projectId);
            progress.setProjectName(projectName);
            progress.setBuildingId(buildingId);
            progress.setMeasurementDate(date);

            progress.setFloorLevel(finalFloor); // 使用修正后的楼层
            progress.setActualHeight(BigDecimal.valueOf(finalHeight).setScale(2, RoundingMode.HALF_UP)); // 使用修正后的高度

            progress.setH1Val(h1 != -1 ? BigDecimal.valueOf(h1).setScale(2, RoundingMode.HALF_UP) : null);
            progress.setH2Val(h2 != -1 ? BigDecimal.valueOf(h2).setScale(2, RoundingMode.HALF_UP) : null);
            progress.setDroneAlt(BigDecimal.valueOf(droneAlt).setScale(2, RoundingMode.HALF_UP));
            progress.setIsH2Measured(isH2Measured);

            // 🔴 关键修改：保存 photoCount
            progress.setPhotoCount(photoCount);

            progress.setCreatedAt(LocalDateTime.now());

            actualProgressMapper.insert(progress);
        }
    }

    // =========================================================
    // 4. 内部数据结构 (V7 新增)
    // =========================================================

    @Data
    @AllArgsConstructor
    private static class RawData {
        Long id;
        double dist;
        double droneAlt;
        DataType initialType; // 初始分类

        public RawData() {}
    }

    private enum DataType {
        H1_ROOF,
        H2_GROUND
    }

    // =========================================================
    // 5. AI 日报数据聚合 (终极修正版：带调试日志 + 强制读取最新)
    // =========================================================
    @Override
    public String getProjectFullStatusJson(Integer projectId) {
        try {
            SysProject project = sysProjectMapper.selectById(projectId);
            String projectName = (project != null) ? project.getProjectName() : "未知项目";

            // 1. 总体报告时间：直接写死为“截止今日”，避免 AI 算错系统时间
            String reportTime = "截止今日 (" + LocalDate.now().toString() + ")";

            Map<String, Object> root = new HashMap<>();
            root.put("projectName", projectName);
            root.put("reportTime", reportTime);

            List<SysBuilding> buildings = sysBuildingMapper.selectList(
                new QueryWrapper<SysBuilding>().eq("project_id", projectId)
            );

            List<Map<String, Object>> buildingList = new ArrayList<>();
            int delayBuildingCount = 0;

            System.out.println("========== AI 数据源调试开始 ==========");

            for (SysBuilding b : buildings) {
                Map<String, Object> bInfo = new HashMap<>();
                bInfo.put("name", b.getName());

                // --- A. 查实际进度 (关键修改) ---
                // 按 measurement_date 倒序，如果有同一天的，按 id 倒序(取最新录入的)
                ActualProgress actual = actualProgressMapper.selectOne(new QueryWrapper<ActualProgress>()
                        .eq("building_id", b.getId())
                        .orderByDesc("measurement_date", "id")
                        .last("LIMIT 1"));

                int currentFloor = 0;
                double currentHeight = 0.0;
                String lastMeasureDate = "暂无数据"; // 默认值

                if (actual != null) {
                    currentFloor = actual.getFloorLevel() != null ? actual.getFloorLevel() : 0;
                    currentHeight = actual.getActualHeight() != null ? actual.getActualHeight().doubleValue() : 0.0;

                    // 获取数据库里的真实日期
                    if (actual.getMeasurementDate() != null) {
                        lastMeasureDate = actual.getMeasurementDate().toString();
                    }
                }

                // 打印调试日志：请在控制台查看这里输出的日期和高度是不是你想要的！
                System.out.println("楼栋: " + b.getName() + " | DB日期: " + lastMeasureDate + " | DB高度: " + currentHeight);

                bInfo.put("currentFloor", currentFloor);
                bInfo.put("currentHeight", String.format("%.2f", currentHeight));
                bInfo.put("lastMeasureDate", lastMeasureDate); // 传给 AI

                // --- B. 查计划进度 ---
                int plannedFloor = 0;
                String plannedEndDate = "未设置计划";

                if (b.getPlanBuildingName() != null) {
                    List<PlanProgress> plans = planProgressMapper.selectList(new QueryWrapper<PlanProgress>()
                            .eq("Building", b.getPlanBuildingName())
                            .le("PlannedEnd", LocalDateTime.now()));
                    for (PlanProgress p : plans) {
                        try {
                            String fStr = p.getFloor().replaceAll("[^0-9]", "");
                            if (!fStr.isEmpty()) {
                                int f = Integer.parseInt(fStr);
                                if (f > plannedFloor) plannedFloor = f;
                            }
                        } catch (Exception e) {}
                    }
                }
                bInfo.put("plannedFloor", plannedFloor);

                // --- C. 计算滞后 ---
                int floorDiff = currentFloor - plannedFloor;
                long delayDays = 0;

                if (floorDiff >= 0) {
                    bInfo.put("status", "正常");
                    bInfo.put("statusDesc", "进度正常");
                } else {
                    bInfo.put("status", "滞后");
                    delayBuildingCount++;

                    if (b.getPlanBuildingName() != null) {
                        PlanProgress planForCurrent = planProgressMapper.selectOne(new QueryWrapper<PlanProgress>()
                                .eq("Building", b.getPlanBuildingName())
                                .like("Floor", String.valueOf(currentFloor))
                                .last("LIMIT 1"));

                        if (planForCurrent != null && planForCurrent.getPlannedEnd() != null) {
                            LocalDate planDate = planForCurrent.getPlannedEnd().toLocalDate();
                            LocalDate today = LocalDate.now();
                            plannedEndDate = planDate.toString();

                            if (planDate.isBefore(today)) {
                                delayDays = java.time.temporal.ChronoUnit.DAYS.between(planDate, today);
                            }
                        }
                    }
                    bInfo.put("delayDays", delayDays);
                    bInfo.put("delayFloors", Math.abs(floorDiff));
                    bInfo.put("plannedEndDate", plannedEndDate);
                }

                buildingList.add(bInfo);
            }
            System.out.println("========== AI 数据源调试结束 ==========");

            root.put("buildings", buildingList);
            root.put("overallSummary", delayBuildingCount > 0
                ? "项目整体存在延期风险，共有 " + delayBuildingCount + " 栋楼进度滞后。"
                : "项目整体进度管控良好，各单体均按计划推进。");

            return objectMapper.writeValueAsString(root);

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"数据计算异常: " + e.getMessage() + "\"}";
        }
    }
}