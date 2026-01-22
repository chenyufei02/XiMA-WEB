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
import java.util.Set;
import java.util.stream.Collectors;

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
    private ProjectPhotoMapper projectPhotoMapper;

    /**
     * ✅ 步骤1：输入Key，查询大疆项目列表
     * 升级：自动标记哪些项目是【当前用户已导入】的
     */
    @GetMapping("/dji-workspaces")
    public ApiResponse<List<DjiProjectDto>> getDjiWorkspaces(@RequestParam String apiKey, HttpServletRequest httpRequest) {
        // 1. 获取当前用户ID
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");
        if (userId == null) userId = 1;

        // 2. 从大疆 API 获取所有原始项目
        List<DjiProjectDto> djiProjects = djiService.getProjects(apiKey);

        // 3. 查库：找出当前用户已经导入了哪些 UUID
        QueryWrapper<SysProject> query = new QueryWrapper<>();
        query.select("dji_project_uuid"); // 只查 UUID 字段，省流
        query.eq("created_by", userId);   // 关键：只查当前用户的！

        List<SysProject> myExistingProjects = sysProjectMapper.selectList(query);

        // 转成 Set 方便快速比对
        Set<String> importedUuids = myExistingProjects.stream()
                .map(SysProject::getDjiProjectUuid)
                .collect(Collectors.toSet());

        // 4. 遍历并打标
        for (DjiProjectDto dto : djiProjects) {
            // 如果这个 UUID 在库里有，标记为 true
            if (importedUuids.contains(dto.getUuid())) {
                dto.setImported(true);
            }
        }

        return ApiResponse.success("获取成功", djiProjects);
    }

    /**
     * 步骤2：保存导入的项目
     */
    @PostMapping("/import")
    public ApiResponse<Object> importProject(@RequestBody ProjectImportRequest request, HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");
        if (userId == null) userId = 1;

        try {
            projectService.importProject(request, userId);
            return ApiResponse.success("导入成功");
        } catch (RuntimeException re) {
            // 捕获我们自己抛出的重复导入异常
            return ApiResponse.error(re.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("系统异常: " + e.getMessage());
        }
    }

    // ... 其他接口保持不变 (getMyProjects, updateBoundary, getProjectPhotos, deleteProject, updateProject) ...

    @GetMapping("/my")
    public ApiResponse<List<SysProject>> getMyProjects(HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");
        List<SysProject> projects = projectService.getUserProjects(userId);
        return ApiResponse.success("获取成功", projects);
    }

    @PostMapping("/{projectId}/boundary")
    public ApiResponse<Object> updateBoundary(@PathVariable Integer projectId, @RequestBody List<Coordinate> coords) {
        ((ProjectServiceImpl) projectService).updateBoundary(projectId, coords);
        return ApiResponse.success("围栏设置成功");
    }

    @GetMapping("/{id}/photos")
    public ApiResponse<List<ProjectPhoto>> getProjectPhotos(@PathVariable Integer id) {
        QueryWrapper<ProjectPhoto> query = new QueryWrapper<>();
        query.eq("project_id", id);
        query.isNotNull("gps_lat");
        query.orderByDesc("shoot_time");
        List<ProjectPhoto> photos = projectPhotoMapper.selectList(query);
        return ApiResponse.success("获取成功", photos);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> deleteProject(@PathVariable Integer id) {
        try {
            projectService.deleteProject(id);
            return ApiResponse.success("项目删除成功");
        } catch (Exception e) {
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<Object> updateProject(@PathVariable Integer id, @RequestBody SysProject project) {
        project.setId(id);
        boolean result = projectService.updateProjectInfo(project);
        return result ? ApiResponse.success("更新成功") : ApiResponse.error("更新失败");
    }
}