package com.whu.ximaweb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.DashboardVo;
import com.whu.ximaweb.mapper.PlanProgressMapper;
import com.whu.ximaweb.mapper.SysUserMapper;
import com.whu.ximaweb.model.PlanProgress;
import com.whu.ximaweb.model.SysUser;
import com.whu.ximaweb.service.EmailService;
import com.whu.ximaweb.service.KimiAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private ProgressController progressController;

    @Autowired
    private KimiAiService kimiAiService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlanProgressMapper planProgressMapper;

    /**
     * 手动触发 AI 分析
     */
    @PostMapping("/analyze")
    public ApiResponse<String> analyzeProject(@RequestBody Map<String, Integer> body) {
        Integer projectId = body.get("projectId");
        if (projectId == null) return ApiResponse.error("缺少 projectId");

        ApiResponse<DashboardVo> dashboardData = progressController.getDashboardData(projectId);
        if (dashboardData.getStatus() != 200) {
            return ApiResponse.error("无法获取项目数据");
        }
        DashboardVo vo = dashboardData.getData();

        try {
            String context = simplifyDataForAi(vo);
            String analysis = kimiAiService.generateProjectAnalysis(context);
            return ApiResponse.success("分析完成", analysis);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("分析失败: " + e.getMessage());
        }
    }

    /**
     * 手动发送分析结果到邮箱
     */
    @PostMapping("/send-report")
    public ApiResponse<String> sendReport(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String content = (String) body.get("content");

        // 兼容 projectId 类型
        Object pIdObj = body.get("projectId");
        Integer projectId = null;
        try {
            if (pIdObj instanceof Integer) {
                projectId = (Integer) pIdObj;
            } else if (pIdObj instanceof String) {
                projectId = Integer.parseInt((String) pIdObj);
            } else {
                return ApiResponse.error("参数错误：projectId 缺失或格式不正确");
            }
        } catch (NumberFormatException e) {
            return ApiResponse.error("参数错误：projectId 必须是数字");
        }

        Object userIdObj = request.getAttribute("currentUser");
        if (userIdObj == null) return ApiResponse.error("用户未登录或Token无效");
        Integer userId = (Integer) userIdObj;

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getEmail() == null) {
            return ApiResponse.error("用户邮箱未设置");
        }

        String subject = "【XiMA智造】项目AI进度分析报告";
        String mailText = "尊敬的用户 " + user.getRealName() + "：\n\n" +
                          "以下是您请求的项目进度智能分析报告：\n\n" +
                          "------------------------------------------------\n" +
                          content + "\n" +
                          "------------------------------------------------\n\n" +
                          "发送时间：" + java.time.LocalDateTime.now().toString();

        try {
            emailService.sendSimpleMail(user.getEmail(), subject, mailText);
            return ApiResponse.success("邮件已发送至 " + user.getEmail());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("邮件发送失败，请检查服务器日志");
        }
    }

    /**
     * 构建精准的数据上下文
     * ✅ 核心修改：滞后计算基准改为“最后一次实测日期”
     */
    private String simplifyDataForAi(DashboardVo vo) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("reportTime", LocalDate.now().toString());
        root.put("projectName", vo.getProjectName());

        root.put("summary", String.format("共%d栋楼。滞后:%d, 正常:%d, 超前:%d",
                vo.getTotalBuildings(), vo.getDelayedCount(), vo.getNormalCount(), vo.getAheadCount()));

        ArrayNode buildingsNode = root.putArray("buildings");

        for (DashboardVo.BuildingProgressVo b : vo.getBuildings()) {
            ObjectNode bNode = buildingsNode.addObject();
            bNode.put("name", b.getBuildingName());
            bNode.put("currentFloor", b.getCurrentFloor());
            bNode.put("currentHeight", b.getCurrentHeight() != null ? b.getCurrentHeight() : 0.0);
            bNode.put("status", b.getStatusTag());

            // 1. 获取实际完成时间 (作为计算基准)
            String actualDateStr = "暂无数据";
            LocalDate actualDate = null;
            if (b.getLastMeasureDate() != null && !b.getLastMeasureDate().equals("-")) {
                actualDateStr = b.getLastMeasureDate();
                try {
                    actualDate = LocalDate.parse(actualDateStr);
                } catch (Exception e) {}
            }
            bNode.put("actualFinishDate", actualDateStr);

            // 2. 计划完成时间 (查询该层楼的计划)
            String planDateStr = "未配置计划";
            LocalDate planDate = null;
            if (b.getPlanName() != null && b.getCurrentFloor() > 0) {
                PlanProgress plan = planProgressMapper.selectOne(
                    new QueryWrapper<PlanProgress>()
                        .eq("Building", b.getPlanName())
                        .like("Floor", b.getCurrentFloor().toString())
                        .last("LIMIT 1")
                );
                if (plan != null && plan.getPlannedEnd() != null) {
                    planDate = plan.getPlannedEnd().toLocalDate();
                    planDateStr = planDate.toString();
                }
            }
            bNode.put("plannedFinishDate", planDateStr);

            // 3. 计算时间滞后 (天数)
            if (actualDate != null && planDate != null) {
                long diff = ChronoUnit.DAYS.between(planDate, actualDate);
                bNode.put("lagDays", diff);
            } else {
                bNode.put("lagDays", "无法计算");
            }

            // === 4. 计算楼层滞后 (修正版：基于最后实测日期) ===
            // 逻辑：如果最后一次拍照是10天前，那就查10天前应该盖到第几层
            int floorsBehind = 0;
            String checkDateDesc = "无实测数据";

            if (actualDate != null) {
                // 查出 "实测那天" 应该建到多少层
                int targetFloorAtRecord = getPlanFloorAtDate(b.getPlanName(), actualDate);
                bNode.put("plannedFloorAtRecord", targetFloorAtRecord);

                // 计算滞后
                floorsBehind = targetFloorAtRecord - b.getCurrentFloor();
                checkDateDesc = "截止于" + actualDate.toString();
            } else {
                // 无实测数据，无法计算基于实测的滞后
                bNode.put("plannedFloorAtRecord", 0);
            }

            bNode.put("floorsBehind", floorsBehind);
            bNode.put("dataTimeScope", checkDateDesc); // 告诉 AI 这是截止到什么时候的结论
        }

        return objectMapper.writeValueAsString(root);
    }

    /**
     * 辅助方法：获取截至某天的计划楼层
     */
    private int getPlanFloorAtDate(String planName, LocalDate date) {
        if (planName == null) return 0;
        List<PlanProgress> plans = planProgressMapper.selectList(new QueryWrapper<PlanProgress>()
                .eq("Building", planName)
                .le("PlannedEnd", date.atTime(23, 59, 59)));
        int max = 0;
        for (PlanProgress p : plans) {
            try {
                String fStr = p.getFloor().replaceAll("[^0-9]", "");
                if (!fStr.isEmpty()) max = Math.max(max, Integer.parseInt(fStr));
            } catch (Exception e) {}
        }
        return max;
    }
}