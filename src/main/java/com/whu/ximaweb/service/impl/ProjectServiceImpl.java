package com.whu.ximaweb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ProjectImportRequest;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.mapper.SysUserProjectMapper;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importProject(ProjectImportRequest request, Integer userId) {
        // 1. 保存项目配置
        SysProject project = new SysProject();
        project.setProjectName(request.getProjectName());
        project.setPhotoFolderKeyword(request.getPhotoFolderKeyword());

        project.setDjiOrgKey(request.getDjiOrgKey());
        project.setDjiProjectUuid(request.getDjiProjectUuid());

        project.setObsEndpoint(request.getObsEndpoint());
        project.setObsBucketName(request.getObsBucketName());
        project.setObsAk(request.getObsAk());
        project.setObsSk(request.getObsSk());

        project.setCreatedBy(userId);
        project.setCreatedAt(LocalDateTime.now());

        sysProjectMapper.insert(project);

        // 2. 建立关联
        SysUserProject relation = new SysUserProject();
        relation.setUserId(userId);
        relation.setProjectId(project.getId());

        sysUserProjectMapper.insert(relation);
    }

    @Override
    public List<SysProject> getUserProjects(Integer userId) {
        // 1. 查关联
        QueryWrapper<SysUserProject> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        List<SysUserProject> relations = sysUserProjectMapper.selectList(query);

        if (relations.isEmpty()) {
            return List.of();
        }

        List<Integer> projectIds = relations.stream()
                .map(SysUserProject::getProjectId)
                .collect(Collectors.toList());

        // 2. 查详情
        return sysProjectMapper.selectBatchIds(projectIds);
    }

    /**
     * 更新项目的电子围栏
     * @param projectId 项目ID
     * @param coords 前端传来的坐标点列表
     */
    public void updateBoundary(Integer projectId, java.util.List<com.whu.ximaweb.dto.Coordinate> coords) {
        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        try {
            // 把 List<Coordinate> 转成 JSON 字符串存入数据库
            // 例如转成String: "[{\"lat\":30.1,\"lng\":114.2},...]"
            String jsonCoords = objectMapper.writeValueAsString(coords);

            project.setBoundaryCoords(jsonCoords);
            sysProjectMapper.updateById(project);

            System.out.println("项目 [" + projectId + "] 围栏更新成功: " + jsonCoords);
        } catch (Exception e) {
            throw new RuntimeException("坐标保存失败: " + e.getMessage());
        }
    }
}