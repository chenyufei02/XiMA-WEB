package com.whu.ximaweb.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.dji.DjiMediaFileDto;
import com.whu.ximaweb.model.ProjectPhoto;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.model.SysTaskLog; // ✅ 新增
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.mapper.SysTaskLogMapper; // ✅ 新增
import com.whu.ximaweb.service.DjiService;
import com.whu.ximaweb.service.ObsService;
import com.whu.ximaweb.service.PhotoProcessor;
import com.whu.ximaweb.service.ProgressService;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 定时任务：自动同步大疆照片 + 智能触发进度计算
 * 完整逻辑：每小时执行 -> 扫描大疆任务 -> 过滤 -> 上传OBS -> 解析XMP(含目标点坐标) -> 入库 -> 触发进度计算
 */
@Component
@EnableScheduling
public class PhotoSyncTask {

    @Autowired
    private SysProjectMapper sysProjectMapper;

    @Autowired
    private ProjectPhotoMapper projectPhotoMapper;

    @Autowired
    private SysTaskLogMapper sysTaskLogMapper; // ✅ 新增：用于记录监控日志

    @Autowired
    private DjiService djiService;

    @Autowired
    private ObsService obsService;

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private PhotoProcessor photoProcessor;

    @Autowired
    private ProgressService progressService;

