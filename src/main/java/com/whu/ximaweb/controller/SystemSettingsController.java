package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.mapper.SysProjectMapper;
import com.whu.ximaweb.mapper.SysUserMapper;
import com.whu.ximaweb.model.SysProject;
import com.whu.ximaweb.model.SysUser;
import com.whu.ximaweb.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SystemSettingsController {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysProjectMapper sysProjectMapper;

    @Autowired
    private EmailService emailService;

    @Autowired
    private com.whu.ximaweb.service.ProgressService progressService;

    @Autowired
    private com.whu.ximaweb.service.KimiAiService kimiAiService;

    /**
     * 获取用户设置信息 (邮箱、推送时间、各项目开关)
     */
    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getSettings(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("currentUser");
        SysUser user = sysUserMapper.selectById(userId);

        // 获取用户创建的项目 (用于开关列表)
        List<SysProject> projects = sysProjectMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysProject>()
                .eq("created_by", userId)
        );

        return ApiResponse.success("获取成功", Map.of(
            "email", user.getEmail(),
            "reportTime", user.getReportTime() == null ? "" : user.getReportTime(),
            "projects", projects
        ));
    }

    /**
     * 发送修改邮箱验证码
     */
    @PostMapping("/send-verify")
    public ApiResponse<String> sendVerify(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isEmpty()) return ApiResponse.error("邮箱不能为空");
        emailService.sendVerifyCode(email);
        return ApiResponse.success("验证码已发送");
    }

    /**
     * 保存设置 (修改邮箱需要验证码)
     */
    @PostMapping("/save")
    public ApiResponse<String> saveSettings(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("currentUser");
        SysUser user = sysUserMapper.selectById(userId);

        String newEmail = (String) body.get("email");
        String code = (String) body.get("code");
        String reportTime = (String) body.get("reportTime"); // "09:00" or ""

        // 1. 如果修改了邮箱，必须校验验证码
        if (!newEmail.equals(user.getEmail())) {
            if (code == null || !emailService.verifyCode(newEmail, code)) {
                return ApiResponse.error("验证码错误或已过期");
            }
            user.setEmail(newEmail);
        }

        // 2. 更新推送时间
        user.setReportTime(reportTime == null || reportTime.isEmpty() ? null : reportTime);
        sysUserMapper.updateById(user);

        // 3. 更新项目开关 (Projects 数组)
        List<Map<String, Object>> projectSettings = (List<Map<String, Object>>) body.get("projectSettings");
        if (projectSettings != null) {
            for (Map<String, Object> p : projectSettings) {
                Integer pid = (Integer) p.get("id");
                Boolean enabled = (Boolean) p.get("enableAiReport"); // 前端传 true/false

                SysProject proj = new SysProject();
                proj.setId(pid);
                proj.setEnableAiReport(enabled ? 1 : 0);
                sysProjectMapper.updateById(proj);
            }
        }

        return ApiResponse.success("设置保存成功");
    }

    /**
     * ✅ 真实数据预览接口
     * 获取指定项目的实时AI日报 (基于数据库真实进度)
     */
    @PostMapping("/preview-report")
    public ApiResponse<String> previewDailyReport(@RequestBody Map<String, Integer> body) {
        Integer projectId = body.get("projectId");
        if (projectId == null) return ApiResponse.error("项目ID不能为空");

        try {
            // 1. 从数据库抓取“血淋淋”的真实数据 (JSON格式)
            String realProjectData = progressService.getProjectFullStatusJson(projectId);

            System.out.println(">>> 投喂给AI的真实数据: " + realProjectData); // 调试看日志

            // 2. 召唤 AI 进行分析
            String htmlReport = kimiAiService.generateProjectAnalysis(realProjectData);

            return ApiResponse.success("生成成功", htmlReport);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("生成失败: " + e.getMessage());
        }
    }
}