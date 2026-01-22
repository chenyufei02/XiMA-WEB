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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度管理服务核心实现
 * 包含：上传进度追踪 + 施工进度智能计算算法 (最终完整版)
 */
@Service
public class ProgressServiceImpl implements ProgressService {

    // --- 上传进度管理 ---
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
    private PlanProgressMapper planProgressMapper; // ✅ 新增：用于查询Navisworks计划数据

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
    // ✅ 核心业务：全自动施工进度计算
    // =========================================================

    @Override
    public void calculateProjectProgress(Integer projectId) {
        // 1. 先查出项目信息
        SysProject project = sysProjectMapper.selectById(projectId);
        String projectName = (project != null) ? project.getProjectName() : "未知项目";

        // 2. 获取该项目下所有的楼栋
        List<SysBuilding> buildings = sysBuildingMapper.selectList(
            new QueryWrapper<SysBuilding>().eq("project_id", projectId)
        );
        if (buildings.isEmpty()) return;

        // 3. 获取该项目下所有有效照片，必须按时间正序排列以支持推算
        QueryWrapper<ProjectPhoto> photoQuery = new QueryWrapper<>();
        photoQuery.eq("project_id", projectId);
        photoQuery.isNotNull("gps_lat").isNotNull("gps_lng");
        photoQuery.isNotNull("absolute_altitude").isNotNull("laser_distance");
        photoQuery.orderByAsc("shoot_time"); // ✅ 关键：按时间排序
        List<ProjectPhoto> photos = projectPhotoMapper.selectList(photoQuery);

        // 临时聚合容器: BuildingId -> Date -> List<Data>
        Map<Integer, Map<String, List<CalcData>>> aggregation = new HashMap<>();

        // 缓存每个楼栋的"基准数据" (用于 H2 推算): BuildingId -> RefData
        Map<Integer, RefData> lastRefDataMap = new HashMap<>();

        // 4. 核心循环：空间匹配与高度推算
        for (ProjectPhoto photo : photos) {
            double lat = photo.getGpsLat().doubleValue();
            double lng = photo.getGpsLng().doubleValue();

            for (SysBuilding building : buildings) {
                if (isInsideBoundary(lat, lng, building.getBoundaryCoords())) {

                    double h1 = photo.getLaserDistance().doubleValue(); // 实测楼顶距离
                    double currentDroneAlt = photo.getAbsoluteAltitude().doubleValue(); // 当前飞高
                    double h2;
                    boolean isRef = false;

                    // --- H2 动态推算核心逻辑 ---

                    // 1. 尝试从内存缓存中获取基准
                    RefData ref = lastRefDataMap.get(building.getId());

                    // 2. 如果内存没有，去数据库查最近的一条有效基准 (is_h2_measured = true)
                    if (ref == null) {
                        ActualProgress lastDbRecord = actualProgressMapper.selectOne(new QueryWrapper<ActualProgress>()
                                .eq("building_id", building.getId())
                                .eq("is_h2_measured", true)
                                .orderByDesc("measurement_date")
                                .last("LIMIT 1"));
                        if (lastDbRecord != null) {
                            ref = new RefData(lastDbRecord.getH2Val().doubleValue(), lastDbRecord.getDroneAlt().doubleValue());
                            lastRefDataMap.put(building.getId(), ref);
                        }
                    }

                    if (ref != null) {
                        // ✅ 核心公式：本次H2 = 基准H2 + (本次飞高 - 基准飞高)
                        // 物理含义：无人机飞得越高，测到地面的距离 H2 也就越大，差值就是飞高变化量
                        h2 = ref.h2 + (currentDroneAlt - ref.droneAlt);
                    } else {
                        // 兜底：如果是该楼栋第一张照片，且没有任何历史数据
                        // 暂时假设当前飞高即为参考基准 (这只是为了让第一次计算能跑通，数据可能不准，但有了第一条后后续会自我修正)
                        h2 = currentDroneAlt;
                        lastRefDataMap.put(building.getId(), new RefData(h2, currentDroneAlt));
                        isRef = true; // 标记这条为新的基准
                    }

                    // ✅ 最终计算：楼高 = 地面距离(H2) - 楼顶距离(H1)
                    double actualHeight = h2 - h1;
                    if (actualHeight < 0) actualHeight = 0; // 修正误差

                    String dateStr = photo.getShootTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                    aggregation
                        .computeIfAbsent(building.getId(), k -> new HashMap<>())
                        .computeIfAbsent(dateStr, k -> new ArrayList<>())
                        .add(new CalcData(actualHeight, h1, h2, currentDroneAlt, isRef));

                    break;
                }
            }
        }

        // 5. 数据聚合、楼层判定与入库
        for (Map.Entry<Integer, Map<String, List<CalcData>>> buildingEntry : aggregation.entrySet()) {
            Integer buildingId = buildingEntry.getKey();
            SysBuilding buildingInfo = sysBuildingMapper.selectById(buildingId);
            // ✅ 获取用户绑定的 Navisworks 计划楼名
            String planName = buildingInfo.getPlanBuildingName();

            Map<String, List<CalcData>> dailyData = buildingEntry.getValue();

            // 预查楼层标尺
            List<BuildingFloorInfo> floorRuler = floorInfoMapper.selectList(
                new QueryWrapper<BuildingFloorInfo>()
                    .eq("building_id", buildingId)
                    .orderByAsc("floor_number")
            );

            for (Map.Entry<String, List<CalcData>> dateEntry : dailyData.entrySet()) {
                String dateStr = dateEntry.getKey();
                LocalDate measureDate = LocalDate.parse(dateStr);
                List<CalcData> dataList = dateEntry.getValue();

                // 计算平均值
                double avgActualHeight = dataList.stream().mapToDouble(d -> d.actualHeight).average().orElse(0.0);
                double avgH1 = dataList.stream().mapToDouble(d -> d.h1).average().orElse(0.0);
                double avgH2 = dataList.stream().mapToDouble(d -> d.h2).average().orElse(0.0);
                double avgDroneAlt = dataList.stream().mapToDouble(d -> d.droneAlt).average().orElse(0.0);
                boolean isH2Measured = dataList.stream().anyMatch(d -> d.isRef);

                // 智能楼层判定
                int preciseFloor = calculateFloorLevel(avgActualHeight, floorRuler);

                // 保存实际进度
                saveOrUpdateProgress(projectId, projectName, buildingId, measureDate,
                                     avgActualHeight, avgH1, avgH2, avgDroneAlt, preciseFloor, isH2Measured);

                // ✅ 触发状态分析 (如果已绑定计划)
                if (planName != null && !planName.isEmpty()) {
                    analyzeAndSaveStatus(planName, measureDate, preciseFloor);
                }
            }
        }
    }

