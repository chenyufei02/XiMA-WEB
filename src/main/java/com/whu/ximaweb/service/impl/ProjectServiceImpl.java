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
import org.springframework.beans.factory.annotation.Value; // ✅ 新增：读取配置
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils; // ✅ 新增：工具类

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

    // =========================================================================
    // ✅ 核心修改：注入系统默认配置 (来自 application.properties)
    // =========================================================================
    @Value("${xima.obs.default-endpoint:obs.cn-south-1.myhuaweicloud.com}")
    private String defaultEndpoint;

    @Value("${xima.obs.default-bucket:xima-prod}")
    private String defaultBucket;

    @Value("${xima.obs.default-ak}")
    private String defaultAk;

    @Value("${xima.obs.default-sk}")
    private String defaultSk;

    /**
     * ✅ 核心升级：导入项目
     * 逻辑：如果 (UUID + UserId) 已存在，直接报错，禁止重复导入。
     * 安全升级：如果是系统托管模式（前端没传AK），自动填充默认AK/SK。
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

        // 2. 智能配置填充 (新增逻辑)
        // 如果前端传了(不为空)，就用前端的；如果前端没传(为空)，就用后端的默认值
        String targetAk = StringUtils.hasText(request.getObsAk()) ? request.getObsAk() : defaultAk;
        String targetSk = StringUtils.hasText(request.getObsSk()) ? request.getObsSk() : defaultSk;
        String targetBucket = StringUtils.hasText(request.getObsBucketName()) ? request.getObsBucketName() : defaultBucket;
        String targetEndpoint = StringUtils.hasText(request.getObsEndpoint()) ? request.getObsEndpoint() : defaultEndpoint;

        // 文件夹关键词：如果不填，默认为 '激光测距'
        String folderKeyword = StringUtils.hasText(request.getPhotoFolderKeyword())
                               ? request.getPhotoFolderKeyword()
                               : "激光测距";

        // 3. 执行新建流程
        SysProject project = new SysProject();
        project.setProjectName(request.getProjectName());
        project.setDjiProjectUuid(request.getDjiProjectUuid());
        project.setDjiOrgKey(request.getDjiOrgKey());

        // 填入处理后的配置
        project.setPhotoFolderKeyword(folderKeyword);
        project.setObsBucketName(targetBucket);
        project.setObsAk(targetAk);
        project.setObsSk(targetSk);
        project.setObsEndpoint(targetEndpoint);

        project.setCreatedBy(userId);
        project.setCreatedAt(LocalDateTime.now());

        sysProjectMapper.insert(project);

        // 4. 关联表插入
        SysUserProject relation = new SysUserProject();
        relation.setUserId(userId);
        relation.setProjectId(project.getId());
        sysUserProjectMapper.insert(relation);
    }

    // ... 其他方法保持不变 ...

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

    // ⚠️ 注意：此处移除了 @Override，因为接口 ProjectService 中没有定义此方法
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