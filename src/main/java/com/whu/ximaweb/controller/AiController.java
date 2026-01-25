package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.DashboardVo;
import com.whu.ximaweb.mapper.SysUserMapper;
import com.whu.ximaweb.model.SysUser;
import com.whu.ximaweb.service.EmailService;
import com.whu.ximaweb.service.KimiAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private ProgressController progressController; // 复用获取数据逻辑

    @Autowired
    private KimiAiService kimiAiService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 手动触发 AI 分析
     */
    @PostMapping("/analyze")
    public ApiResponse<String> analyzeProject(@RequestBody Map<String, Integer> body) {
        Integer projectId = body.get("projectId");
        if (projectId == null) return ApiResponse.error("缺少 projectId");

        // 1. 获取项目数据 (复用 Dashboard 接口逻辑)
        ApiResponse<DashboardVo> dashboardData = progressController.getDashboardData(projectId);
        if (dashboardData.getStatus() != 200) {
            return ApiResponse.error("无法获取项目数据");
        }
        DashboardVo vo = dashboardData.getData();

        // 2. 简化数据喂给 AI (减少 Token 消耗)
        try {
            // 只提取关键信息，不要把所有历史点都发过去
            String context = simplifyDataForAi(vo);

            // 3. 调用 AI
            String analysis = kimiAiService.generateProjectAnalysis(context);
            return ApiResponse.success("分析完成", analysis);

        } catch (Exception e) {
            return ApiResponse.error("分析失败: " + e.getMessage());
        }
    }

    /**
     * 手动发送分析结果到邮箱
     */
    @PostMapping("/send-report")
    public ApiResponse<String> sendReport(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String content = (String) body.get("content");
        Integer projectId = (Integer) body.get("projectId");

        Integer userId = (Integer) request.getAttribute("currentUser");
        SysUser user = sysUserMapper.selectById(userId);

        if (user == null || user.getEmail() == null) {
            return ApiResponse.error("用户邮箱未设置");
        }

        String subject = "【XiMA智造】项目AI进度分析报告";
        // 简单包装一下邮件内容
        String mailText = "尊敬的用户 " + user.getRealName() + "：\n\n" +
                          "以下是您请求的项目进度智能分析报告：\n\n" +
                          "------------------------------------------------\n" +
                          content + "\n" +
                          "------------------------------------------------\n\n" +
                          "发送时间：" + java.time.LocalDateTime.now().toString();

        // 复用 EmailService 发送
        // 注意：您原本的 EmailService 只有 verifyCode 相关方法，建议加一个通用发送方法
        // 这里暂时使用 JavaMailSender (假设 EmailService 里有注入或者您扩充一下)
        // 为了方便，我们在 EmailService 里加一个 sendSimpleMail 方法
        emailService.sendSimpleMail(user.getEmail(), subject, mailText);

        return ApiResponse.success("邮件已发送至 " + user.getEmail());
    }

    private String simplifyDataForAi(DashboardVo vo) throws Exception {
        // 构建一个精简的 JSON 描述当前状态
        StringBuilder sb = new StringBuilder();
        sb.append("项目名称:").append(vo.getProjectName()).append("; ");
        sb.append("总体统计:滞后").append(vo.getDelayedCount()).append("栋, ");
        sb.append("正常").append(vo.getNormalCount()).append("栋, ");
        sb.append("超前").append(vo.getAheadCount()).append("栋; ");
        sb.append("详情:");
        for (DashboardVo.BuildingProgressVo b : vo.getBuildings()) {
            sb.append("[").append(b.getBuildingName()).append(": ");
            sb.append("当前").append(b.getCurrentFloor()).append("层, ");
            sb.append("状态").append(b.getStatusTag()).append("]; ");
        }
        return sb.toString();
    }
}