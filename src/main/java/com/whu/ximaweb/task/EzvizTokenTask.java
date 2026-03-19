package com.whu.ximaweb.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.service.EzvizService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct; // 🔥 关键新增 1：引入 PostConstruct 注解
import java.util.Calendar;
import java.util.List;

/**
 * 萤石云 Token 自动刷新定时任务
 * 包含：开机自启校验 + 每天凌晨自动续期
 */
@Component
public class EzvizTokenTask {

    private static final Logger log = LoggerFactory.getLogger(EzvizTokenTask.class);

    @Autowired
    private SysProjectMapper sysProjectMapper;

    @Autowired
    private EzvizService ezvizService;

    /**
     * 🔥 [关键新增 2] 开机自启机制：项目每次启动时，强制执行一次 Token 刷新
     * 解决开发阶段/服务器重启后，未到凌晨2点导致 Token 过期黑屏的问题
     */
    @PostConstruct
    public void initTokenOnStartup() {
        log.info("🚀 [系统启动] 触发萤石云 Token 开机自检与自动刷新...");
        refreshTokenTask();
    }

    /**
     * 定时策略：每天凌晨 02:00 执行一次
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void refreshTokenTask() {
        log.info("🕒 [萤石云保活] 开始执行 Token 自动刷新任务...");

        try {
            // 1. 查出所有配置了萤石云 AppKey 的项目
            QueryWrapper<SysProject> query = new QueryWrapper<>();
            query.isNotNull("ezviz_app_key").ne("ezviz_app_key", "");

            List<SysProject> projectList = sysProjectMapper.selectList(query);

            if (projectList == null || projectList.isEmpty()) {
                log.info("📭 [萤石云保活] 没有配置萤石云的项目，任务结束。");
                return;
            }

            int successCount = 0;
            int failCount = 0;

            // 2. 遍历每个项目，单独刷新
            for (SysProject project : projectList) {
                String appKey = project.getEzvizAppKey();
                String secret = project.getEzvizAppSecret();

                // 双重检查：确保 Secret 也是有的
                if (secret == null || secret.isEmpty()) {
                    continue;
                }

                try {
                    // 3. 调用萤石云接口获取最新 Token
                    String newToken = ezvizService.getAccessToken(appKey, secret);

                    // 4. 更新内存数据
                    project.setEzvizAccessToken(newToken);

                    // 重新设置过期时间为 7 天后
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_YEAR, 7);
                    project.setEzvizTokenExpireTime(cal.getTime());

                    // 5. 更新数据库
                    sysProjectMapper.updateById(project);

                    successCount++;
                    log.info("✅ [萤石云保活] 项目 [{}] Token 刷新成功", project.getProjectName());

                } catch (Exception e) {
                    failCount++;
                    log.error("❌ [萤石云保活] 项目 [{}] Token 刷新失败: {}", project.getProjectName(), e.getMessage());
                }
            }
            log.info("🏁 [萤石云保活] 任务完成。成功: {}, 失败: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("💥 [萤石云保活] 任务执行异常", e);
        }
    }
}