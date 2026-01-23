package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.DashboardVo;
import com.whu.ximaweb.mapper.*;
import com.whu.ximaweb.model.*;
import com.whu.ximaweb.service.ProgressService;
import com.whu.ximaweb.service.impl.ProgressServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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
     * 👉 3. ✅ 新增核心接口：获取项目看板详情数据 (Step 4 新增)
     * 包含：顶部指标卡、每栋楼的状态、三张图表的所有数据点
     * 调用方式：GET /api/progress/dashboard/1
     */
    @GetMapping("/dashboard/{projectId}")
    public ApiResponse<DashboardVo> getDashboardData(@PathVariable Integer projectId) {
        DashboardVo vo = new DashboardVo();

        // 1. 基础信息
        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) return ApiResponse.error("项目不存在");

        vo.setProjectName(project.getProjectName());
        // 计算安全运行天数 (从创建到现在)
        long days = ChronoUnit.DAYS.between(project.getCreatedAt().toLocalDate(), LocalDate.now());
        vo.setSafeRunDays(days);

        // 2. 获取楼栋列表
        List<SysBuilding> buildings = sysBuildingMapper.selectList(
            new QueryWrapper<SysBuilding>().eq("project_id", projectId)
        );
        vo.setTotalBuildings(buildings.size());

        List<DashboardVo.BuildingProgressVo> buildingVos = new ArrayList<>();
        int delayed = 0, normal = 0, ahead = 0, waiting = 0;
        LocalDate maxDate = LocalDate.MIN;

        // 3. 遍历楼栋计算状态
        for (SysBuilding b : buildings) {
            DashboardVo.BuildingProgressVo bVo = new DashboardVo.BuildingProgressVo();
            bVo.setBuildingId(b.getId());
            bVo.setBuildingName(b.getName());
            bVo.setPlanName(b.getPlanBuildingName());

            // 3.1 获取实际进度历史 (按时间排序)
            List<ActualProgress> history = actualProgressMapper.selectList(
                new QueryWrapper<ActualProgress>()
                    .eq("building_id", b.getId())
                    .orderByAsc("measurement_date")
            );

            // 准备图表容器
            List<String> dates = new ArrayList<>();
            List<Integer> actualFloors = new ArrayList<>();
            List<Integer> planFloors = new ArrayList<>();
            List<Double> actualHeights = new ArrayList<>();
            List<Integer> deviations = new ArrayList<>();

            if (!history.isEmpty()) {
                // 取最新一条状态
                ActualProgress latest = history.get(history.size() - 1);
                bVo.setCurrentFloor(latest.getFloorLevel());
                bVo.setCurrentHeight(latest.getActualHeight().doubleValue());
                bVo.setLastMeasureDate(latest.getMeasurementDate().toString());

                // 更新项目最后更新时间
                if (latest.getMeasurementDate().isAfter(maxDate)) maxDate = latest.getMeasurementDate();

                // 判断时效性 (>7天为过期)
                long gap = ChronoUnit.DAYS.between(latest.getMeasurementDate(), LocalDate.now());
                boolean isOutdated = gap > 7;
                bVo.setOutdated(isOutdated);

                // 计算状态 (使用 Service 中的逻辑)
                String status = "暂无计划";
                String color = "info";

                if (isOutdated) {
                    status = "暂无新数据"; // 超过7天，强制显示此状态
                    color = "warning"; // 黄色
                    waiting++;
                } else {
                    // 数据新鲜，进行计划对比
                    if (b.getPlanBuildingName() != null) {
                        status = progressServiceImpl.analyzeStatus(b.getPlanBuildingName(), latest.getFloorLevel(), latest.getMeasurementDate());
                    }
                    // 确定颜色
                    if (status.contains("滞后")) {
                        color = "danger"; // 红色
                        delayed++;
                    } else if (status.contains("超前")) {
                        color = "success"; // 绿色
                        ahead++;
                    } else if (status.contains("正常")) {
                        color = "primary"; // 蓝色
                        normal++;
                    } else {
                        // 暂无计划
                        waiting++;
                    }
                }
                bVo.setStatusTag(status);
                bVo.setStatusColor(color);

                // 3.2 填充图表数据
                for (ActualProgress ap : history) {
                    dates.add(ap.getMeasurementDate().toString());
                    actualFloors.add(ap.getFloorLevel());
                    actualHeights.add(ap.getActualHeight().doubleValue()); // 图2数据

                    // 查当天的计划楼层 (用于画对比线)
                    int planFloor = getPlanFloorAtDate(b.getPlanBuildingName(), ap.getMeasurementDate());
                    planFloors.add(planFloor);

                    // 计算偏差 (图3数据)
                    deviations.add(ap.getFloorLevel() - planFloor);
                }
            } else {
                // 暂无数据
                bVo.setCurrentFloor(0);
                bVo.setCurrentHeight(0.0);
                bVo.setStatusTag("等待首次测量");
                bVo.setStatusColor("info");
                bVo.setLastMeasureDate("-");
                bVo.setOutdated(false);
                waiting++;
            }

            bVo.setDates(dates);
            bVo.setActualFloors(actualFloors);
            bVo.setPlanFloors(planFloors);
            bVo.setActualHeights(actualHeights);
            bVo.setDeviations(deviations);

            buildingVos.add(bVo);
        }

        vo.setBuildings(buildingVos);
        vo.setDelayedCount(delayed);
        vo.setNormalCount(normal);
        vo.setAheadCount(ahead);
        vo.setWaitingCount(waiting);
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
}