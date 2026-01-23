package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.Coordinate;
import com.whu.ximaweb.mapper.ActualProgressMapper;
import com.whu.ximaweb.mapper.PlanProgressMapper;
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.mapper.SysBuildingMapper;
import com.whu.ximaweb.model.ActualProgress;
import com.whu.ximaweb.model.ProjectPhoto;
import com.whu.ximaweb.model.SysBuilding;
import com.whu.ximaweb.service.ProgressService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Autowired
    private ActualProgressMapper actualProgressMapper;

    @Autowired
    private ProgressService progressService;

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
     */
    @PostMapping("/boundary")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Integer> saveBoundary(@RequestBody BoundarySaveRequest request) {

        if (request.getCoords() == null || request.getCoords().size() < 3) {
            return ApiResponse.error("围栏至少需要3个坐标点才能构成闭合区域");
        }

        try {
            String jsonCoords = objectMapper.writeValueAsString(request.getCoords());

            // 转换照片ID列表为字符串
            String photoIdsStr = null;
            if (request.getPhotoIds() != null && !request.getPhotoIds().isEmpty()) {
                photoIdsStr = request.getPhotoIds().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
            }

            QueryWrapper<SysBuilding> query = new QueryWrapper<>();
            query.eq("project_id", request.getProjectId());
            query.eq("name", request.getBuildingName());
            SysBuilding building = sysBuildingMapper.selectOne(query);

            if (building == null) {
                // 新增
                building = new SysBuilding();
                building.setProjectId(request.getProjectId());
                building.setName(request.getBuildingName());
                building.setBoundaryCoords(jsonCoords);
                building.setMarkerPhotoIds(photoIdsStr); // ✅ 保存关联ID
                building.setCreatedAt(LocalDateTime.now());
                sysBuildingMapper.insert(building);
            } else {
                // 更新前，先复位旧的拐点标记
                if (building.getMarkerPhotoIds() != null && !building.getMarkerPhotoIds().isEmpty()) {
                    resetPhotosMarker(building.getMarkerPhotoIds());
                }

                building.setBoundaryCoords(jsonCoords);
                building.setMarkerPhotoIds(photoIdsStr); // ✅ 更新关联ID
                sysBuildingMapper.updateById(building);
            }

            // 标记新选中的照片为"拐点" (is_marker=1)
            if (request.getPhotoIds() != null && !request.getPhotoIds().isEmpty()) {
                ProjectPhoto updatePhoto = new ProjectPhoto();
                updatePhoto.setIsMarker(true);
                QueryWrapper<ProjectPhoto> photoQuery = new QueryWrapper<>();
                photoQuery.in("id", request.getPhotoIds());
                projectPhotoMapper.update(updatePhoto, photoQuery);
            }

            // 触发计算
            try {
                System.out.println(">>> 围栏更新成功，触发项目 [" + request.getProjectId() + "] 进度重算...");
                progressService.calculateProjectProgress(request.getProjectId());
            } catch (Exception e) {
                e.printStackTrace();
            }

            return ApiResponse.success("电子围栏保存成功，进度已更新", building.getId());

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 4. 获取未绑定计划
     */
    @GetMapping("/options/unbound-plans")
    public ApiResponse<List<String>> getUnboundPlans(@RequestParam Integer projectId) {
        List<String> names = planProgressMapper.selectUnboundBuildingNames(projectId);
        return ApiResponse.success("获取成功", names);
    }

    /**
     * 5. 执行绑定
     */
    @PostMapping("/bind")
    public ApiResponse<Object> bindPlan(@RequestBody Map<String, Object> payload) {
        Integer id = null;
        if (payload.get("id") != null) id = Integer.valueOf(payload.get("id").toString());
        Integer projectId = null;
        if (payload.get("projectId") != null) projectId = Integer.valueOf(payload.get("projectId").toString());
        String currentName = (String) payload.get("currentName");
        String planName = (String) payload.get("planBuildingName");
        String displayName = (String) payload.get("displayName");

        SysBuilding building = null;
        if (id != null) {
            building = sysBuildingMapper.selectById(id);
        } else {
            QueryWrapper<SysBuilding> query = new QueryWrapper<>();
            query.eq("project_id", projectId);
            query.eq("name", currentName);
            building = sysBuildingMapper.selectOne(query);
        }

        if (building == null) return ApiResponse.error("楼栋不存在");

        if (displayName != null && !displayName.isEmpty()) building.setName(displayName);
        building.setPlanBuildingName(planName);
        sysBuildingMapper.updateById(building);

        return ApiResponse.success("绑定成功");
    }

    /**
     * 6. 删除楼栋 (级联删除 + 复位标记)
     */
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Object> deleteBuilding(@PathVariable Integer id) {
        SysBuilding building = sysBuildingMapper.selectById(id);
        if (building == null) return ApiResponse.success("楼栋不存在或已删除");

        // 复位照片标记
        if (building.getMarkerPhotoIds() != null && !building.getMarkerPhotoIds().isEmpty()) {
            resetPhotosMarker(building.getMarkerPhotoIds());
        }

        // 级联删除进度
        QueryWrapper<ActualProgress> progressQuery = new QueryWrapper<>();
        progressQuery.eq("building_id", id);
        actualProgressMapper.delete(progressQuery);

        // 删除楼栋
        sysBuildingMapper.deleteById(id);

        return ApiResponse.success("删除成功");
    }

    /**
     * 辅助方法：复位照片标记
     */
    private void resetPhotosMarker(String photoIdsStr) {
        try {
            if (photoIdsStr == null || photoIdsStr.trim().isEmpty()) return;

            // 使用 String.split 可能包含空字符串，需要过滤
            List<String> idList = Arrays.stream(photoIdsStr.split(","))
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty())
                                        .collect(Collectors.toList());

            if (!idList.isEmpty()) {
                UpdateWrapper<ProjectPhoto> updateWrapper = new UpdateWrapper<>();
                updateWrapper.in("id", idList);
                updateWrapper.set("is_marker", false);
                projectPhotoMapper.update(null, updateWrapper);
            }
        } catch (Exception e) {
            System.err.println("复位照片标记失败: " + e.getMessage());
        }
    }

    @Data
    public static class BoundarySaveRequest {
        private Integer projectId;
        private String buildingName;
        private List<Coordinate> coords;
        private List<Long> photoIds;
    }
}