package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.obs.services.ObsClient;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.TemporarySignatureRequest;
import com.obs.services.model.TemporarySignatureResponse;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.DashboardVo;
import com.whu.ximaweb.mapper.*;
import com.whu.ximaweb.model.*;
import com.whu.ximaweb.service.ProgressService;
import com.whu.ximaweb.service.impl.ProgressServiceImpl;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper; // 👈 选这个！不要选 shade 开头的
import com.fasterxml.jackson.core.type.TypeReference; // 👈 这个也不能少
import com.whu.ximaweb.dto.Coordinate; // 确保这个也在

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import com.whu.ximaweb.dto.BuildingHistoryVo;
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.model.ProjectPhoto;
import java.time.format.DateTimeFormatter;
import javax.annotation.PostConstruct; // 用于初始化
import javax.annotation.PreDestroy;    // 用于销毁


/**
 * 进度管理控制器 (最终完整版)
 * 负责：触发计算、获取原始图表数据、获取看板聚合数据
 */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    @Autowired
    private ProgressService progressService;

    @Autowired
    private ActualProgressMapper actualProgressMapper;

    // --- 新增依赖 (用于 Dashboard) ---
    @Autowired
    private ProgressServiceImpl progressServiceImpl; // 用于调用 analyzeStatus
    @Autowired
    private SysProjectMapper sysProjectMapper;
    @Autowired
    private SysBuildingMapper sysBuildingMapper;
    @Autowired
    private PlanProgressMapper planProgressMapper;
    @Autowired
    private ProjectPhotoMapper projectPhotoMapper; // 👈 必须注入它，否则无法查照片


    // --- OBS 配置注入 ---
    @Value("${xima.obs.default-endpoint}")
    private String obsEndpoint;

    @Value("${xima.obs.default-bucket}")
    private String obsBucket;

    // 使用配置文件里已有的 default-ak
    @Value("${xima.obs.default-ak}")
    private String obsAccessKey;

    // 🔥使用配置文件里已有的 default-sk
    @Value("${xima.obs.default-sk}")
    private String obsSecretKey;

    // OBS 客户端实例
    private ObsClient obsClient;


    /**
     * 初始化 ObsClient (在服务启动时执行一次)
     */
    @PostConstruct
    public void initObsClient() {
        this.obsClient = new ObsClient(obsAccessKey, obsSecretKey, obsEndpoint);
    }

    /**
     * 销毁 ObsClient (在服务关闭时执行)
     */
    @PreDestroy
    public void closeObsClient() {
        if (this.obsClient != null) {
            try {
                this.obsClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }





    /**
     * 👉 1. 手动触发计算接口 (保留原功能)
     * 作用：让系统根据当前的围栏，把历史所有照片重新跑一遍，算出每一天的进度。
     * 调用方式：POST /api/progress/calculate?projectId=1
     * 前端调用：在 Dashboard 或 围栏页点击“刷新计算”时调用
     */
    @PostMapping("/calculate")
    public ApiResponse<String> calculateProgress(@RequestParam Integer projectId) {
        try {
            System.out.println(">>> 收到手动触发计算请求，项目ID: " + projectId);
            long start = System.currentTimeMillis();

            // 调用核心 Service 进行全量计算
            progressService.calculateProjectProgress(projectId);

            long end = System.currentTimeMillis();
            System.out.println(">>> 计算完成，耗时: " + (end - start) + "ms");
            return ApiResponse.success("计算完成！耗时: " + (end - start) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("计算失败: " + e.getMessage());
        }
    }

    /**
     * 👉 2. 获取原始进度数据接口 (保留原功能)
     * 作用：前端画简单折线图时，通过这个接口获取数据
     */
    @GetMapping("/data")
    public ApiResponse<List<ActualProgress>> getProgressData(
            @RequestParam Integer projectId,
            @RequestParam(required = false) Integer buildingId
    ) {
        QueryWrapper<ActualProgress> query = new QueryWrapper<>();
        query.eq("project_id", projectId);
        if (buildingId != null) {
            query.eq("building_id", buildingId);
        }
        query.orderByAsc("measurement_date"); // 按日期排序

        List<ActualProgress> list = actualProgressMapper.selectList(query);
        return ApiResponse.success("获取成功", list);
    }

    /**
     * 👉 3. [已修改] 获取项目看板详情数据
     * 逻辑变更：无论数据是否过期，都计算滞后/超前状态并统计。过期仅作为标记。
     */
    @GetMapping("/dashboard/{projectId}")
    public ApiResponse<DashboardVo> getDashboardData(@PathVariable Integer projectId) {
        DashboardVo vo = new DashboardVo();

        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) return ApiResponse.error("项目不存在");

        vo.setProjectId(project.getId()); // 确保传回ID
        vo.setProjectName(project.getProjectName());
        long days = ChronoUnit.DAYS.between(project.getCreatedAt().toLocalDate(), LocalDate.now());
        vo.setSafeRunDays(days);

        List<SysBuilding> buildings = sysBuildingMapper.selectList(
            new QueryWrapper<SysBuilding>().eq("project_id", projectId)
        );
        vo.setTotalBuildings(buildings.size());

        List<DashboardVo.BuildingProgressVo> buildingVos = new ArrayList<>();
        int delayed = 0, normal = 0, ahead = 0;
        // 注意：waiting 不再用于表示“过期”，只表示“从未测过”
        int waiting = 0;
        LocalDate maxDate = LocalDate.MIN;

        for (SysBuilding b : buildings) {
            DashboardVo.BuildingProgressVo bVo = new DashboardVo.BuildingProgressVo();
            bVo.setBuildingId(b.getId());
            bVo.setBuildingName(b.getName());
            bVo.setPlanName(b.getPlanBuildingName());

            List<ActualProgress> history = actualProgressMapper.selectList(
                new QueryWrapper<ActualProgress>()
                    .eq("building_id", b.getId())
                    .orderByAsc("measurement_date")
            );

            // 初始化图表数据容器
            List<String> dates = new ArrayList<>();
            List<Integer> actualFloors = new ArrayList<>();
            List<Integer> planFloors = new ArrayList<>();
            List<Double> actualHeights = new ArrayList<>();
            List<Integer> deviations = new ArrayList<>();
            List<Integer> photoCounts = new ArrayList<>(); // 支持 Dashboard 照片数预警

            if (!history.isEmpty()) {
                ActualProgress latest = history.get(history.size() - 1);
                bVo.setCurrentFloor(latest.getFloorLevel());
                bVo.setCurrentHeight(latest.getActualHeight().doubleValue());
                bVo.setLastMeasureDate(latest.getMeasurementDate().toString());

                if (latest.getMeasurementDate().isAfter(maxDate)) maxDate = latest.getMeasurementDate();

                // 1. 判定过时 (逻辑：超过7天) - 仅作为 UI 标记
                long gap = ChronoUnit.DAYS.between(latest.getMeasurementDate(), LocalDate.now());
                boolean isOutdated = gap > 7;
                bVo.setOutdated(isOutdated);

                // 2. 计算状态 (无论是否过时，都算)
                String status = "暂无计划";
                String color = "info";

                if (b.getPlanBuildingName() != null) {
                    status = progressServiceImpl.analyzeStatus(b.getPlanBuildingName(), latest.getFloorLevel(), latest.getMeasurementDate());
                }

                // 3. 统计归类
                if (status.contains("滞后")) {
                    color = "danger";
                    delayed++;
                } else if (status.contains("超前")) {
                    color = "success";
                    ahead++;
                } else if (status.contains("正常")) {
                    color = "primary";
                    normal++;
                } else {
                    waiting++; // 有数据但无计划
                }

                bVo.setStatusTag(status);
                bVo.setStatusColor(color);

                // 填充历史数据
                for (ActualProgress ap : history) {
                    dates.add(ap.getMeasurementDate().toString());
                    actualFloors.add(ap.getFloorLevel());
                    actualHeights.add(ap.getActualHeight().doubleValue());
                    // 假设 ActualProgress 有 photoCount 字段，若没有需处理 null
                    photoCounts.add(ap.getPhotoCount() == null ? 0 : ap.getPhotoCount());

                    int planFloor = getPlanFloorAtDate(b.getPlanBuildingName(), ap.getMeasurementDate());
                    planFloors.add(planFloor);
                    deviations.add(ap.getFloorLevel() - planFloor);
                }
            } else {
                // 真·暂无数据
                bVo.setCurrentFloor(0);
                bVo.setCurrentHeight(0.0);
                bVo.setStatusTag("等待首次测量");
                bVo.setStatusColor("info");
                bVo.setLastMeasureDate("-");
                bVo.setOutdated(false);
                waiting++; // 真正的等待中
            }

            bVo.setDates(dates);
            bVo.setActualFloors(actualFloors);
            bVo.setPlanFloors(planFloors);
            bVo.setActualHeights(actualHeights);
            bVo.setDeviations(deviations);
            bVo.setPhotoCounts(photoCounts);

            buildingVos.add(bVo);
        }

        vo.setBuildings(buildingVos);
        vo.setDelayedCount(delayed);
        vo.setNormalCount(normal);
        vo.setAheadCount(ahead);
        vo.setWaitingCount(waiting); // 这里现在仅代表“无数据或无计划”的楼栋
        vo.setLastUpdateDate(maxDate == LocalDate.MIN ? "暂无" : maxDate.toString());

        return ApiResponse.success("获取成功", vo);
    }

    // 辅助：查某天计划楼层 (简化版，仅用于图表连线)
    private int getPlanFloorAtDate(String planName, LocalDate date) {
        if (planName == null) return 0;
        List<PlanProgress> plans = planProgressMapper.selectList(new QueryWrapper<PlanProgress>()
                .eq("Building", planName)
                .le("PlannedEnd", date.atTime(23, 59, 59)));
        int max = 0;
        for (PlanProgress p : plans) {
            try {
                String fStr = p.getFloor().replaceAll("[^0-9]", "");
                if (!fStr.isEmpty()) max = Math.max(max, Integer.parseInt(fStr));
            } catch (Exception e) {}
        }
        return max;
    }

    /**
     * 👉 4. ✅ 新增：批量保存计划进度
     * 前端传入：楼栋ID、总层数、每一层的计划时间列表
     */
    @PostMapping("/plan/save")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<String> savePlanConfig(@RequestBody PlanConfigDto dto) {
        // 1. 校验楼栋
        SysBuilding building = sysBuildingMapper.selectById(dto.getBuildingId());
        if (building == null) return ApiResponse.error("楼栋不存在");

        // 关键：PlanProgress 表使用的是 Navisworks 的模型名称 (Building 字段)
        // 所以我们必须确保当前楼栋已经绑定了模型名称
        String modelName = building.getPlanBuildingName();
        if (modelName == null || modelName.isEmpty()) {
            // 如果没绑定，默认用楼栋名作为模型名 (兼容逻辑)
            modelName = building.getName();
            // 更新回去，确保下次能对应上
            building.setPlanBuildingName(modelName);
            sysBuildingMapper.updateById(building);
        }

        // 2. 删除该楼栋旧的计划数据 (覆盖模式)
        planProgressMapper.deleteByBuildingName(modelName);

        // 3. 批量插入新数据
        for (PlanItem item : dto.getItems()) {
            PlanProgress p = new PlanProgress();
            p.setBuildingName(modelName); // 存入模型名
            p.setFloor(String.valueOf(item.getFloor())); // 存入层号

            // 处理时间
            if (item.getStartDate() != null) {
                p.setPlannedStart(LocalDate.parse(item.getStartDate()).atStartOfDay());
            }
            if (item.getEndDate() != null) {
                // 结束时间通常设为当天的最后一秒
                p.setPlannedEnd(LocalDate.parse(item.getEndDate()).atTime(23, 59, 59));
            }

            planProgressMapper.insert(p);
        }

        return ApiResponse.success("计划保存成功！已更新 " + dto.getItems().size() + " 层数据");
    }

    /**
     * 👉 5. ✅ 新增：获取计划进度列表 (用于前端回显)
     */
    @GetMapping("/plan/list")
    public ApiResponse<List<PlanItem>> getPlanList(@RequestParam Integer buildingId) {
        SysBuilding building = sysBuildingMapper.selectById(buildingId);
        if (building == null) return ApiResponse.error("楼栋不存在");

        // 优先使用模型名查询，如果没有则用楼栋名
        String modelName = building.getPlanBuildingName();
        if (modelName == null || modelName.isEmpty()) modelName = building.getName();

        List<PlanProgress> list = planProgressMapper.selectList(
            new QueryWrapper<PlanProgress>()
                .eq("Building", modelName)
                // 按楼层排序，这里需要注意 Floor 字段是 String，可能需要自定义排序逻辑，这里简单按字符串排
                // 实际生产中建议转成数字排序
        );

        // 转换成前端需要的 DTO
        List<PlanItem> result = new ArrayList<>();
        // 为了排序，我们可以简单提取数字
        list.sort((a, b) -> {
            int fa = extractInt(a.getFloor());
            int fb = extractInt(b.getFloor());
            return fa - fb;
        });

        for (PlanProgress p : list) {
            PlanItem item = new PlanItem();
            item.setFloor(extractInt(p.getFloor()));
            if (p.getPlannedStart() != null) item.setStartDate(p.getPlannedStart().toLocalDate().toString());
            if (p.getPlannedEnd() != null) item.setEndDate(p.getPlannedEnd().toLocalDate().toString());
            result.add(item);
        }
        return ApiResponse.success("获取成功", result);
    }

    // 辅助方法：从 "1F", "F1", "1" 中提取数字 1
    private int extractInt(String str) {
        try {
            return Integer.parseInt(str.replaceAll("[^0-9]", ""));
        } catch (Exception e) { return 0; }
    }

    /**
     * [重写版] 获取某栋楼的完整生长历史 (基于电子围栏 + 缓冲区匹配)
     * 解决了数据库没有 building_id 字段的问题
     */
    @GetMapping("/building/{buildingId}/history")
    public ApiResponse<List<BuildingHistoryVo>> getBuildingHistory(@PathVariable Integer buildingId) {
        // 1. 获取楼栋信息和电子围栏
        SysBuilding building = sysBuildingMapper.selectById(buildingId);
        if (building == null) return ApiResponse.error("楼栋不存在");

        List<Coordinate> fence = null;
        try {
            // 解析围栏 JSON
            ObjectMapper mapper = new ObjectMapper();
            String boundaryJson = building.getBoundaryCoords();
            if (boundaryJson != null && !boundaryJson.isEmpty()) {
                fence = mapper.readValue(boundaryJson, new TypeReference<List<Coordinate>>() {});
            }
        } catch (Exception e) {
            e.printStackTrace(); // 围栏解析失败，但这不影响查数据，只是没法配照片
        }

        // 2. 查询该楼栋的实测进度记录 (作为时间轴)
        QueryWrapper<ActualProgress> progressQuery = new QueryWrapper<>();
        progressQuery.eq("building_id", buildingId);
        progressQuery.orderByAsc("measurement_date");
        List<ActualProgress> progressList = actualProgressMapper.selectList(progressQuery);

        if (progressList == null || progressList.isEmpty()) {
            return ApiResponse.success("暂无历史数据", new ArrayList<>());
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<BuildingHistoryVo> historyList = new ArrayList<>();

        // 3. 遍历每一天的进度，去匹配当天的照片
        for (ActualProgress progress : progressList) {
            BuildingHistoryVo vo = new BuildingHistoryVo();

            // 3.1 填充基础数据
            String dateStr = "";
            if (progress.getMeasurementDate() != null) {
                dateStr = progress.getMeasurementDate().format(dtf);
            }
            vo.setDate(dateStr);
            vo.setFloor(progress.getFloorLevel());
            vo.setHeight(progress.getActualHeight() != null ? progress.getActualHeight().doubleValue() : 0.0);

            // 3.2 寻找匹配的照片 (核心逻辑！)
            String matchedUrl = "";

            // 如果有围栏数据，且日期有效，就开始找照片
            if (fence != null && fence.size() >= 3 && !dateStr.isEmpty()) {

                // A. 查出【整个项目】在【这一天】的所有照片
                QueryWrapper<ProjectPhoto> photoQuery = new QueryWrapper<>();
                photoQuery.select("photo_url", "gps_lat", "gps_lng", "lrf_target_lat", "lrf_target_lng");
                photoQuery.eq("project_id", progress.getProjectId());
                // 精确匹配日期
                photoQuery.apply("DATE_FORMAT(shoot_time, '%Y-%m-%d') = {0}", dateStr);
                // 限制条数，防止单日照片过多炸内存 (取前100张匹配即可)
                photoQuery.last("LIMIT 100");

                List<ProjectPhoto> dailyPhotos = projectPhotoMapper.selectList(photoQuery);

                // B. 遍历照片，判断哪一张在当前楼栋的围栏里
                for (ProjectPhoto p : dailyPhotos) {
                    // 优先取激光打点坐标，没有则取无人机GPS坐标
                    double lat = (p.getLrfTargetLat() != null) ? p.getLrfTargetLat().doubleValue() : (p.getGpsLat() != null ? p.getGpsLat().doubleValue() : 0.0);
                    double lng = (p.getLrfTargetLng() != null) ? p.getLrfTargetLng().doubleValue() : (p.getGpsLng() != null ? p.getGpsLng().doubleValue() : 0.0);

                    // 坐标无效跳过
                    if (lat == 0.0 || lng == 0.0) continue;

                    // 🔥 [核心调用] 使用你刚才复制进去的几何算法！
                    // 缓冲区设为 20.0 米 (和 ProgressServiceImpl 保持一致)
                    if (isInsideOrBuffered(lat, lng, fence, 20.0)) {
                        matchedUrl = p.getPhotoUrl(); // 找到了！
                        break; // 只要一张作为封面即可，跳出循环
                    }
                }
            }

            // 3.3 处理 OBS 签名 (私有桶访问权限)
            if (matchedUrl != null && !matchedUrl.isEmpty()) {
                String objectKey = matchedUrl;
                // 去掉开头的 "/" (如果有)
                if (objectKey.startsWith("/")) {
                    objectKey = objectKey.substring(1);
                }

                try {
                    // 生成临时签名 URL (有效期 1 小时)
                    TemporarySignatureRequest request = new TemporarySignatureRequest(HttpMethodEnum.GET, 3600L);
                    request.setBucketName(obsBucket); // 确保使用了配置里的桶名
                    request.setObjectKey(objectKey);

                    TemporarySignatureResponse response = obsClient.createTemporarySignature(request);
                    vo.setPhotoUrl(response.getSignedUrl());
                } catch (Exception e) {
                    e.printStackTrace();
                    vo.setPhotoUrl(""); // 签名失败降级为空
                }
            } else {
                vo.setPhotoUrl(""); // 没匹配到照片
            }

            historyList.add(vo);
        }

        return ApiResponse.success("获取生长历史成功", historyList);
    }

    /**
     * 🔥 [新增接口] 获取某栋楼、某一天在围栏内的【所有】照片
     * 用于前端点击图表后的“当日详情检视”模式
     */
    @GetMapping("/building/{buildingId}/{dateStr}/photos")
    public ApiResponse<List<String>> getBuildingDailyPhotos(@PathVariable Integer buildingId,
                                                            @PathVariable String dateStr) {
        // 1. 获取楼栋和围栏
        SysBuilding building = sysBuildingMapper.selectById(buildingId);
        if (building == null) return ApiResponse.error("楼栋不存在");

        List<Coordinate> fence = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String boundaryJson = building.getBoundaryCoords();
            if (boundaryJson != null && !boundaryJson.isEmpty()) {
                fence = mapper.readValue(boundaryJson, new TypeReference<List<Coordinate>>() {});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (fence == null || fence.size() < 3) {
            return ApiResponse.error("该楼栋未设置电子围栏，无法筛选照片");
        }

        // 2. 查出当天的所有照片
        QueryWrapper<ProjectPhoto> photoQuery = new QueryWrapper<>();
        photoQuery.select("photo_url", "gps_lat", "gps_lng", "lrf_target_lat", "lrf_target_lng");
        photoQuery.eq("project_id", building.getProjectId());
        photoQuery.apply("DATE_FORMAT(shoot_time, '%Y-%m-%d') = {0}", dateStr);
        photoQuery.orderByAsc("shoot_time"); // 按拍摄时间排序

        List<ProjectPhoto> dailyPhotos = projectPhotoMapper.selectList(photoQuery);
        List<String> validUrls = new ArrayList<>();

        // 3. 空间筛选 (保留围栏内的)
        for (ProjectPhoto p : dailyPhotos) {
            double lat = (p.getLrfTargetLat() != null) ? p.getLrfTargetLat().doubleValue() : (p.getGpsLat() != null ? p.getGpsLat().doubleValue() : 0.0);
            double lng = (p.getLrfTargetLng() != null) ? p.getLrfTargetLng().doubleValue() : (p.getGpsLng() != null ? p.getGpsLng().doubleValue() : 0.0);

            if (lat == 0 || lng == 0) continue;

            // 复用之前的几何算法
            if (isInsideOrBuffered(lat, lng, fence, 20.0)) {
                // 4. 签名 URL
                String signedUrl = "";
                try {
                    String objectKey = p.getPhotoUrl();
                    if (objectKey.startsWith("/")) objectKey = objectKey.substring(1);
                    TemporarySignatureRequest request = new TemporarySignatureRequest(HttpMethodEnum.GET, 3600L);
                    request.setBucketName(obsBucket);
                    request.setObjectKey(objectKey);
                    TemporarySignatureResponse response = obsClient.createTemporarySignature(request);
                    signedUrl = response.getSignedUrl();
                } catch (Exception e) {
                    signedUrl = p.getPhotoUrl(); // 降级
                }
                validUrls.add(signedUrl);
            }
        }

        return ApiResponse.success("获取当日照片成功", validUrls);
    }



    // =========================================================================
    // 🔥 [核心算法区] 电子围栏判定 (包含缓冲区逻辑，解决高层投影偏差)
    // =========================================================================

    /**
     * 判断点是否在多边形内或缓冲区内 (核心入口)
     * @param lat 纬度
     * @param lng 经度
     * @param polygon 围栏坐标点集合
     * @param bufferMeters 缓冲区距离 (例如 20.0米)
     */
    private boolean isInsideOrBuffered(double lat, double lng, List<Coordinate> polygon, double bufferMeters) {
        if (polygon == null || polygon.size() < 3) return false;

        // 1. 先判断是否精准在围栏内部 (射线法)
        if (isPointInPolygon(lat, lng, polygon)) return true;

        // 2. 如果不在内部，判断是否在边缘缓冲区内 (解决高楼投影偏差)
        return getMinDistanceToBoundary(lat, lng, polygon) <= bufferMeters;
    }

    /**
     * 射线法判断点是否在多边形内部
     */
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

    /**
     * 计算点到多边形边界的最小距离 (米)
     */
    private double getMinDistanceToBoundary(double lat, double lng, List<Coordinate> polygon) {
        double minDistance = Double.MAX_VALUE;
        // 简易墨卡托投影系数 (适用于小范围计算)
        double mPerLat = 111132.92;
        double mPerLng = 111412.84 * Math.cos(Math.toRadians(lat));

        for (int i = 0; i < polygon.size(); i++) {
            Coordinate p1 = polygon.get(i);
            Coordinate p2 = polygon.get((i + 1) % polygon.size());

            // 将经纬度差转换为米
            double x1 = (p1.getLng() - lng) * mPerLng;
            double y1 = (p1.getLat() - lat) * mPerLat;
            double x2 = (p2.getLng() - lng) * mPerLng;
            double y2 = (p2.getLat() - lat) * mPerLat;

            // 计算点到线段的距离
            double dist = pointToSegmentDistance(0, 0, x1, y1, x2, y2);
            if (dist < minDistance) minDistance = dist;
        }
        return minDistance;
    }

    /**
     * 计算点 (px,py) 到线段 (x1,y1)-(x2,y2) 的最短距离
     */
    private double pointToSegmentDistance(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;

        // 如果线段是一个点
        if (dx == 0 && dy == 0) return Math.hypot(px - x1, py - y1);

        // 计算投影比例 t
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);

        // 限制 t 在线段范围内 [0, 1]
        if (t < 0) t = 0;
        if (t > 1) t = 1;

        // 计算最近点坐标
        double nearestX = x1 + t * dx;
        double nearestY = y1 + t * dy;

        // 返回距离
        return Math.hypot(px - nearestX, py - nearestY);
    }




    // --- DTO 内部类 ---
    @Data
    public static class PlanConfigDto {
        private Integer projectId;
        private Integer buildingId;
        private List<PlanItem> items;
    }

    @Data
    public static class PlanItem {
        private Integer floor;
        private String startDate; // yyyy-MM-dd
        private String endDate;   // yyyy-MM-dd
    }
}