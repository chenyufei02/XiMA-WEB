package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.model.ActualProgress;
import com.whu.ximaweb.mapper.ActualProgressMapper;
import com.whu.ximaweb.service.ProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 进度管理控制器
 * 负责触发计算任务、获取进度图表数据
 */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    @Autowired
    private ProgressService progressService;

    @Autowired
    private ActualProgressMapper actualProgressMapper;

    /**
     * 👉 手动触发计算接口
     * 作用：让系统根据当前的围栏，把历史所有照片重新跑一遍，算出每一天的进度。
     * 调用方式：POST /api/progress/calculate?projectId=1
     */
    @PostMapping("/calculate")
    public ApiResponse<String> calculateProgress(@RequestParam Integer projectId) {
        try {
            long start = System.currentTimeMillis();
            progressService.calculateProjectProgress(projectId);
            long end = System.currentTimeMillis();
            return ApiResponse.success("计算完成！耗时: " + (end - start) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("计算失败: " + e.getMessage());
        }
    }

    /**
     * 👉 获取图表数据接口
     * 作用：前端画折线图时，通过这个接口获取数据
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
}