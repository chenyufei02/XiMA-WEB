package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.Coordinate;
import com.whu.ximaweb.mapper.PlanProgressMapper;
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.mapper.SysBuildingMapper;
import com.whu.ximaweb.model.ProjectPhoto;
import com.whu.ximaweb.model.SysBuilding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 楼栋与电子围栏管理控制器
 */
@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    @Autowired
    private SysBuildingMapper sysBuildingMapper;

    @Autowired
    private ProjectPhotoMapper projectPhotoMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlanProgressMapper planProgressMapper;

    /**
     * 1. 获取已保存的楼栋围栏列表
     */
    @GetMapping("/list")
    public ApiResponse<List<SysBuilding>> getBuildings(@RequestParam Integer projectId) {
        QueryWrapper<SysBuilding> query = new QueryWrapper<>();
        query.eq("project_id", projectId);
        query.orderByAsc("id");
        List<SysBuilding> list = sysBuildingMapper.selectList(query);
        return ApiResponse.success("获取成功", list);
    }

    /**
     * 2. 获取指定“任务名称”下的所有照片坐标
     */
    @GetMapping("/points")
    public ApiResponse<List<Coordinate>> getPhotoPoints(
            @RequestParam Integer projectId,
            @RequestParam(required = false) String taskName
    ) {
        QueryWrapper<ProjectPhoto> query = new QueryWrapper<>();
        query.eq("project_id", projectId);
        query.isNotNull("gps_lat");
        query.isNotNull("gps_lng");

        if (taskName != null && !taskName.isEmpty()) {
            query.like("photo_url", taskName);
        }
        query.last("LIMIT 2000");

        List<ProjectPhoto> photos = projectPhotoMapper.selectList(query);
        List<Coordinate> points = new ArrayList<>();
        for (ProjectPhoto p : photos) {
            Coordinate c = new Coordinate();
            c.setLat(p.getGpsLat().doubleValue());
            c.setLng(p.getGpsLng().doubleValue());
            points.add(c);
        }
        return ApiResponse.success("查询成功，该任务下共有 " + points.size() + " 个测量点", points);
    }

    /**
     * 3. 保存用户画好的围栏
     * ✅ 修改：返回类型改为 ApiResponse<Integer>，返回楼栋ID，方便前端立刻进行绑定
     */
    @PostMapping("/boundary")
    public ApiResponse<Integer> saveBoundary(
            @RequestParam Integer projectId,
            @RequestParam String buildingName,
            @RequestBody List<Coordinate> coords) {

        if (coords == null || coords.size() < 3) {
            return ApiResponse.error("围栏至少需要3个坐标点才能构成闭合区域");
        }

        try {
            String jsonCoords = objectMapper.writeValueAsString(coords);

            QueryWrapper<SysBuilding> query = new QueryWrapper<>();
            query.eq("project_id", projectId);
            query.eq("name", buildingName);
            SysBuilding building = sysBuildingMapper.selectOne(query);

            if (building == null) {
                // 新增
                building = new SysBuilding();
                building.setProjectId(projectId);
                building.setName(buildingName);
                building.setBoundaryCoords(jsonCoords);
                building.setCreatedAt(LocalDateTime.now());
                sysBuildingMapper.insert(building);
            } else {
                // 更新
                building.setBoundaryCoords(jsonCoords);
                sysBuildingMapper.updateById(building);
            }

            // 返回 ID
            return ApiResponse.success("电子围栏保存成功", building.getId());

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("保存失败: " + e.getMessage());
        }
    }

    // ==========================================
    // ✅ 新增功能区 (用于手动绑定 Navisworks 楼名)
    // ==========================================

    /**
     * 4. 获取该项目下【还未绑定】的 Navisworks 计划楼名
     */
    @GetMapping("/options/unbound-plans")
    public ApiResponse<List<String>> getUnboundPlans(@RequestParam Integer projectId) {
        List<String> names = planProgressMapper.selectUnboundBuildingNames(projectId);
        return ApiResponse.success("获取成功", names);
    }

    /**
     * 5. 执行绑定 / 更新楼栋信息
     * ✅ 增强：支持通过 ID 查找（用于改名），也支持通过 Name 查找（用于新建）
     */
    @PostMapping("/bind")
    public ApiResponse<Object> bindPlan(@RequestBody Map<String, Object> payload) {
        // 尝试获取 ID (编辑模式会有)
        Integer id = null;
        if (payload.get("id") != null) {
            id = Integer.valueOf(payload.get("id").toString());
        }

        Integer projectId = null;
        if (payload.get("projectId") != null) {
            projectId = Integer.valueOf(payload.get("projectId").toString());
        }
        String currentName = (String) payload.get("currentName");

        String planName = (String) payload.get("planBuildingName");
        String displayName = (String) payload.get("displayName"); // 新名称

        SysBuilding building = null;

        // 1. 优先用 ID 找 (改名必须用 ID)
        if (id != null) {
            building = sysBuildingMapper.selectById(id);
        }
        // 2. 没有 ID 用名字找 (新建绑定)
        else {
            QueryWrapper<SysBuilding> query = new QueryWrapper<>();
            query.eq("project_id", projectId);
            query.eq("name", currentName);
            building = sysBuildingMapper.selectOne(query);
        }

        if (building == null) {
            return ApiResponse.error("楼栋不存在，请先保存围栏");
        }

        // 修改名称 (如果用户改了)
        if (displayName != null && !displayName.isEmpty()) {
            building.setName(displayName);
        }
        // 更新绑定关系
        building.setPlanBuildingName(planName);
        sysBuildingMapper.updateById(building);

        return ApiResponse.success("绑定成功");
    }

    /**
     * 6. 删除楼栋
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Object> deleteBuilding(@PathVariable Integer id) {
        sysBuildingMapper.deleteById(id);
        return ApiResponse.success("删除成功");
    }
}