    /**
     * 状态分析：对比实际楼层与计划楼层 (Navisworks数据)
     */
    public String analyzeStatus(String navisworksName, int actualFloor, LocalDate date) {
        // 查计划：Navisworks表里的 Building 字段
        List<PlanProgress> plans = planProgressMapper.selectList(new QueryWrapper<PlanProgress>()
                .eq("Building", navisworksName)
                .le("PlannedEnd", date.atTime(23, 59, 59))); // 截止到当天结束

        int plannedFloor = 0;
        for (PlanProgress p : plans) {
            try {
                // 处理 Navisworks 导出的楼层字符串 (如 "17", "Roof")，提取数字
                String fStr = p.getFloor().replaceAll("[^0-9]", "");
                if(!fStr.isEmpty()){
                    int f = Integer.parseInt(fStr);
                    if (f > plannedFloor) plannedFloor = f; // 取最大值作为应当完成的进度
                }
            } catch (NumberFormatException e) {}
        }

        if (plans.isEmpty()) return "暂无计划";

        int diff = actualFloor - plannedFloor;
        if (diff >= 0) {
            return diff == 0 ? "正常" : "超前 " + diff + " 层";
        } else {
            return Math.abs(diff) > 2 ? "严重滞后" : "滞后 " + Math.abs(diff) + " 层";
        }
    }

    private void analyzeAndSaveStatus(String planName, LocalDate date, int actualFloor) {
        // 可以在这里将状态写入日志或缓存，目前主要用于逻辑演示
        // 实际前端调用接口时会实时计算
        String status = analyzeStatus(planName, actualFloor, date);
    }

