package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.Coordinate;
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

    /**
     * 1. 获取已保存的楼栋围栏列表
     * 作用：当用户打开地图页面时，先把之前已经画好的楼栋（1号楼、2号楼...）都显示出来，
     * 避免用户重复去画已经存在的楼。
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
     * 2. 获取指定“任务名称”下的所有照片坐标（拐点参考点）
     * 逻辑：用户输入任务名称（如“1号楼拐点”），后台去照片库里找路径包含这个名字的所有照片，
     * 把它们的 GPS 坐标取出来返给前端。用户看着这些点，就能把围栏画出来了。
     *
     * @param projectId 项目ID
     * @param taskName 任务名称 (例如 "1号楼拐点")
     */
    @GetMapping("/points")
    public ApiResponse<List<Coordinate>> getPhotoPoints(
            @RequestParam Integer projectId,
            @RequestParam(required = false) String taskName
    ) {
        QueryWrapper<ProjectPhoto> query = new QueryWrapper<>();
        query.eq("project_id", projectId);

        // 必须要有经纬度
        query.isNotNull("gps_lat");
        query.isNotNull("gps_lng");

        // ✅ 核心逻辑修正：根据用户输入的“任务名称”进行模糊匹配
        // 因为我们的路径是：projects/1/激光测距/1号楼拐点 2023.../xxx.jpg
        // 所以只要搜 "1号楼拐点"，就能匹配到对应的照片
        if (taskName != null && !taskName.isEmpty()) {
            query.like("photo_url", taskName);
        }

        // 限制数量，防止一次加载几万个点把浏览器卡死
        query.last("LIMIT 2000");

        List<ProjectPhoto> photos = projectPhotoMapper.selectList(query);

        // 提取坐标
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
     * 逻辑：前端把画好的一个个点传过来，后端存进数据库。
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

            return ApiResponse.success("电子围栏保存成功");

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("保存失败: " + e.getMessage());
        }
    }
}