package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.model.ProjectPhoto;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.service.ObsService;
import com.whu.ximaweb.service.PhotoProcessor;
import com.whu.ximaweb.model.PhotoData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 【抢救专用】本地文件导入控制器 (幂等版：支持断点续传)
 * 用于将本地硬盘的历史照片补录到系统中，支持H1、H2推算所需的全量元数据
 * 修改版：增加 LRF 目标点坐标的入库支持
 */
@RestController
@RequestMapping("/api/rescue")
public class RescueController {

    @Autowired
    private SysProjectMapper sysProjectMapper;

    @Autowired
    private ProjectPhotoMapper projectPhotoMapper;

    @Autowired
    private ObsService obsService;

    @Autowired
    private PhotoProcessor photoProcessor;

    /**
     * 导入本地文件夹下的所有照片
     * @param projectId 项目ID
     * @param localPath 本地文件夹绝对路径 (例如: D:/DJI_Photos/2023_Manual_Flight)
     * @param dryRun 是否预演 (true:不写真正入库, false:真实执行)
     */
    @PostMapping("/import-local")
    public ApiResponse<String> importLocalFiles(
            @RequestParam Integer projectId,
            @RequestParam String localPath,
            @RequestParam(defaultValue = "true") Boolean dryRun
    ) {
        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) return ApiResponse.error("项目不存在");

        File rootDir = new File(localPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            return ApiResponse.error("路径无效: " + localPath);
        }

        System.out.println("==============================================");
        System.out.println(dryRun ? "🚦 [预演] 开始扫描 (只读模式)..." : "🚀 [实战] 开始执行 (断点续传模式)...");
        System.out.println("==============================================");

        int count = processDirectory(rootDir, rootDir.getName(), project, dryRun);

        return ApiResponse.success((dryRun ? "[预演] " : "[实战] ") + "处理完成。新增/扫描照片: " + count + " 张。");
    }

    private int processDirectory(File currentDir, String relativePath, SysProject project, boolean dryRun) {
        int count = 0;
        File[] files = currentDir.listFiles();
        if (files == null) return 0;

        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            relativePath = relativePath.substring(1);
        }

        for (File file : files) {
            if (file.isDirectory()) {
                count += processDirectory(file, relativePath + "/" + file.getName(), project, dryRun);
            } else {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png")) {
                    String objectKey = "projects/" + project.getId() + "/" + relativePath + "/" + file.getName();

                    if (dryRun) {
                        // --- 预演模式 ---
                        System.out.println("   [扫描] " + file.getName());
                        count++;
                    } else {
                        // --- 实战模式 (带双重去重) ---
                        try {
                            // 1. 检查数据库去重 (最快，优先检查)
                            QueryWrapper<ProjectPhoto> query = new QueryWrapper<>();
                            query.eq("photo_url", objectKey);
                            if (projectPhotoMapper.selectCount(query) > 0) {
                                // 数据库里有了，直接跳过
                                continue;
                            }

                            System.out.println("   🚀 [处理新文件] " + file.getName());

                            // 2. 检查OBS去重 + 上传
                            if (!obsService.doesObjectExist(project.getObsAk(), project.getObsSk(), project.getObsEndpoint(), project.getObsBucketName(), objectKey)) {
                                try (FileInputStream fis = new FileInputStream(file)) {
                                    obsService.uploadStream(project.getObsAk(), project.getObsSk(), project.getObsEndpoint(), project.getObsBucketName(), objectKey, fis);
                                    System.out.println("      -> OBS上传成功");
                                }
                            } else {
                                System.out.println("      -> OBS已存在 (跳过上传)");
                            }

                            // 3. 解析XMP并入库
                            try (FileInputStream fis = new FileInputStream(file)) {
                                Optional<PhotoData> photoDataOpt = photoProcessor.process(fis, file.getName());
                                ProjectPhoto photo = new ProjectPhoto();
                                photo.setProjectId(project.getId());
                                photo.setPhotoUrl(objectKey);

                                if (photoDataOpt.isPresent()) {
                                    PhotoData data = photoDataOpt.get();
                                    photo.setShootTime(data.getCaptureTime());

                                    // 存飞机坐标
                                    photo.setGpsLat(BigDecimal.valueOf(data.getLatitude()));
                                    photo.setGpsLng(BigDecimal.valueOf(data.getLongitude()));

                                    // ✅ 新增：存激光测距目标点坐标 (用于判断 H1/H2)
                                    // PhotoProcessor 如果解析失败会返回 -1，这里做个判断
                                    if (data.getLrfTargetLat() != -1 && data.getLrfTargetLng() != -1) {
                                        photo.setLrfTargetLat(BigDecimal.valueOf(data.getLrfTargetLat()));
                                        photo.setLrfTargetLng(BigDecimal.valueOf(data.getLrfTargetLng()));
                                        // System.out.println("      -> 捕获目标点坐标: " + data.getLrfTargetLat() + ", " + data.getLrfTargetLng());
                                    }

                                    photo.setLaserDistance(BigDecimal.valueOf(data.getDistance()));

                                    // 保存绝对高度 (用于 H2 智能推算)
                                    photo.setAbsoluteAltitude(BigDecimal.valueOf(data.getDroneAbsoluteAltitude()));

                                    // 默认为普通照片，参与计算
                                    photo.setIsMarker(false);

                                    System.out.println("      -> XMP解析成功 (" + data.getCaptureTime() + ")");
                                } else {
                                    photo.setShootTime(LocalDateTime.now());
                                    photo.setIsMarker(false);
                                    System.err.println("      -> ⚠️ 无XMP数据");
                                }
                                projectPhotoMapper.insert(photo);
                                System.out.println("      -> 数据库入库成功");
                                count++;
                            }

                        } catch (Exception e) {
                            System.err.println("   ❌ 处理失败 [" + file.getName() + "]: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return count;
    }
}