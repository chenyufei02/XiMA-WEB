package com.whu.ximaweb.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.dji.DjiMediaFileDto;
import com.whu.ximaweb.model.ProjectPhoto;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.service.DjiService;
import com.whu.ximaweb.service.ObsService;
import com.whu.ximaweb.service.PhotoProcessor;
import com.whu.ximaweb.model.PhotoData;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 定时任务：自动同步大疆照片
 * 逻辑：每小时执行一次 -> 扫描大疆任务 -> 过滤关键词 -> 保持目录结构上传华为云 -> 解析XMP入库
 */
@Component
@EnableScheduling
public class PhotoSyncTask {

    @Autowired
    private SysProjectMapper sysProjectMapper;

    @Autowired
    private ProjectPhotoMapper projectPhotoMapper;

    @Autowired
    private DjiService djiService;

    @Autowired
    private ObsService obsService;

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private PhotoProcessor photoProcessor;

    // 每1小时执行一次 (3600000毫秒)
    // initialDelay = 10000: 项目启动10秒后先跑一次，方便观察
    @Scheduled(fixedRate = 3600000, initialDelay = 10000)
    public void syncPhotosTask() {
        System.out.println("\n=================================================");
        System.out.println("⏰ [定时任务] 开始执行照片自动同步...");
        System.out.println("=================================================");

        // 1. 获取所有项目
        List<SysProject> projects = sysProjectMapper.selectList(null);

        if (projects.isEmpty()) {
            System.out.println("⚠️ 数据库中没有项目，无需同步。");
            return;
        }

        for (SysProject project : projects) {
            try {
                System.out.println(">>> 正在扫描项目: " + project.getProjectName());

                // 2. 调用大疆API获取符合关键词的文件列表
                // 注意：DjiService 里已经封装好了“先查任务 -> 再查媒体 -> 拼凑路径”的复杂逻辑
                List<DjiMediaFileDto> djiFiles = djiService.getPhotosFromFolder(
                    project.getDjiProjectUuid(),
                    project.getDjiOrgKey(),
                    project.getPhotoFolderKeyword()
                );

                if (djiFiles.isEmpty()) {
                    System.out.println("    ⚪ 未发现新照片。");
                    continue;
                }

                System.out.println("    🔥 发现 " + djiFiles.size() + " 张潜在照片，开始处理...");

                int successCount = 0;
                for (DjiMediaFileDto djiFile : djiFiles) {
                    String fileName = djiFile.getFileName();

                    // 🛑 1. 黑名单过滤 (彻底根治红字)
                    if ("Remote-Control".equals(fileName)
                            || fileName.endsWith(".MRK") || fileName.endsWith(".NAV")
                            || fileName.endsWith(".OBS") || fileName.endsWith(".RTK")
                            || fileName.endsWith("_D")) {
                        System.out.println("       ⚪ [静默跳过] 原始数据/文件夹: " + fileName);
                        continue;
                    }

                    // ✅ 2. 核心补丁：如果文件名没有后缀，强制加上 .JPG
                    // 这样就能和本地抢救上传的 "DJI_xxx.JPG" 完美重合，触发 OBS 跳过机制
                    if (!fileName.toLowerCase().endsWith(".jpg") && !fileName.toLowerCase().endsWith(".jpeg")) {
                        fileName = fileName + ".jpeg";
                    }

                    // 3. 构造路径
                    String relativePath = djiFile.getFilePath();
                    if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
                    String objectKey = "projects/" + project.getId() + "/" + relativePath + "/" + fileName;

                    // 4. 查库去重
                    QueryWrapper<ProjectPhoto> query = new QueryWrapper<>();
                    query.eq("photo_url", objectKey);
                    if (projectPhotoMapper.selectCount(query) > 0) continue;

                    System.out.println("    🚀 [新照片] 正在同步: " + fileName);

                    // 5. 下载与处理 (全包裹 try-catch)
                    try {
                        if (djiFile.getDownloadUrl() == null || djiFile.getDownloadUrl().isEmpty()) {
                            System.out.println("       ⚠️ 跳过: 无下载地址");
                            continue;
                        }

                        Request request = new Request.Builder().url(djiFile.getDownloadUrl()).get().build();
                        try (Response response = okHttpClient.newCall(request).execute()) {
                            if (!response.isSuccessful() || response.body() == null) throw new RuntimeException("HTTP " + response.code());
                            byte[] fileBytes = response.body().bytes();

                            // 6. 上传华为云 (检测是否存在，避免重复上传)
                            if (!obsService.doesObjectExist(project.getObsAk(), project.getObsSk(), project.getObsEndpoint(), project.getObsBucketName(), objectKey)) {
                                obsService.uploadStream(project.getObsAk(), project.getObsSk(), project.getObsEndpoint(), project.getObsBucketName(), objectKey, new ByteArrayInputStream(fileBytes));
                                System.out.println("       -> 上传华为云成功");
                            } else {
                                // 🌟 看到这行日志，就说明对齐成功
                                System.out.println("       -> OBS已存在 (跳过上传)");
                            }

                            // 7. 入库
                            try (InputStream xmpStream = new ByteArrayInputStream(fileBytes)) {
                                Optional<PhotoData> photoDataOpt = photoProcessor.process(xmpStream, fileName);
                                ProjectPhoto photo = new ProjectPhoto();
                                photo.setProjectId(project.getId());
                                photo.setPhotoUrl(objectKey);

                                if (photoDataOpt.isPresent()) {
                                    PhotoData data = photoDataOpt.get();
                                    photo.setShootTime(data.getCaptureTime());
                                    photo.setGpsLat(java.math.BigDecimal.valueOf(data.getLatitude()));
                                    photo.setGpsLng(java.math.BigDecimal.valueOf(data.getLongitude()));
                                    photo.setLaserDistance(java.math.BigDecimal.valueOf(data.getDistance()));

                                    projectPhotoMapper.insert(photo);
                                    successCount++;
                                    System.out.println("       ✅ 入库成功");
                                } else {
                                    System.out.println("       ⚠️ 跳过: 无XMP数据");
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("       ⚪ [跳过] " + fileName + ": " + e.getMessage());
                    }
                }
                System.out.println("    ✅ 项目同步完成，新增入库: " + successCount + " 张");

            } catch (Exception e) {
                System.err.println("❌ 项目处理异常: " + e.getMessage());
            }
        }
        System.out.println("=================================================\n");
    }
}