package com.whu.ximaweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ProjectImportRequest;
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.mapper.SysBuildingMapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.mapper.SysUserProjectMapper;
import com.whu.ximaweb.model.ProjectPhoto;
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

    @Autowired
    private ProjectPhotoMapper projectPhotoMapper;

    /**
     * ✅ 核心升级：导入项目
     * 逻辑：如果 (UUID + UserId) 已存在，直接报错，禁止重复导入。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importProject(ProjectImportRequest request, Integer userId) {
        // 1. 严格查重：检查当前用户是否已有该大疆项目
        QueryWrapper<SysProject> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dji_project_uuid", request.getDjiProjectUuid());
        queryWrapper.eq("created_by", userId);

        SysProject existingProject = sysProjectMapper.selectOne(queryWrapper);

        if (existingProject != null) {
            // ❌ 拦截：该用户已导入过此项目
            throw new RuntimeException("该项目 [" + existingProject.getProjectName() + "] 已经是您的项目了，请勿重复导入！");
        }

        // 2. 执行新建流程
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

        // 3. 关联表插入
        SysUserProject relation = new SysUserProject();
        relation.setUserId(userId);
        relation.setProjectId(project.getId());
        sysUserProjectMapper.insert(relation);
    }

    // ... 其他方法保持不变 (getUserProjects, deleteProject, updateProjectInfo, updateBoundary) ...

    @Override
    public List<SysProject> getUserProjects(Integer userId) {
        QueryWrapper<SysUserProject> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        List<SysUserProject> relations = sysUserProjectMapper.selectList(query);
        if (relations.isEmpty()) return List.of();
        List<Integer> projectIds = relations.stream().map(SysUserProject::getProjectId).collect(Collectors.toList());
        return sysProjectMapper.selectBatchIds(projectIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Integer projectId) {
        QueryWrapper<ProjectPhoto> photoQuery = new QueryWrapper<>();
        photoQuery.eq("project_id", projectId);
        projectPhotoMapper.delete(photoQuery);

        QueryWrapper<SysBuilding> buildingQuery = new QueryWrapper<>();
        buildingQuery.eq("project_id", projectId);
        sysBuildingMapper.delete(buildingQuery);

        QueryWrapper<SysUserProject> relationQuery = new QueryWrapper<>();
        relationQuery.eq("project_id", projectId);
        sysUserProjectMapper.delete(relationQuery);

        sysProjectMapper.deleteById(projectId);
    }

    @Override
    public boolean updateProjectInfo(SysProject project) {
        SysProject old = sysProjectMapper.selectById(project.getId());
        if (old == null) return false;
        old.setProjectName(project.getProjectName());
        old.setPhotoFolderKeyword(project.getPhotoFolderKeyword());
        return sysProjectMapper.updateById(old) > 0;
    }

    public void updateBoundary(Integer projectId, java.util.List<com.whu.ximaweb.dto.Coordinate> coords) {
        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) throw new RuntimeException("项目不存在");
        try {
            String jsonCoords = objectMapper.writeValueAsString(coords);
            QueryWrapper<SysBuilding> query = new QueryWrapper<>();
            query.eq("project_id", projectId);
            query.last("LIMIT 1");
            SysBuilding building = sysBuildingMapper.selectOne(query);
            if (building == null) {
                building = new SysBuilding();
                building.setProjectId(projectId);
                building.setName("默认教学楼");
                building.setBoundaryCoords(jsonCoords);
                building.setCreatedAt(LocalDateTime.now());
                sysBuildingMapper.insert(building);
            } else {
                building.setBoundaryCoords(jsonCoords);
                sysBuildingMapper.updateById(building);
            }
        } catch (Exception e) {
            throw new RuntimeException("坐标保存失败: " + e.getMessage());
        }
    }
}