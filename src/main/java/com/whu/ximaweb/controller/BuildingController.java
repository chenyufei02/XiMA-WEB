package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.Coordinate;
import com.whu.ximaweb.mapper.SysBuildingMapper;
import com.whu.ximaweb.model.SysBuilding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 楼栋与电子围栏管理控制器
 * 职责：管理项目下的单体楼栋信息，以及对应的电子围栏坐标
 */
@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    @Autowired
    private SysBuildingMapper sysBuildingMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取指定项目下的所有楼栋信息（包含围栏坐标）
     * 前端调用时机：进入“定义电子围栏”页面时，加载已有的楼栋列表
     * @param projectId 项目ID
     */
    @GetMapping("/list")
    public ApiResponse<List<SysBuilding>> getBuildings(@RequestParam Integer projectId) {
        QueryWrapper<SysBuilding> query = new QueryWrapper<>();
        query.eq("project_id", projectId);
        query.orderByAsc("id"); // 按添加顺序排序
        List<SysBuilding> list = sysBuildingMapper.selectList(query);
        return ApiResponse.success("获取成功", list);
    }

    /**
     * 保存或更新某栋楼的电子围栏
     * 逻辑：根据项目ID和楼栋名称查找。如果存在则更新围栏，不存在则创建新楼栋。
     *
     * @param projectId 项目ID
     * @param buildingName 楼栋名称 (如 "1号楼")
     * @param coords 围栏坐标点列表
     */
    @PostMapping("/boundary")
    public ApiResponse<String> saveBoundary(
            @RequestParam Integer projectId,
            @RequestParam String buildingName,
            @RequestBody List<Coordinate> coords) {

        if (coords == null || coords.size() < 3) {
            return ApiResponse.error("围栏至少需要3个坐标点才能构成闭合区域");
        }

        try {
            // 1. 将坐标对象转换为 JSON 字符串存储
            String jsonCoords = objectMapper.writeValueAsString(coords);

            // 2. 查询该项目下是否已存在同名楼栋
            QueryWrapper<SysBuilding> query = new QueryWrapper<>();
            query.eq("project_id", projectId);
            query.eq("name", buildingName);
            SysBuilding building = sysBuildingMapper.selectOne(query);

            if (building == null) {
                // --- 新增逻辑 ---
                building = new SysBuilding();
                building.setProjectId(projectId);
                building.setName(buildingName);
                building.setBoundaryCoords(jsonCoords);
                building.setCreatedAt(LocalDateTime.now());
                sysBuildingMapper.insert(building);
            } else {
                // --- 更新逻辑 ---
                building.setBoundaryCoords(jsonCoords);
                sysBuildingMapper.updateById(building);
            }

            return ApiResponse.success("电子围栏保存成功");

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 解析楼栋的围栏坐标 (辅助工具方法，供以后 Service 调用，暂留此处演示用法)
     * 说明：数据库里存的是 String (JSON)，用的时候需要转回 List<Coordinate>
     */
    private List<Coordinate> parseCoords(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Coordinate>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}