    // 每1小时执行一次 (3600000毫秒)，启动10秒后初次执行
    @Scheduled(fixedRate = 3600000, initialDelay = 10000)
    public void syncPhotosTask() {
        System.out.println("\n=================================================");
        System.out.println("⏰ [定时任务] 开始执行照片自动同步...");
        System.out.println("=================================================");

        // 1. 获取所有项目
        List<SysProject> projects = sysProjectMapper.selectList(null);

        if (projects == null || projects.isEmpty()) {
            System.out.println("⚠️ 数据库中没有项目，无需同步。");
            return;
        }

        for (SysProject project : projects) {
            try {
                System.out.println(">>> 正在扫描项目: " + project.getProjectName());

                // 2. 调用大疆API获取符合关键词的文件列表
                // 如果关键词为空，使用默认空字符串搜索
                String keyword = project.getPhotoFolderKeyword();
                if (keyword == null) keyword = "";

                List<DjiMediaFileDto> djiFiles = djiService.getPhotosFromFolder(
                    project.getDjiProjectUuid(),
                    project.getDjiOrgKey(),
                    keyword
                );

                if (djiFiles.isEmpty()) {
                    // 即使没有新照片，也记录一次"连接成功"的心跳日志，让面板显示"最近同步：刚刚"
                    SysTaskLog log = new SysTaskLog();
                    log.setProjectId(project.getId());
                    log.setTaskType(SysTaskLog.TYPE_PHOTO_SYNC);
                    log.setStatus(1);
                    log.setMessage("连接正常，当前无新照片");
                    sysTaskLogMapper.insert(log);

                    System.out.println("    ⚪ 未发现新照片，跳过后续处理。");
                    continue;
                }

                System.out.println("    🔥 发现 " + djiFiles.size() + " 张潜在照片，开始处理...");

                int successCount = 0; // 记录本轮新增的照片数量

                for (DjiMediaFileDto djiFile : djiFiles) {
                    String fileName = djiFile.getFileName();

                    // 🛑 1. 文件名黑名单过滤
                    if ("Remote-Control".equals(fileName)
                            || fileName.endsWith(".MRK") || fileName.endsWith(".NAV")
                            || fileName.endsWith(".OBS") || fileName.endsWith(".RTK")
                            || fileName.endsWith("_D")) {
                        // 这些是无关的定位辅助文件，静默跳过
                        continue;
                    }

                    // ✅ 2. 强制后缀名补全 (防止部分文件没有后缀)
                    if (!fileName.toLowerCase().endsWith(".jpg") && !fileName.toLowerCase().endsWith(".jpeg")) {
                        fileName = fileName + ".jpeg";
                    }

                    // 3. 构造云存储路径
                    String relativePath = djiFile.getFilePath();
                    if (relativePath.startsWith("/")) {
                        relativePath = relativePath.substring(1);
                    }
                    String objectKey = "projects/" + project.getId() + "/" + relativePath + "/" + fileName;

                    // 4. 查库去重 (如果数据库已有该路径，直接跳过)
                    QueryWrapper<ProjectPhoto> query = new QueryWrapper<>();
                    query.eq("photo_url", objectKey);
                    if (projectPhotoMapper.selectCount(query) > 0) {
                        continue;
                    }

                    System.out.println("    🚀 [新照片] 正在同步: " + fileName);

                    // 5. 下载与处理
                    try {
                        if (djiFile.getDownloadUrl() == null || djiFile.getDownloadUrl().isEmpty()) {
                            System.out.println("       ⚠️ 跳过: 无下载地址");
                            continue;
                        }

                        Request request = new Request.Builder().url(djiFile.getDownloadUrl()).get().build();
                        try (Response response = okHttpClient.newCall(request).execute()) {
                            if (!response.isSuccessful() || response.body() == null) {
                                System.out.println("       ❌ 下载失败: HTTP " + response.code());
                                continue;
                            }

                            byte[] fileBytes = response.body().bytes();

                            // 6. 上传华为云 OBS
                            // 先检查是否存在，不存在再上传
                            boolean existsInObs = obsService.doesObjectExist(
                                    project.getObsAk(), project.getObsSk(),
                                    project.getObsEndpoint(), project.getObsBucketName(), objectKey
                            );

                            if (!existsInObs) {
                                obsService.uploadStream(
                                        project.getObsAk(), project.getObsSk(),
                                        project.getObsEndpoint(), project.getObsBucketName(),
                                        objectKey, new ByteArrayInputStream(fileBytes)
                                );
                                System.out.println("       -> 上传华为云成功");
                            } else {
                                System.out.println("       -> OBS已存在 (跳过上传)");
                            }

                            // 7. 解析 XMP 并入库 (核心修改区域)
                            try (InputStream xmpStream = new ByteArrayInputStream(fileBytes)) {
                                // 调用 PhotoProcessor 解析
                                Optional<PhotoData> photoDataOpt = photoProcessor.process(xmpStream, fileName);

                                ProjectPhoto photo = new ProjectPhoto();
                                photo.setProjectId(project.getId());
                                photo.setPhotoUrl(objectKey);

                                if (photoDataOpt.isPresent()) {
                                    PhotoData data = photoDataOpt.get();
                                    photo.setShootTime(data.getCaptureTime());

                                    // 存入飞机坐标 (用于地图显示飞机位置)
                                    photo.setGpsLat(BigDecimal.valueOf(data.getLatitude()));
                                    photo.setGpsLng(BigDecimal.valueOf(data.getLongitude()));

                                    // ✅ 存入激光目标点坐标 (用于进度计算判定)
                                    // 如果解析到了有效值 (-1为无效)，则存入
                                    if (data.getLrfTargetLat() != -1 && data.getLrfTargetLng() != -1) {
                                        photo.setLrfTargetLat(BigDecimal.valueOf(data.getLrfTargetLat()));
                                        photo.setLrfTargetLng(BigDecimal.valueOf(data.getLrfTargetLng()));
                                    }

                                    photo.setLaserDistance(BigDecimal.valueOf(data.getDistance()));
                                    photo.setAbsoluteAltitude(BigDecimal.valueOf(data.getDroneAbsoluteAltitude()));

                                    // 默认为非拐点，参与计算
                                    photo.setIsMarker(false);

                                    projectPhotoMapper.insert(photo);
                                    successCount++;
                                    System.out.println("       ✅ 入库成功 (含 LRFTarget 数据)");
                                } else {
                                    // 解析失败也入库，但没有详细数据
                                    photo.setShootTime(java.time.LocalDateTime.now());
                                    photo.setIsMarker(false);
                                    projectPhotoMapper.insert(photo);
                                    System.out.println("       ⚠️ 入库成功，但无 XMP 数据");
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("       ⚪ [跳过] " + fileName + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                System.out.println("    ✅ 项目同步完成，新增入库: " + successCount + " 张");

                // ✅ 8. 智能计算触发逻辑
                if (successCount > 0) {
                    System.out.println("    ⚡ 监测到有新照片入库，正在触发 [Actual表计算逻辑]...");
                    try {
                        progressService.calculateProjectProgress(project.getId());
                        System.out.println("    ✅ 实际进度 (ActualProgress) 计算并更新完成！");
                    } catch (Exception e) {
                        System.err.println("    ❌ 进度计算发生异常: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("    💤 本次无新照片，跳过 Actual 表计算以节约资源。");
                }

                // ✅ 9. [新增] 记录监控日志 (保证有新数据时记录，或者至少记录一次同步成功)
                SysTaskLog log = new SysTaskLog();
                log.setProjectId(project.getId());
                log.setTaskType(SysTaskLog.TYPE_PHOTO_SYNC);
                log.setStatus(1);
                if (successCount > 0) {
                    log.setMessage("同步完成，新增照片 " + successCount + " 张");
                } else {
                    log.setMessage("检查完毕，无新内容");
                }
                sysTaskLogMapper.insert(log);

            } catch (Exception e) {
                System.err.println("❌ 项目处理异常: " + e.getMessage());

                // ✅ 10. [新增] 记录异常日志
                try {
                    SysTaskLog errorLog = new SysTaskLog();
                    errorLog.setProjectId(project.getId());
                    errorLog.setTaskType(SysTaskLog.TYPE_PHOTO_SYNC);
                    errorLog.setStatus(0);
                    errorLog.setMessage("同步异常: " + e.getMessage());
                    sysTaskLogMapper.insert(errorLog);
                } catch (Exception ex) {
                    // 防止日志记录本身失败导致循环报错，吞掉
                }

                e.printStackTrace();
            }
        }
        System.out.println("=================================================\n");
    }
}