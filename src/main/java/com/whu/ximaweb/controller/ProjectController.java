package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.Coordinate;
import com.whu.ximaweb.dto.ProjectImportRequest;
import com.whu.ximaweb.dto.dji.DjiMediaFileDto;
import com.whu.ximaweb.dto.dji.DjiProjectDto;
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.model.PhotoData;
import com.whu.ximaweb.model.ProjectPhoto;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.service.DjiService;
import com.whu.ximaweb.service.ObsService;
import com.whu.ximaweb.service.PhotoProcessor;
import com.whu.ximaweb.service.ProgressService;
import com.whu.ximaweb.service.ProjectService;
import com.whu.ximaweb.service.impl.ProjectServiceImpl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private ProgressService progressService;

    @Autowired
    private SysProjectMapper sysProjectMapper;

    @Autowired
    private ProjectPhotoMapper projectPhotoMapper;

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private ObsService obsService;

    @Autowired
    private PhotoProcessor photoProcessor;

    /**
     * 获取大疆工作空间项目列表
     */
    @GetMapping("/dji-workspaces")
    public ApiResponse<List<DjiProjectDto>> getDjiWorkspaces(@RequestParam String apiKey, HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");
        if (userId == null) userId = 1;

        List<DjiProjectDto> djiProjects = djiService.getProjects(apiKey);

        QueryWrapper<SysProject> query = new QueryWrapper<>();
        query.select("dji_project_uuid");
        query.eq("created_by", userId);

        List<SysProject> myExistingProjects = sysProjectMapper.selectList(query);
        Set<String> importedUuids = myExistingProjects.stream()
                .map(SysProject::getDjiProjectUuid)
                .collect(Collectors.toSet());

        for (DjiProjectDto dto : djiProjects) {
            if (importedUuids.contains(dto.getUuid())) {
                dto.setImported(true);
            }
        }
        return ApiResponse.success("获取成功", djiProjects);
    }

    /**
     * 导入项目
     */
    @PostMapping("/import")
    public ApiResponse<Object> importProject(@RequestBody ProjectImportRequest request, HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");
        if (userId == null) userId = 1;

        try {
            projectService.importProject(request, userId);
            return ApiResponse.success("导入成功");
        } catch (RuntimeException re) {
            return ApiResponse.error(re.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("系统异常: " + e.getMessage());
        }
    }

    /**
     * 获取我的项目列表
     */
    @GetMapping("/my")
    public ApiResponse<List<SysProject>> getMyProjects(HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");
        List<SysProject> projects = projectService.getUserProjects(userId);
        return ApiResponse.success("获取成功", projects);
    }

    /**
     * 更新项目围栏并触发进度计算
     */
    @PostMapping("/{projectId}/boundary")
    public ApiResponse<Object> updateBoundary(@PathVariable Integer projectId, @RequestBody List<Coordinate> coords) {
        ((ProjectServiceImpl) projectService).updateBoundary(projectId, coords);
        try {
            System.out.println(">>> 围栏更新成功，正在触发项目 [" + projectId + "] 的进度重算...");
            progressService.calculateProjectProgress(projectId);
            System.out.println(">>> 进度重算完成");
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.success("围栏设置成功，但进度计算遇到异常: " + e.getMessage());
        }
        return ApiResponse.success("围栏设置成功，且进度已更新");
    }

    /**
     * 获取项目照片列表
     */
    @GetMapping("/{id}/photos")
    public ApiResponse<List<ProjectPhoto>> getProjectPhotos(@PathVariable Integer id) {
        QueryWrapper<ProjectPhoto> query = new QueryWrapper<>();
        query.eq("project_id", id);
        query.isNotNull("gps_lat");
        query.orderByDesc("shoot_time");
        List<ProjectPhoto> photos = projectPhotoMapper.selectList(query);
        return ApiResponse.success("获取成功", photos);
    }

    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Object> deleteProject(@PathVariable Integer id) {
        try {
            projectService.deleteProject(id);
            return ApiResponse.success("项目删除成功");
        } catch (Exception e) {
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 更新项目信息
     */
    @PutMapping("/{id}")
    public ApiResponse<Object> updateProject(@PathVariable Integer id, @RequestBody SysProject project) {
        project.setId(id);
        boolean result = projectService.updateProjectInfo(project);
        return result ? ApiResponse.success("更新成功") : ApiResponse.error("更新失败");
    }

    /**
     * ✅ 手动触发同步接口
     */
    @PostMapping("/{projectId}/sync")
    public ApiResponse<String> manualSyncPhotos(@PathVariable Integer projectId, @RequestBody Map<String, String> body) {
        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) return ApiResponse.error("项目不存在");

        String tempKeyword = body.get("tempKeyword");
        String targetKeyword = (tempKeyword != null && !tempKeyword.trim().isEmpty())
                                ? tempKeyword.trim()
                                : project.getPhotoFolderKeyword();

        try {
            List<DjiMediaFileDto> djiFiles = djiService.getPhotosFromFolder(
                project.getDjiProjectUuid(),
                project.getDjiOrgKey(),
                targetKeyword
            );

            if (djiFiles.isEmpty()) {
                return ApiResponse.success("同步完成，未找到包含关键词 [" + targetKeyword + "] 的新照片。");
            }

            int successCount = 0;
            for (DjiMediaFileDto djiFile : djiFiles) {
                String fileName = djiFile.getFileName();
                if ("Remote-Control".equals(fileName) || fileName.endsWith(".MRK") || fileName.endsWith(".NAV")
                        || fileName.endsWith(".OBS") || fileName.endsWith(".RTK") || fileName.endsWith("_D")) {
                    continue;
                }
                if (!fileName.toLowerCase().endsWith(".jpg") && !fileName.toLowerCase().endsWith(".jpeg")) {
                    fileName = fileName + ".jpeg";
                }

                String relativePath = djiFile.getFilePath();
                if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
                String objectKey = "projects/" + project.getId() + "/" + relativePath + "/" + fileName;

                if (projectPhotoMapper.selectCount(new QueryWrapper<ProjectPhoto>().eq("photo_url", objectKey)) > 0) continue;

                try {
                    Request request = new Request.Builder().url(djiFile.getDownloadUrl()).get().build();
                    try (Response response = okHttpClient.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            byte[] fileBytes = response.body().bytes();

                            if (!obsService.doesObjectExist(project.getObsAk(), project.getObsSk(), project.getObsEndpoint(), project.getObsBucketName(), objectKey)) {
                                obsService.uploadStream(project.getObsAk(), project.getObsSk(), project.getObsEndpoint(), project.getObsBucketName(), objectKey, new ByteArrayInputStream(fileBytes));
                            }

                            try (InputStream xmpStream = new ByteArrayInputStream(fileBytes)) {
                                Optional<PhotoData> photoDataOpt = photoProcessor.process(xmpStream, fileName);
                                if (photoDataOpt.isPresent()) {
                                    PhotoData data = photoDataOpt.get();
                                    ProjectPhoto photo = new ProjectPhoto();
                                    photo.setProjectId(project.getId());
                                    photo.setPhotoUrl(objectKey);
                                    photo.setShootTime(data.getCaptureTime());

                                    // 存飞机坐标
                                    photo.setGpsLat(BigDecimal.valueOf(data.getLatitude()));
                                    photo.setGpsLng(BigDecimal.valueOf(data.getLongitude()));

                                    // ✅ 存目标点坐标 (新增)
                                    if (data.getLrfTargetLat() != -1 && data.getLrfTargetLng() != -1) {
                                        photo.setLrfTargetLat(BigDecimal.valueOf(data.getLrfTargetLat()));
                                        photo.setLrfTargetLng(BigDecimal.valueOf(data.getLrfTargetLng()));
                                    }

                                    photo.setLaserDistance(BigDecimal.valueOf(data.getDistance()));
                                    photo.setAbsoluteAltitude(BigDecimal.valueOf(data.getDroneAbsoluteAltitude()));
                                    photo.setIsMarker(false);

                                    projectPhotoMapper.insert(photo);
                                    successCount++;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("手动同步文件失败: " + fileName + ", " + e.getMessage());
                }
            }

            if (successCount > 0) {
                progressService.calculateProjectProgress(projectId);
                return ApiResponse.success("同步成功，新增 " + successCount + " 张照片，进度已自动更新。");
            } else {
                return ApiResponse.success("同步完成，找到 " + djiFiles.size() + " 张照片，但都是已存在的，无新增。");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("同步过程中发生错误: " + e.getMessage());
        }
    }
}