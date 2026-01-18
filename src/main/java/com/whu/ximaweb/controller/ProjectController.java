package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.Coordinate;
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
            @RequestBody List<Coordinate> coords) {

        ((ProjectServiceImpl) projectService).updateBoundary(projectId, coords);
        return ApiResponse.success("围栏设置成功");
    }

    /**
     * ✅ 步骤4：获取项目下的所有照片（用于前端画布显示）
     * 请求路径：GET /api/projects/{id}/photos
     * 逻辑：使用 QueryWrapper 查询带GPS坐标的照片，并按时间倒序排列
     */
    @GetMapping("/{id}/photos")
    public ApiResponse<List<ProjectPhoto>> getProjectPhotos(@PathVariable Integer id) {
        // 使用 QueryWrapper 构造查询条件
        QueryWrapper<ProjectPhoto> query = new QueryWrapper<>();
        query.eq("project_id", id);
        query.isNotNull("gps_lat"); // 只查有坐标的照片
        query.orderByDesc("shoot_time"); // 按时间倒序，方便看到最新的

        // selectList 是 MyBatis-Plus 内置方法，绝对安全
        List<ProjectPhoto> photos = projectPhotoMapper.selectList(query);

        return ApiResponse.success("获取成功", photos);
    }
}