    /**
     * 核心算法：根据高度查找楼层
     */
    private int calculateFloorLevel(double currentHeight, List<BuildingFloorInfo> ruler) {
        if (ruler == null || ruler.isEmpty()) {
            return Math.max(0, (int) (currentHeight / 3.0));
        }
        if (currentHeight <= 0) return 0;

        for (BuildingFloorInfo info : ruler) {
            double limit = info.getCumulativeHeight().doubleValue();
            if (currentHeight <= limit) {
                return info.getFloorNumber();
            }
        }
        return ruler.get(ruler.size() - 1).getFloorNumber();
    }

    /**
     * 射线法判断坐标
     */
    private boolean isInsideBoundary(double lat, double lng, String boundaryJson) {
        if (boundaryJson == null || boundaryJson.isEmpty()) return false;
        try {
            List<Coordinate> polygon = objectMapper.readValue(boundaryJson, new TypeReference<List<Coordinate>>() {});
            if (polygon.size() < 3) return false;

            boolean result = false;
            for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
                if ((polygon.get(i).getLat() > lat) != (polygon.get(j).getLat() > lat) &&
                    (lng < (polygon.get(j).getLng() - polygon.get(i).getLng()) * (lat - polygon.get(i).getLat()) / (polygon.get(j).getLat() - polygon.get(i).getLat()) + polygon.get(i).getLng())) {
                    result = !result;
                }
            }
            return result;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 保存进度记录
     */
    private void saveOrUpdateProgress(Integer projectId, String projectName, Integer buildingId, LocalDate date,
                                      double height, double h1, double h2, double droneAlt, Integer floorLevel, boolean isH2Measured) {
        QueryWrapper<ActualProgress> query = new QueryWrapper<>();
        query.eq("building_id", buildingId);
        query.eq("measurement_date", date);
        ActualProgress exist = actualProgressMapper.selectOne(query);

        if (exist != null) {
            exist.setProjectName(projectName);
            exist.setFloorLevel(floorLevel);
            exist.setActualHeight(BigDecimal.valueOf(height).setScale(2, RoundingMode.HALF_UP));
            exist.setH1Val(BigDecimal.valueOf(h1).setScale(2, RoundingMode.HALF_UP));
            exist.setH2Val(BigDecimal.valueOf(h2).setScale(2, RoundingMode.HALF_UP)); // ✅ 存入 H2
            exist.setDroneAlt(BigDecimal.valueOf(droneAlt).setScale(2, RoundingMode.HALF_UP));
            exist.setIsH2Measured(isH2Measured); // ✅ 存入是否实测
            actualProgressMapper.updateById(exist);
        } else {
            ActualProgress progress = new ActualProgress();
            progress.setProjectId(projectId);
            progress.setProjectName(projectName);
            progress.setBuildingId(buildingId);
            progress.setMeasurementDate(date);
            progress.setFloorLevel(floorLevel);

            progress.setActualHeight(BigDecimal.valueOf(height).setScale(2, RoundingMode.HALF_UP));
            progress.setH1Val(BigDecimal.valueOf(h1).setScale(2, RoundingMode.HALF_UP));
            progress.setH2Val(BigDecimal.valueOf(h2).setScale(2, RoundingMode.HALF_UP)); // ✅ 存入 H2
            progress.setDroneAlt(BigDecimal.valueOf(droneAlt).setScale(2, RoundingMode.HALF_UP));

            progress.setIsH2Measured(isH2Measured); // ✅ 存入是否实测
            progress.setCreatedAt(LocalDateTime.now());
            actualProgressMapper.insert(progress);
        }
    }

    /**
     * 辅助内部类：用于聚合当日数据
     */
    @Data
    @AllArgsConstructor
    private static class CalcData {
        double actualHeight;
        double h1;
        double h2; // ✅ 增加 H2 字段
        double droneAlt;
        boolean isRef; // ✅ 增加是否为基准标记
    }

    /**
     * 辅助内部类：用于缓存基准数据
     */
    @Data
    @AllArgsConstructor
    private static class RefData {
        double h2;
        double droneAlt;
    }
}