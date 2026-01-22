package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.mapper.BuildingFloorInfoMapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.model.BuildingFloorInfo;
import com.whu.ximaweb.model.SysProject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/floors")
public class BuildingFloorController {

    @Autowired
    private BuildingFloorInfoMapper floorMapper;

    @Autowired
    private SysProjectMapper sysProjectMapper;

    /**
     * 获取指定楼栋的楼层配置列表
     */
    @GetMapping("/list")
    public ApiResponse<List<BuildingFloorInfo>> getFloorList(@RequestParam Integer buildingId) {
        QueryWrapper<BuildingFloorInfo> query = new QueryWrapper<>();
        query.eq("building_id", buildingId);
        query.orderByAsc("floor_number"); // 按楼层从小到大排序
        return ApiResponse.success("获取成功", floorMapper.selectList(query));
    }

    /**
     * 保存/重置某栋楼的楼层配置
     * 逻辑：先删除该楼栋所有旧记录，再插入新记录
     */
    @PostMapping("/save")
    @Transactional(rollbackFor = Exception.class) // 开启事务，保证删和增要么同时成功，要么同时失败
    public ApiResponse<String> saveFloors(
            @RequestParam Integer projectId,
            @RequestParam Integer buildingId,
            @RequestBody List<BuildingFloorInfo> floorList) {

        if (floorList == null || floorList.isEmpty()) {
            return ApiResponse.error("楼层列表不能为空");
        }

        // 1. 获取项目名称 (补全冗余字段)
        SysProject project = sysProjectMapper.selectById(projectId);
        String projectName = (project != null) ? project.getProjectName() : "";

        // 2. 删除旧数据 (覆盖模式)
        QueryWrapper<BuildingFloorInfo> deleteQuery = new QueryWrapper<>();
        deleteQuery.eq("building_id", buildingId);
        floorMapper.delete(deleteQuery);

        // 3. 批量插入新数据
        for (BuildingFloorInfo info : floorList) {
            info.setId(null); // 确保ID为null，触发自增
            info.setProjectId(projectId);
            info.setBuildingId(buildingId);
            info.setProjectName(projectName);
            floorMapper.insert(info);
        }

        return ApiResponse.success("楼层配置保存成功，共保存 " + floorList.size() + " 层");
    }
}