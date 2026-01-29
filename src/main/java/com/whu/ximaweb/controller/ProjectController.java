package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.Coordinate;
import com.whu.ximaweb.dto.MonitorVo; // ✅ 新增
import com.whu.ximaweb.dto.ProjectImportRequest;
import com.whu.ximaweb.dto.dji.DjiMediaFileDto;
import com.whu.ximaweb.dto.dji.DjiProjectDto;
import com.whu.ximaweb.mapper.ProjectPhotoMapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.mapper.SysTaskLogMapper; // ✅ 新增
import com.whu.ximaweb.mapper.SysUserMapper;    // ✅ 新增
import com.whu.ximaweb.model.*;
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
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.whu.ximaweb.mapper.SysTaskLogMapper;
import com.whu.ximaweb.model.SysTaskLog;

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
    private SysTaskLogMapper sysTaskLogMapper; // ✅ 注入日志操作

    @Autowired
    private SysUserMapper sysUserMapper; // ✅ 注入用户操作(用于获取日报时间)

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private ObsService obsService;

    @Autowired
    private PhotoProcessor photoProcessor;

    @Autowired
    private com.whu.ximaweb.service.EzvizService ezvizService; // 🔥 [新增]

    /**
     * [新增接口] 获取项目的自动化监控面板数据
     */
    @GetMapping("/{id}/monitor")
    public ApiResponse<MonitorVo> getMonitorData(@PathVariable Integer id) {
        SysProject project = sysProjectMapper.selectById(id);
        if (project == null) return ApiResponse.error("项目不存在");

        MonitorVo vo = new MonitorVo();

        // 🔥 [修复1] 强制设置中国时区，解决时间显示不对的问题
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // 🔥 [修复2] 日志时间格式增加年月日
        SimpleDateFormat timeSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        timeSdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

        // 🔥 [新增] 注入萤石云播放地址
        // 只有当 Token 存在且未过期时，才返回播放地址
        if (project.getEzvizAccessToken() != null && project.getEzvizDeviceSerial() != null) {
            // 简单判断过期 (为了演示稳健性)
            if (project.getEzvizTokenExpireTime() != null && new Date().after(project.getEzvizTokenExpireTime())) {
                // 如果过期了，这里应该触发刷新逻辑，但毕设演示暂不处理，或者手动点保存触发刷新
                vo.setEzvizUrl("");
            } else {
                // 构造标准播放地址: ezopen://open.ys7.com/[验证码@][序列号]/[通道].live
                String playUrl = "ezopen://open.ys7.com/";
                if (project.getEzvizValidateCode() != null && !project.getEzvizValidateCode().isEmpty()) {
                    playUrl += project.getEzvizValidateCode() + "@";
                }
                playUrl += project.getEzvizDeviceSerial() + "/1.live";

                vo.setEzvizUrl(playUrl);
                vo.setEzvizToken(project.getEzvizAccessToken());
            }
        }


        // 1. --- 左侧：司空2同步监控 ---
        Long totalPhotos = projectPhotoMapper.selectCount(new QueryWrapper<ProjectPhoto>().eq("project_id", id));
        vo.setTotalPhotos(totalPhotos);

        Date lastSync = sysTaskLogMapper.selectLatestTime(id, SysTaskLog.TYPE_PHOTO_SYNC);
        if (lastSync != null) {
            vo.setLastSyncTime(sdf.format(lastSync));
            vo.setDjiConnected(true);

            Calendar cal = Calendar.getInstance();
            cal.setTime(lastSync);
            cal.add(Calendar.HOUR_OF_DAY, 1);

            if (cal.getTime().before(new Date())) {
                vo.setNextSyncTime("任务执行中...");
            } else {
                long diffMinutes = (cal.getTime().getTime() - System.currentTimeMillis()) / (1000 * 60);
                vo.setNextSyncTime(diffMinutes + " 分钟后");
            }
        } else {
            vo.setLastSyncTime("暂无记录");
            vo.setDjiConnected(false);
            vo.setNextSyncTime("等待初始化");
        }

        // 2. --- 右侧：日报监控 ---
        vo.setReportEnabled(project.getEnableAiReport() != null && project.getEnableAiReport() == 1);

        // 计算运行天数 (修复 getCreatedAt 调用)
        if (project.getCreatedAt() != null) {
            long days = ChronoUnit.DAYS.between(
                    project.getCreatedAt().toLocalDate(),
                    java.time.LocalDate.now()
            );
            vo.setRunDays(days <= 0 ? 1 : days);
        } else {
            vo.setRunDays(1L);
        }

        int reportCount = sysTaskLogMapper.countByProjectAndType(id, SysTaskLog.TYPE_DAILY_REPORT);
        vo.setTotalReports(reportCount);

        Date lastReport = sysTaskLogMapper.selectLatestTime(id, SysTaskLog.TYPE_DAILY_REPORT);
        vo.setLastReportTime(lastReport != null ? sdf.format(lastReport) : "尚未发送");

        SysUser creator = sysUserMapper.selectById(project.getCreatedBy());
        if (creator != null && creator.getReportTime() != null) {
            vo.setReceiverName(creator.getRealName() != null ? creator.getRealName() : creator.getUsername());

            String reportTimeStr = creator.getReportTime();
            LocalTime reportTime = LocalTime.parse(reportTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextRun = now.with(reportTime).withSecond(0);

            if (now.isAfter(nextRun)) {
                nextRun = nextRun.plusDays(1);
            }

            long hoursLeft = ChronoUnit.HOURS.between(now, nextRun);
            long minutesLeft = ChronoUnit.MINUTES.between(now, nextRun) % 60;
            vo.setNextReportTime(hoursLeft + "小时 " + minutesLeft + "分 后");

        } else {
            vo.setReceiverName("管理员");
            vo.setNextReportTime("未设置时间");
        }

        // 3. --- 底部：日志流 ---
        List<SysTaskLog> logs = sysTaskLogMapper.selectRecentLogs(id, 20);
        List<MonitorVo.LogItem> logItems = new ArrayList<>();

        if (logs != null) {
            for (SysTaskLog log : logs) {
                MonitorVo.LogItem item = new MonitorVo.LogItem();
                item.setTime(timeSdf.format(log.getCreateTime())); // 使用修正后的带日期格式
                item.setMessage(log.getMessage());

                if (log.getStatus() == 0) item.setType("ERROR");
                else if (SysTaskLog.TYPE_DAILY_REPORT.equals(log.getTaskType())) item.setType("SUCCESS");
                else item.setType("INFO");

                logItems.add(item);
            }
        }
        vo.setLogs(logItems);

        return ApiResponse.success("获取监控数据成功", vo);
    }

    // =========================================================================
    // 下面是原有的接口，保持不变
    // =========================================================================

    @GetMapping("/dji-workspaces")
    public ApiResponse<List<DjiProjectDto>> getDjiWorkspaces(@RequestParam String apiKey, HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");
        if (userId == null) userId = 1;
        List<DjiProjectDto> djiProjects = djiService.getProjects(apiKey);
        QueryWrapper<SysProject> query = new QueryWrapper<>();
        query.select("dji_project_uuid");
        query.eq("created_by", userId);
        List<SysProject> myExistingProjects = sysProjectMapper.selectList(query);
        Set<String> importedUuids = new HashSet<>();
        if (myExistingProjects != null) {
            for (SysProject p : myExistingProjects) {
                if (p.getDjiProjectUuid() != null) {
                    importedUuids.add(p.getDjiProjectUuid().trim().toLowerCase());
                }
            }
        }
        if (djiProjects != null) {
            for (DjiProjectDto dto : djiProjects) {
                if (dto.getUuid() != null) {
                    String cleanUuid = dto.getUuid().trim().toLowerCase();
                    if (importedUuids.contains(cleanUuid)) {
                        dto.setImported(true);
                    }
                }
            }
        }
        return ApiResponse.success("获取成功", djiProjects);
    }

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

    @GetMapping("/my")
    public ApiResponse<List<SysProject>> getMyProjects(HttpServletRequest httpRequest) {
        Integer userId = (Integer) httpRequest.getAttribute("currentUser");
        List<SysProject> projects = projectService.getUserProjects(userId);
        return ApiResponse.success("获取成功", projects);
    }

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

    @GetMapping("/{id}")
    public ApiResponse<SysProject> getProjectDetail(@PathVariable Integer id) {
        SysProject project = sysProjectMapper.selectById(id);
        if (project == null) {
            return ApiResponse.error("项目不存在");
        }
        return ApiResponse.success("获取成功", project);
    }

    @PutMapping("/{id}")
    public ApiResponse<Object> updateProject(@PathVariable Integer id, @RequestBody SysProject project) {
        project.setId(id);
        boolean result = projectService.updateProjectInfo(project);
        return result ? ApiResponse.success("更新成功") : ApiResponse.error("更新失败");
    }

    /**
     * 手动触发同步接口 (已修复日志记录功能)
     */
    @PostMapping("/{projectId}/sync")
    public ApiResponse<String> manualSyncPhotos(@PathVariable Integer projectId, @RequestBody Map<String, String> body) {
        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) return ApiResponse.error("项目不存在");

        String tempKeyword = body.get("tempKeyword");
        String targetKeyword = (tempKeyword != null && !tempKeyword.trim().isEmpty())
                                ? tempKeyword.trim()
                                : project.getPhotoFolderKeyword();

        // 1. [新增] 准备日志对象
        SysTaskLog log = new SysTaskLog();
        log.setProjectId(projectId);
        log.setTaskType(SysTaskLog.TYPE_PHOTO_SYNC);

        try {
            List<DjiMediaFileDto> djiFiles = djiService.getPhotosFromFolder(
                project.getDjiProjectUuid(),
                project.getDjiOrgKey(),
                targetKeyword
            );

            if (djiFiles.isEmpty()) {
                // 2. [新增] 即使没找到文件，也记录一条"成功"日志，证明系统检查过了
                log.setStatus(1);
                log.setMessage("手动检查完毕，司空平台无新文件");
                sysTaskLogMapper.insert(log);

                return ApiResponse.success("同步完成，未找到包含关键词 [" + targetKeyword + "] 的新照片。");
            }

            int successCount = 0;
            for (DjiMediaFileDto djiFile : djiFiles) {
                // --- 原有的过滤逻辑 (保持不变) ---
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

                // --- 原有的下载与解析逻辑 (保持不变) ---
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

                                    // 存目标点坐标
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

            // 3. [新增] 循环结束后，记录最终结果日志
            log.setStatus(1);
            if (successCount > 0) {
                progressService.calculateProjectProgress(projectId);
                log.setMessage("手动同步完成，新增 " + successCount + " 张");
                sysTaskLogMapper.insert(log);

                return ApiResponse.success("同步成功，新增 " + successCount + " 张照片，进度已自动更新。");
            } else {
                log.setMessage("手动检查完毕，云端文件均已同步");
                sysTaskLogMapper.insert(log);

                return ApiResponse.success("同步完成，找到 " + djiFiles.size() + " 张照片，但都是已存在的，无新增。");
            }

        } catch (Exception e) {
            e.printStackTrace();
            // 4. [新增] 异常情况也记录日志
            log.setStatus(0);
            log.setMessage("手动同步异常: " + e.getMessage());
            sysTaskLogMapper.insert(log);

            return ApiResponse.error("同步过程中发生错误: " + e.getMessage());
        }
    }

    /**
     * [新增接口] 保存萤石云摄像头配置，并尝试自动获取 Token
     */
    @PostMapping("/{projectId}/camera-config")
    public ApiResponse<Object> saveCameraConfig(@PathVariable Integer projectId, @RequestBody Map<String, String> body) {
        SysProject project = sysProjectMapper.selectById(projectId);
        if (project == null) return ApiResponse.error("项目不存在");

        String appKey = body.get("appKey");
        String secret = body.get("secret");
        String serial = body.get("serial");
        String code = body.get("validateCode"); // 验证码

        // 1. 更新基础配置
        project.setEzvizAppKey(appKey);
        project.setEzvizAppSecret(secret);
        project.setEzvizDeviceSerial(serial);
        project.setEzvizValidateCode(code);

        // 2. 如果填了 Key 和 Secret，尝试去萤石云拿 Token (实战核心！)
        if (appKey != null && !appKey.isEmpty() && secret != null && !secret.isEmpty()) {
            try {
                System.out.println(">>> 正在向萤石云申请 Token...");
                String token = ezvizService.getAccessToken(appKey, secret);

                // 获取成功，保存 Token 和过期时间 (简单起见，设为7天后过期)
                project.setEzvizAccessToken(token);

                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, 7); // 萤石云Token有效期默认7天
                project.setEzvizTokenExpireTime(cal.getTime());

                System.out.println(">>> 萤石云 Token 获取成功: " + token);
            } catch (Exception e) {
                e.printStackTrace();
                // 注意：这里我们捕获异常但不阻断保存，只是提示用户
                // 但为了严谨，我们可以先把配置存进去，前端提示警告
                sysProjectMapper.updateById(project);
                return ApiResponse.error("配置保存成功，但连接萤石云失败: " + e.getMessage());
            }
        }

        // 3. 保存入库
        sysProjectMapper.updateById(project);
        return ApiResponse.success("摄像头配置已保存，且连接测试通过！");
    }
}