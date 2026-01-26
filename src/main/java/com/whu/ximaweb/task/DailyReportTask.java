package com.whu.ximaweb.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.controller.AiController;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.mapper.SysTaskLogMapper; // ✅ 新增
import com.whu.ximaweb.mapper.SysUserMapper;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.model.SysTaskLog; // ✅ 新增
import com.whu.ximaweb.model.SysUser;
import com.whu.ximaweb.service.EmailService; // ✅ 新增
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class DailyReportTask {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysProjectMapper sysProjectMapper;

    @Autowired
    private SysTaskLogMapper sysTaskLogMapper; // ✅ 注入日志 Mapper

    @Autowired
    private EmailService emailService; // ✅ 注入邮件服务

    @Autowired
    private AiController aiController; // 复用分析逻辑

    // 每分钟检查一次，看是否有用户的设定时间到了
    @Scheduled(cron = "0 * * * * ?")
    public void executeDailyReport() {
        String nowStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        System.out.println(">>> [定时任务] 检查AI报告发送任务: " + nowStr);

        // 1. 查找所有设定了当前时间发送报告的用户
        List<SysUser> users = sysUserMapper.selectList(
            new QueryWrapper<SysUser>().eq("report_time", nowStr)
        );

        for (SysUser user : users) {
            // 2. 查找该用户下所有开启了 AI 报告的项目
            List<SysProject> projects = sysProjectMapper.selectList(
                new QueryWrapper<SysProject>()
                    .eq("created_by", user.getId())
                    .eq("enable_ai_report", 1)
            );

            for (SysProject project : projects) {
                try {
                    System.out.println("   -> 正在为用户 " + user.getUsername() + " 生成项目 [" + project.getProjectName() + "] 的报告...");

                    // 3. 调用 AI 分析
                    // 注意：AiController.analyzeProject 需要 ProjectId。
                    ApiResponse<String> analysisRes = aiController.analyzeProject(Map.of("projectId", project.getId()));

                    if (analysisRes.getStatus() == 200) {
                        String content = analysisRes.getData();
                        // 4. 发送邮件
                        String subject = "【每日自动简报】" + project.getProjectName();
                        String mailText = "尊敬的 " + user.getRealName() + "：\n\n" +
                                          "这是您的项目 [" + project.getProjectName() + "] 今日的自动巡检报告：\n\n" +
                                          content + "\n\n" +
                                          "(您可以在系统设置中调整发送时间或关闭此项目的推送)";

                        // ✅ [修改] 真正发送邮件 (原代码注释掉了)
                        if (emailService != null) {
                            emailService.sendSimpleMail(user.getEmail(), subject, mailText);
                            System.out.println("      ✔ 邮件已发送至: " + user.getEmail());

                            // ✅ [新增] 记录日志到数据库，供监控面板展示
                            SysTaskLog log = new SysTaskLog();
                            log.setProjectId(project.getId());
                            log.setTaskType(SysTaskLog.TYPE_DAILY_REPORT);
                            log.setStatus(1);
                            log.setMessage("日报已发送至: " + (user.getRealName() != null ? user.getRealName() : user.getUsername()));
                            sysTaskLogMapper.insert(log);
                        } else {
                            System.err.println("      ⚠ EmailService 未注入，无法发送邮件");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("   -> 发送失败: " + e.getMessage());
                    e.printStackTrace();

                    // 记录失败日志(可选)
                    try {
                        SysTaskLog errorLog = new SysTaskLog();
                        errorLog.setProjectId(project.getId());
                        errorLog.setTaskType(SysTaskLog.TYPE_DAILY_REPORT);
                        errorLog.setStatus(0);
                        errorLog.setMessage("发送失败: " + e.getMessage());
                        sysTaskLogMapper.insert(errorLog);
                    } catch (Exception ex) {}
                }
            }
        }
    }
}