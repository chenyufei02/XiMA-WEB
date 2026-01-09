package com.whu.ximaweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ProjectImportRequest;
import com.whu.ximaweb.mapper.SysBuildingMapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.mapper.SysUserProjectMapper;
import com.whu.ximaweb.model.SysBuilding;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.model.SysUserProject;
import com.whu.ximaweb.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private SysProjectMapper sysProjectMapper;

    @Autowired
    private SysUserProjectMapper sysUserProjectMapper;

    @Autowired
    private SysBuildingMapper sysBuildingMapper;

    /**
     * 导入项目（已修复重复导入报错的问题）
     * 逻辑：如果项目已存在，则更新配置；如果不存在，则新建。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importProject(ProjectImportRequest request, Integer userId) {
        // 1. 检查该用户下是否已存在同名项目
        QueryWrapper<SysProject> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("project_name", request.getProjectName());
        queryWrapper.eq("created_by", userId);
        SysProject existingProject = sysProjectMapper.selectOne(queryWrapper);

        if (existingProject != null) {
            // --- 情况A：项目已存在，更新配置 ---
            existingProject.setPhotoFolderKeyword(request.getPhotoFolderKeyword());
            existingProject.setDjiProjectUuid(request.getDjiProjectUuid());
            existingProject.setDjiOrgKey(request.getDjiOrgKey());
            existingProject.setObsBucketName(request.getObsBucketName());
            existingProject.setObsAk(request.getObsAk());
            existingProject.setObsSk(request.getObsSk());
            existingProject.setObsEndpoint(request.getObsEndpoint());
            // 更新修改时间等（可选）
            sysProjectMapper.updateById(existingProject);
        } else {
            // --- 情况B：项目不存在，执行新建 ---
            SysProject project = new SysProject();
            project.setProjectName(request.getProjectName());
            project.setPhotoFolderKeyword(request.getPhotoFolderKeyword());
            project.setDjiProjectUuid(request.getDjiProjectUuid());
            project.setDjiOrgKey(request.getDjiOrgKey());
            project.setObsBucketName(request.getObsBucketName());
            project.setObsAk(request.getObsAk());
            project.setObsSk(request.getObsSk());
            project.setObsEndpoint(request.getObsEndpoint());

            project.setCreatedBy(userId);
            project.setCreatedAt(LocalDateTime.now());

            sysProjectMapper.insert(project);

            // 建立用户与项目的关联
            SysUserProject relation = new SysUserProject();
            relation.setUserId(userId);
            relation.setProjectId(project.getId());
            sysUserProjectMapper.insert(relation);
        }
    }

    /**
     * 获取用户的所有项目（保留原有功能）
     */
    @Override
    public List<SysProject> getUserProjects(Integer userId) {
        // 1. 查关联表
        QueryWrapper<SysUserProject> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        List<SysUserProject> relations = sysUserProjectMapper.selectList(query);

        if (relations.isEmpty()) {
            return List.of();
        }

        List<Integer> projectIds = relations.stream()
                .map(SysUserProject::getProjectId)
                .collect(Collectors.toList());

        // 2. 查项目详情
        return sysProjectMapper.selectBatchIds(projectIds);
    }

    /**
     * 更新项目的电子围栏
     */
    public void updateBoundary(Integer projectId, java.util.List<com.whu.ximaweb.dto.Coordinate> coords) {
        // 1. 检查项目是否存在
        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        try {
            String jsonCoords = objectMapper.writeValueAsString(coords);

            // 2. 查找该项目下是否已有楼栋
            QueryWrapper<SysBuilding> query = new QueryWrapper<>();
            query.eq("project_id", projectId);
            query.last("LIMIT 1");
            SysBuilding building = sysBuildingMapper.selectOne(query);

            if (building == null) {
                // 如果没有，自动创建一个默认楼栋
                building = new SysBuilding();
                building.setProjectId(projectId);
                building.setName("默认教学楼");
                building.setBoundaryCoords(jsonCoords);
                building.setCreatedAt(LocalDateTime.now());
                sysBuildingMapper.insert(building);
            } else {
                // 如果有，更新它
                building.setBoundaryCoords(jsonCoords);
                sysBuildingMapper.updateById(building);
            }

        } catch (Exception e) {
            throw new RuntimeException("坐标保存失败: " + e.getMessage());
        }
    }
}