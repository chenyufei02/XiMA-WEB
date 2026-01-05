package com.whu.ximaweb.controller;

import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.ProjectImportRequest;
import com.whu.ximaweb.dto.dji.DjiProjectDto;
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.model.ProjectPhoto;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.service.DjiService;
import com.whu.ximaweb.service.ProjectService;
import com.whu.ximaweb.service.impl.ProjectServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private DjiService djiService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private SysProjectMapper sysProjectMapper;

    @Autowired
    private ProjectPhotoMapper projectPhotoMapper; // ✅ 新增注入：用于查库

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
    public ApiResponse<Object> importProject(@RequestBody ProjectImportRequest request, HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");
        if (userId == null) {
            userId = 1; // 默认管理员
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
     */
    @GetMapping("/my")
    public ApiResponse<List<SysProject>> getMyProjects(HttpServletRequest httpRequest) {
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

        ((ProjectServiceImpl) projectService).updateBoundary(projectId, coords);
        return ApiResponse.success("围栏设置成功");
    }

    /**
     * 步骤4-1：获取指定项目的照片列表 (✅ 改造版：直接查库获取带GPS的真实数据)
     * 注意：返回值从 List<DjiMediaFileDto> 改为了 List<ProjectPhoto>
     */
    @GetMapping("/{projectId}/photos")
    public ApiResponse<List<ProjectPhoto>> getProjectPhotos(@PathVariable Integer projectId) {
        // 1. 直接查数据库
        // 我们已经在 PhotoSyncTask 或 RescueController 中把照片的 GPS 解析并入库了
        List<ProjectPhoto> photos = projectPhotoMapper.selectByProjectId(projectId);

        if (photos.isEmpty()) {
            return ApiResponse.error(404, "暂无照片数据。请先在控制台执行[照片同步]或[本地导入]。");
        }

        return ApiResponse.success("获取成功", photos);
    }
}