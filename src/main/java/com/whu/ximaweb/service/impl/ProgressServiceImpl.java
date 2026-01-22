package com.whu.ximaweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.ximaweb.dto.Coordinate;
import com.whu.ximaweb.mapper.ActualProgressMapper;
import com.whu.ximaweb.mapper.BuildingFloorInfoMapper;
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.mapper.SysBuildingMapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.model.ActualProgress;
import com.whu.ximaweb.model.BuildingFloorInfo;
import com.whu.ximaweb.model.ProjectPhoto;
import com.whu.ximaweb.model.SysBuilding;
import com.whu.ximaweb.service.ProgressService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度管理服务核心实现
 * 包含：上传进度追踪 + 施工进度智能计算算法 (最终版)
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
    private ObjectMapper objectMapper;

    @Autowired
    private BuildingFloorInfoMapper floorInfoMapper; // ✅ 新增：用于查询楼层标尺

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
        com.whu.ximaweb.model.SysProject project = sysProjectMapper.selectById(projectId);
        String projectName = (project != null) ? project.getProjectName() : "未知项目";

        // 2. 获取该项目下所有的楼栋
        List<SysBuilding> buildings = sysBuildingMapper.selectList(
            new QueryWrapper<SysBuilding>().eq("project_id", projectId)
        );
        if (buildings.isEmpty()) return;

        // 3. 获取该项目下所有有效照片
        QueryWrapper<ProjectPhoto> photoQuery = new QueryWrapper<>();
        photoQuery.eq("project_id", projectId);
        photoQuery.isNotNull("gps_lat").isNotNull("gps_lng");
        photoQuery.isNotNull("absolute_altitude").isNotNull("laser_distance");
        List<ProjectPhoto> photos = projectPhotoMapper.selectList(photoQuery);

        // 临时聚合容器
        Map<Integer, Map<String, List<CalcData>>> aggregation = new HashMap<>();

        // 4. 核心循环：空间匹配
        for (ProjectPhoto photo : photos) {
            double lat = photo.getGpsLat().doubleValue();
            double lng = photo.getGpsLng().doubleValue();

            for (SysBuilding building : buildings) {
                if (isInsideBoundary(lat, lng, building.getBoundaryCoords())) {
                    // 物理公式: 高度 = 无人机绝对高 - 激光测距
                    double droneAlt = photo.getAbsoluteAltitude().doubleValue();
                    double laserDist = photo.getLaserDistance().doubleValue();
                    double calculatedHeight = droneAlt - laserDist;

                    String dateStr = photo.getShootTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                    aggregation
                        .computeIfAbsent(building.getId(), k -> new HashMap<>())
                        .computeIfAbsent(dateStr, k -> new ArrayList<>())
                        .add(new CalcData(calculatedHeight, laserDist, droneAlt));

                    break;
                }
            }
        }

        // 5. 数据聚合、楼层判定与入库
        for (Map.Entry<Integer, Map<String, List<CalcData>>> buildingEntry : aggregation.entrySet()) {
            Integer buildingId = buildingEntry.getKey();
            Map<String, List<CalcData>> dailyData = buildingEntry.getValue();

            // ✅ 优化：在循环日期之前，先把这栋楼的"标尺"查出来 (避免循环查库)
            List<BuildingFloorInfo> floorRuler = floorInfoMapper.selectList(
                new QueryWrapper<BuildingFloorInfo>()
                    .eq("building_id", buildingId)
                    .orderByAsc("floor_number") // 从低到高排序
            );

            for (Map.Entry<String, List<CalcData>> dateEntry : dailyData.entrySet()) {
                String dateStr = dateEntry.getKey();
                List<CalcData> dataList = dateEntry.getValue();

                // 计算平均高度
                double avgActualHeight = dataList.stream().mapToDouble(d -> d.actualHeight).average().orElse(0.0);
                double avgH1 = dataList.stream().mapToDouble(d -> d.h1).average().orElse(0.0);
                double avgDroneAlt = dataList.stream().mapToDouble(d -> d.droneAlt).average().orElse(0.0);

                // ✅ 智能楼层判定
                int preciseFloor = calculateFloorLevel(avgActualHeight, floorRuler);

                // 保存
                saveOrUpdateProgress(projectId, projectName, buildingId, LocalDate.parse(dateStr),
                                     avgActualHeight, avgH1, avgDroneAlt, preciseFloor);
            }
        }
    }

    /**
     * ✅ 核心算法：根据高度查找楼层
     * @param currentHeight 当前计算出的实际净高
     * @param ruler 楼层标尺列表 (必须已按楼层排序)
     */
    private int calculateFloorLevel(double currentHeight, List<BuildingFloorInfo> ruler) {
        if (ruler == null || ruler.isEmpty()) {
            // 如果没有配置标尺，还是做个兜底，按3米一层估算
            return Math.max(0, (int) (currentHeight / 3.0));
        }

        // 如果高度小于0 (误差)，归为0层
        if (currentHeight <= 0) return 0;

        // 遍历标尺查找
        for (BuildingFloorInfo info : ruler) {
            // 获取该层的顶标高
            double limit = info.getCumulativeHeight().doubleValue();

            // 如果当前高度 <= 这一层的顶标高，那就是这一层
            // 例如：1层顶4.5米。如果高度是4.0米，<=4.5，就是1层。
            if (currentHeight <= limit) {
                return info.getFloorNumber();
            }
        }

        // 如果遍历完了还没找到（说明高度超过了最高层的顶标高）
        // 比如盖到屋顶了，直接返回最高层
        return ruler.get(ruler.size() - 1).getFloorNumber();
    }

    /**
     * 辅助内部类
     */
    @Data
    @AllArgsConstructor
    private static class CalcData {
        double actualHeight;
        double h1;
        double droneAlt;
    }

    /**
     * 射线法
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
     * 保存
     */
    private void saveOrUpdateProgress(Integer projectId, String projectName, Integer buildingId, LocalDate date, double height, double h1, double droneAlt, Integer floorLevel) {
        QueryWrapper<ActualProgress> query = new QueryWrapper<>();
        query.eq("building_id", buildingId);
        query.eq("measurement_date", date);
        ActualProgress exist = actualProgressMapper.selectOne(query);

        if (exist != null) {
            exist.setProjectName(projectName);
            exist.setFloorLevel(floorLevel);   // ✅ 存入精准楼层
            exist.setActualHeight(BigDecimal.valueOf(height).setScale(2, RoundingMode.HALF_UP));
            exist.setH1Val(BigDecimal.valueOf(h1).setScale(2, RoundingMode.HALF_UP));
            exist.setDroneAlt(BigDecimal.valueOf(droneAlt).setScale(2, RoundingMode.HALF_UP));
            actualProgressMapper.updateById(exist);
        } else {
            ActualProgress progress = new ActualProgress();
            progress.setProjectId(projectId);
            progress.setProjectName(projectName);
            progress.setBuildingId(buildingId);
            progress.setMeasurementDate(date);
            progress.setFloorLevel(floorLevel); // ✅ 存入精准楼层

            progress.setActualHeight(BigDecimal.valueOf(height).setScale(2, RoundingMode.HALF_UP));
            progress.setH1Val(BigDecimal.valueOf(h1).setScale(2, RoundingMode.HALF_UP));
            progress.setDroneAlt(BigDecimal.valueOf(droneAlt).setScale(2, RoundingMode.HALF_UP));

            progress.setIsH2Measured(true);
            progress.setCreatedAt(java.time.LocalDateTime.now());
            actualProgressMapper.insert(progress);
        }
    }
}