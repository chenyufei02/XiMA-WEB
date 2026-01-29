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
     * [新增接口] 获取某栋楼的完整生长历史 (已修复日期类型报错)
     */
    @GetMapping("/building/{buildingId}/history")
    public ApiResponse<List<BuildingHistoryVo>> getBuildingHistory(@PathVariable Integer buildingId) {
        // 1. 查询实测记录
        QueryWrapper<ActualProgress> progressQuery = new QueryWrapper<>();
        progressQuery.eq("building_id", buildingId);
        progressQuery.orderByAsc("measurement_date");
        List<ActualProgress> progressList = actualProgressMapper.selectList(progressQuery);

        if (progressList == null || progressList.isEmpty()) {
            return ApiResponse.success("暂无历史数据", new ArrayList<>());
        }

        // 🔥 [修复] 使用 DateTimeFormatter 处理 LocalDate
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<BuildingHistoryVo> historyList = new ArrayList<>();

        for (ActualProgress progress : progressList) {
            BuildingHistoryVo vo = new BuildingHistoryVo();

            // 🔥 [修复] LocalDate 转 String
            String dateStr = "";
            if (progress.getMeasurementDate() != null) {
                dateStr = progress.getMeasurementDate().format(dtf);
            }
            vo.setDate(dateStr);

            vo.setFloor(progress.getFloorLevel());
            vo.setHeight(progress.getActualHeight() != null ? progress.getActualHeight().doubleValue() : 0.0);

            // 2. 查照片
            QueryWrapper<ProjectPhoto> photoQuery = new QueryWrapper<>();
            photoQuery.select("photo_url"); // 只查 URL 字段，轻量化
            photoQuery.eq("project_id", progress.getProjectId());

            // 匹配日期 (假设数据库里 shoot_time 是 datetime 类型)
            // SQL: DATE_FORMAT(shoot_time, '%Y-%m-%d') = '2026-01-26'
            if (!dateStr.isEmpty()) {
                photoQuery.apply("DATE_FORMAT(shoot_time, '%Y-%m-%d') = {0}", dateStr);
            }

            photoQuery.last("LIMIT 1"); // 只要一张

            ProjectPhoto photo = projectPhotoMapper.selectOne(photoQuery);

            if (photo != null) {
                String objectKey = photo.getPhotoUrl();

                if (objectKey != null && !objectKey.isEmpty()) {
                    // 1. 清理 ObjectKey：OBS 不喜欢以 "/" 开头的路径
                    // 如果数据库存的是 "/projects/..."，要去掉第一个斜杠变成 "projects/..."
                    if (objectKey.startsWith("/")) {
                        objectKey = objectKey.substring(1);
                    }

                    // 2. 生成临时签名 URL (有效期 3600秒 = 1小时)
                    try {
                        TemporarySignatureRequest request = new TemporarySignatureRequest(
                                HttpMethodEnum.GET,
                                3600L
                        );
                        request.setBucketName(obsBucket);
                        request.setObjectKey(objectKey);

                        // 生成带签名的响应
                        TemporarySignatureResponse response = obsClient.createTemporarySignature(request);

                        // 3. 拿到那个带一长串 Token 的安全链接
                        vo.setPhotoUrl(response.getSignedUrl());

                    } catch (Exception e) {
                        // 万一签名失败，降级为空，防止接口崩了
                        e.printStackTrace();
                        vo.setPhotoUrl("");
                    }
                }
            } else {
                vo.setPhotoUrl("");
            }

            historyList.add(vo);
        }

        return ApiResponse.success("获取生长历史成功", historyList);
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