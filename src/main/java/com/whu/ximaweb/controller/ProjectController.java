package com.whu.ximaweb.controller;

import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.ProjectImportRequest;
import com.whu.ximaweb.dto.dji.DjiProjectDto;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.service.DjiService;
import com.whu.ximaweb.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private DjiService djiService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private com.whu.ximaweb.mapper.SysProjectMapper sysProjectMapper; // 临时注入Mapper方便查库

    // TODO: 暂时硬编码为1，后续接登录后修改
    private static final Integer CURRENT_USER_ID = 1;

    /**
     * 步骤1：输入Key，查询大疆项目列表
     */
    @GetMapping("/dji-workspaces")
    public ApiResponse<List<DjiProjectDto>> getDjiWorkspaces(@RequestParam String apiKey) {
        List<DjiProjectDto> projects = djiService.getProjects(apiKey);
        return ApiResponse.success("获取成功", projects);
    }

    /**
     * 步骤2：保存导入的项目
     */
    @PostMapping("/import")
    public ApiResponse<Object> importProject(@RequestBody ProjectImportRequest request) {
        try {
            projectService.importProject(request, CURRENT_USER_ID);
            return ApiResponse.success("导入成功");
        } catch (Exception e) {
            return ApiResponse.success("导入失败: " + e.getMessage());
        }
    }

    /**
     * 首页：查看我的项目列表
     */
    @GetMapping("/my")
    public ApiResponse<List<SysProject>> getMyProjects() {
        List<SysProject> projects = projectService.getUserProjects(CURRENT_USER_ID);
        return ApiResponse.success("获取成功", projects);
    }

    /**
     * 步骤3：保存电子围栏（多边形打点）
     */
    @PostMapping("/{projectId}/boundary")
    public ApiResponse<Object> updateBoundary(
            @PathVariable Integer projectId,
            @RequestBody java.util.List<com.whu.ximaweb.dto.Coordinate> coords) {

        // 暂时强转一下 Service 类型来调用新方法 (或者您在接口里定义后直接用接口调用)
        // 建议您先在 ProjectService 接口里加上 updateBoundary 定义
        // 这里演示直接调用实现类逻辑：
        ((com.whu.ximaweb.service.impl.ProjectServiceImpl) projectService).updateBoundary(projectId, coords);

        return ApiResponse.success("围栏设置成功");
    }

    /**
     * 步骤4-1：获取指定项目的照片列表（用于选择围栏拐点）
     * 前端传入 databaseProjectId (我们数据库里的ID)，后端自动查出 DJI 的 UUID 和 Key 去请求
     */
    @GetMapping("/{projectId}/photos")
    public ApiResponse<List<com.whu.ximaweb.dto.dji.DjiMediaFileDto>> getProjectPhotos(@PathVariable Integer projectId) {
        // 1. 先查数据库，获取这个项目的大疆配置信息
        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) {
            return ApiResponse.error("项目不存在");
        }

        // 2. 检查是否有组织密钥 (如果是云端互联，通常用配置文件的默认Key，或者项目自己存的Key)
        // 这里假设我们用项目里存的，如果没有就用配置文件的（逻辑在Service里处理了，这里传入 project 里的 key）
        // 注意：如果你之前设计的是用默认Key，这里传 null，Service 会处理
        String apiKey = project.getDjiOrgKey();
        if (apiKey == null || apiKey.isEmpty()) {
            // 如果项目没存Key，可能需要一种机制获取默认Key，这里暂且假设项目里有存，或者前端传
            // 为了简化，这里暂时传 project.getDjiOrgKey()
        }

        // 3. 调用大疆接口获取照片
        // 注意：这里需要去 DjiService 确认 getPhotosFromFolder 方法的签名
        // 假设您之前按照 Step 3-1 定义了 getPhotosFromFolder(projectUuid, apiKey, keyword)

        // *修正*：为了防止报错，我们确保 DjiService 里有这个方法。
        // 如果您之前的 DjiService 没有这个方法，请暂时注释掉下面这行，或者去 DjiServiceImpl 补上。
        List<com.whu.ximaweb.dto.dji.DjiMediaFileDto> photos = djiService.getPhotosFromFolder(
            project.getDjiProjectUuid(),
            apiKey, // 或者是 organizationKey
            project.getPhotoFolderKeyword() // "请勿删" 那个关键词
        );

        return ApiResponse.success("获取照片成功", photos);
    }
}