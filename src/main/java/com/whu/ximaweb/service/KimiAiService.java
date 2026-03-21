package com.whu.ximaweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class KimiAiService {

    @Value("${ai.kimi.api-key}")
    private String apiKey;

    @Value("${ai.kimi.api-url}")
    private String apiUrl;

    @Value("${ai.kimi.model}")
    private String modelName;

    @Autowired
    private ObjectMapper objectMapper;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    public String generateProjectAnalysis(String projectContextJson) {
        try {
            // 🔥【核心修改】Prompt 强制要求纯文本，严禁 HTML
            String systemPrompt =
                "你是由国内顶尖大学研发的【XiMA智造助手】。\n" +
                "请根据 JSON 数据，写一封**纯文本**日报邮件。\n" +
                "\n" +
                "### 核心规则：\n" +
                "1. **严禁 Markdown**：不要使用 **加粗**，不要使用 # 标题，不要使用 - 列表符。\n" +
                "2. **使用真实数据**：\n" +
                "   - 时间必须直接使用 JSON 中的 [reportTime]。\n" +
                "   - 楼栋分析时间必须直接使用 JSON 中的 [lastMeasureDate]，严禁自己编造日期。\n" +
                "   - 楼栋高度和层数必须直接使用 JSON 中的数值。\n" +
                "\n" +
                "### 邮件模板（请严格照抄格式）：\n" +
                "尊敬的用户：\n" +
                "您好！我是您的智能工程管家——XiMA智造助手。\n" +
                "您的 [projectName] ，最新进度情况如下：\n" +
                "\n" +
                "【总体评价】\n" +
                "[overallSummary]\n" +
                "\n" +
                "【单体楼栋详细分析】\n" +
                "(请对每栋楼进行如下描述)\n" +
                "1. [楼栋名称]：[status]\n" +
                "   截止 [lastMeasureDate]，施工至第 [currentFloor] 层，高度 [currentHeight] 米。\n" +
                "   (如果是滞后)：该节点原计划于 [plannedEndDate] 完成，当前已滞后 [delayDays] 天（落后计划 [delayFloors] 层）。\n" +
                "   (如果是正常)：施工进度符合预期计划。\n" +
                "\n" +
                "【原因诊断与建议】\n" +
                "原因推测：\n" +
                "1. [根据滞后天数推测原因]\n" +
                "管理建议：\n" +
                "1. [给出具体建议]\n" +
                "\n" +
                "祝您项目顺利完工！\n" +
                "[reportTime]\n"+
                "\n"+
                "深度推理约束：在进行原因推测时，必须结合房建专业知识，从天气（如大风/暴雨）、劳务调配、材料供应（如商砼断档）、机械故障等维度进行跨维度推测，并必须给出对应的【现场实操性】调度建议（如：夜间赶工、派驻催料员等）。"
            ;


            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelName);
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 4000);

            ArrayNode messages = requestBody.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", "项目数据：" + projectContextJson);

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(apiUrl).addHeader("Authorization", "Bearer " + apiKey).post(body).build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return "AI 服务响应异常 (HTTP " + response.code() + ")";
                String respStr = response.body().string();
                JsonNode root = objectMapper.readTree(respStr);
                if (root.has("choices") && root.get("choices").size() > 0) {
                     String raw = root.get("choices").get(0).get("message").get("content").asText();
                     // 再次清洗，防止 AI 习惯性加 Markdown
                     return raw.replace("**", "").replace("###", "").replace("```", "").trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "报告生成失败: " + e.getMessage();
        }
        return "无法生成分析结果。";
    }
}