package com.whu.ximaweb.controller;

import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.ProjectImportRequest;
import com.whu.ximaweb.dto.dji.DjiProjectDto;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.service.DjiService;
import com.whu.ximaweb.service.ProjectService;
import javax.servlet.http.HttpServletRequest;
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


    /**
     * 步骤1：输入Key，查询大疆项目列表
     * (此接口不依赖本地用户ID，只需API Key即可查询大疆端数据)
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
    public ApiResponse<Object> importProject(@RequestBody ProjectImportRequest request, HttpServletRequest httpRequest) {
        // 尝试获取 userId
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");

        // ✅ 兼容逻辑：如果是通过 import.html 导入的（未登录），默认给 ID=1 的管理员
        if (userId == null) {
            userId = 1;
        }

        try {
            projectService.importProject(request, userId);
            return ApiResponse.success("导入成功");
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("导入失败: " + e.getMessage());
        }
    }


    /**
     * 首页：查看我的项目列表
     * ✅ 修改：增加 HttpServletRequest 参数，只返回当前登录用户的项目
     */
    @GetMapping("/my")
    public ApiResponse<List<SysProject>> getMyProjects(HttpServletRequest httpRequest) {
        // 从 Request 中获取 userId
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");

        List<SysProject> projects = projectService.getUserProjects(userId);
        return ApiResponse.success("获取成功", projects);
    }

    /**
     * 步骤3：保存电子围栏
     */
    @PostMapping("/{projectId}/boundary")
    public ApiResponse<Object> updateBoundary(
            @PathVariable Integer projectId,
            @RequestBody java.util.List<com.whu.ximaweb.dto.Coordinate> coords) {

        // 暂时强转调用，后续建议优化 Service 接口定义
        ((com.whu.ximaweb.service.impl.ProjectServiceImpl) projectService).updateBoundary(projectId, coords);
        return ApiResponse.success("围栏设置成功");
    }

    /**
     * 步骤4-1：获取指定项目的照片列表
     */
    @GetMapping("/{projectId}/photos")
    public ApiResponse<List<com.whu.ximaweb.dto.dji.DjiMediaFileDto>> getProjectPhotos(@PathVariable Integer projectId) {
        // 1. 查库获取项目配置
        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) {
            return ApiResponse.error("项目不存在");
        }

        // 2. 获取 API Key (优先使用项目存的，如果没有则需处理，这里暂略)
        String apiKey = project.getDjiOrgKey();

        // 3. 调用大疆接口
        try {
            List<com.whu.ximaweb.dto.dji.DjiMediaFileDto> photos = djiService.getPhotosFromFolder(
                project.getDjiProjectUuid(),
                apiKey,
                project.getPhotoFolderKeyword()
            );
            return ApiResponse.success("获取照片成功", photos);
        } catch (Exception e) {
            return ApiResponse.error("获取照片失败：" + e.getMessage());
        }
    }
}