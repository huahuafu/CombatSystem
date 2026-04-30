package com.military.combat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class AiScenarioService {

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DASHSCOPE_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    public String generateScenarioSuggestion(String userGoal) {
        String prompt = buildPrompt(userGoal);
        return callQwenAPI(prompt);
    }

    public String generateActivityAndInteractionSuggestion(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名作战参谋，请根据当前想定数据给出活动与交互规则建议。\n");
        sb.append("请用简洁的中文列出：\n");
        sb.append("1. 建议的作战活动列表（每个包含：名称、类型ATTACK/DEFEND/RECON、主要参与单位类型、起止回合）。\n");
        sb.append("2. 建议的交互规则列表（每个包含：名称、类型COORDINATE/ATTACK/DEFEND/SUPPORT、触发条件、效果类型DAMAGE_BOOST/SPEED_BOOST/DEFENSE_BOOST及数值）。\n");
        sb.append("3. 简短说明这些建议如何支撑整体作战目标。\n");
        sb.append("当前想定概要（JSON）：\n");
        sb.append(context.toString());
        return callQwenAPI(sb.toString());
    }

    /**
     * 生成结构化自动作战计划（JSON），供系统自动落库执行。
     */
    public String generateStructuredOperationalPlan(String goal, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是联合作战智能参谋。请严格输出 JSON，不要任何额外文字。\n");
        sb.append("字段要求（阶段 B：须与系统可执行结构对齐）：\n");
        sb.append("{\n");
        sb.append("  \"groups\": [\"CARRIER_STRIKE_GROUP\"|\"LAND_ROCKET_ASSAULT_GROUP\"],\n");
        sb.append("  \"commands\": [{\"orderType\":\"ISR|EW|STRIKE|AIR_DEFENSE|RESUPPLY\",\"objective\":\"...\"}],\n");
        sb.append("  \"sorties\": [{\"missionType\":\"ISR|EW|STRIKE\",\"objective\":\"...\"}],\n");
        sb.append("  \"opposingActions\": [{\"actionType\":\"COUNTER_RECON|EW_SUPPRESSION|DECOY|SAM_INTERCEPT\",\"intensity\":0.1-1.0}],\n");
        sb.append("  \"activities\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"作战活动名称\",\n");
        sb.append("      \"type\": \"ATTACK|DEFEND|RECON|SUPPORT\",\n");
        sb.append("      \"side\": \"RED|BLUE\",\n");
        sb.append("      \"description\": \"简述\",\n");
        sb.append("      \"objectiveId\": \"可选，填上下文 objectivesForBinding 中某 id；可空则由系统选首个目标\",\n");
        sb.append("      \"pickUnits\": {\"side\":\"RED|BLUE\",\"maxUnits\":2},\n");
        sb.append("      \"steps\": [\n");
        sb.append("        {\"stepNumber\":1,\"name\":\"机动\",\"action\":\"MOVE|FIRE|RECON\",\"targetType\":\"OBJECTIVE|UNIT|LOCATION\",\"target\":\"单位id或目标id或经纬文本\",\"roundDuration\":3}\n");
        sb.append("      ],\n");
        sb.append("      \"startRound\": 0,\n");
        sb.append("      \"endRound\": 25,\n");
        sb.append("      \"mission\": \"任务摘要\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"interactionRules\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"规则名称\",\n");
        sb.append("      \"type\": \"ATTACK|DEFEND|COORDINATE|SUPPORT\",\n");
        sb.append("      \"description\": \"说明\",\n");
        sb.append("      \"sourceSide\": \"RED\",\n");
        sb.append("      \"targetSide\": \"BLUE\",\n");
        sb.append("      \"triggerType\": \"DISTANCE\",\n");
        sb.append("      \"effectType\": \"DAMAGE_BOOST|SPEED_BOOST|DEFENSE_BOOST\",\n");
        sb.append("      \"effectValue\": 1.15,\n");
        sb.append("      \"minDistance\": 0,\n");
        sb.append("      \"maxDistance\": 250000,\n");
        sb.append("      \"startRound\": 0,\n");
        sb.append("      \"endRound\": -1,\n");
        sb.append("      \"enabled\": true,\n");
        sb.append("      \"priority\": 10\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"simulateRounds\": 1-5,\n");
        sb.append("  \"commanderSummary\": \"给指挥官的简短决策建议\"\n");
        sb.append("}\n");
        sb.append("约束：\n");
        sb.append("1) 必须至少包含 1 个 group。\n");
        sb.append("2) commands 至少 2 条，sorties 至少 2 条，opposingActions 至少 1 条。\n");
        sb.append("3) activities 至少 1 条、interactionRules 至少 1 条；steps 至少 1 步，action 须为 MOVE/FIRE/RECON 等系统支持值。\n");
        sb.append("4) target 填真实 id：请优先使用上下文 unitsForBinding / objectivesForBinding 中的 id。\n");
        sb.append("5) 贴近现代作战，不要空字段。\n");
        sb.append("用户目标：").append(goal == null ? "未提供" : goal).append("\n");
        sb.append("当前系统上下文：").append(context == null ? "{}" : context.toString()).append("\n");
        return callQwenAPI(sb.toString());
    }

    /**
     * 基于当前回合态势进行实时再规划（JSON输出）。
     */
    public String generateRealtimeReplanJson(String goal, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是实时作战指挥AI。根据当前战况做下一回合再规划。仅输出 JSON，不要其他文字。\n");
        sb.append("输出结构：\n");
        sb.append("{\n");
        sb.append("  \"commands\": [{\"orderType\":\"ISR|EW|STRIKE|AIR_DEFENSE|RESUPPLY\",\"objective\":\"...\"}],\n");
        sb.append("  \"sorties\": [{\"missionType\":\"ISR|EW|STRIKE\",\"objective\":\"...\"}],\n");
        sb.append("  \"opposingActions\": [{\"actionType\":\"COUNTER_RECON|EW_SUPPRESSION|DECOY|SAM_INTERCEPT\",\"intensity\":0.1-1.0}],\n");
        sb.append("  \"brief\": \"一句话说明再规划意图\"\n");
        sb.append("}\n");
        sb.append("约束：commands 至少1条，sorties 至少1条，opposingActions 可选。\n");
        sb.append("用户目标：").append(goal == null ? "未提供" : goal).append("\n");
        sb.append("当前态势上下文：").append(context == null ? "{}" : context.toString()).append("\n");
        return callQwenAPI(sb.toString());
    }

    /**
     * 生成 Top-N 候选策略（推荐阶段）。
     */
    public String generateTopNStrategiesJson(String goal, Map<String, Object> context, int topN) {
        int n = Math.max(1, Math.min(6, topN));
        StringBuilder sb = new StringBuilder();
        sb.append("你是海上作战指挥决策AI。请生成 Top-").append(n).append(" 候选策略，仅输出 JSON。\n");
        sb.append("输出结构：\n");
        sb.append("{\n");
        sb.append("  \"strategies\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"策略名称\",\n");
        sb.append("      \"hypothesis\": \"策略假设\",\n");
        sb.append("      \"riskSummary\": \"风险摘要\",\n");
        sb.append("      \"resourcePrediction\": \"资源消耗预测\",\n");
        sb.append("      \"predicted\": {\n");
        sb.append("        \"winRate\": 0-1,\n");
        sb.append("        \"expectedLoss\": 0-100,\n");
        sb.append("        \"missionSuccessRate\": 0-1,\n");
        sb.append("        \"confidence\": 0-1\n");
        sb.append("      },\n");
        sb.append("      \"plan\": {\n");
        sb.append("        \"commands\": [{\"orderType\":\"ISR|EW|STRIKE|AIR_DEFENSE|RESUPPLY\",\"objective\":\"...\"}],\n");
        sb.append("        \"sorties\": [{\"missionType\":\"ISR|EW|STRIKE\",\"objective\":\"...\"}],\n");
        sb.append("        \"opposingActions\": [{\"actionType\":\"COUNTER_RECON|EW_SUPPRESSION|DECOY|SAM_INTERCEPT\",\"intensity\":0.1-1.0}],\n");
        sb.append("        \"simulateRounds\": 1-5,\n");
        sb.append("        \"commanderSummary\": \"一句话建议\"\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("约束：strategies 数组长度必须为 ").append(n).append("；数据要可执行，不要空字段。\n");
        sb.append("用户目标：").append(goal == null ? "未提供" : goal).append("\n");
        sb.append("上下文：").append(context == null ? "{}" : context.toString()).append("\n");
        return callQwenAPI(sb.toString());
    }

    /**
     * 生成六阶段杀伤链动作包（结构化 JSON），供四类策略解释与可执行模板使用。
     * <p>必须仅输出 JSON，不要其他文字。</p>
     */
    public String generateKillChainStageActionsJson(String goal, Map<String, Object> context, String strategyLabel) {
        String label = strategyLabel == null ? "BALANCED" : strategyLabel.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("你是海上作战指挥决策AI。请严格仅输出 JSON，不要任何解释文字。\n");
        sb.append("任务：为策略 ").append(label).append(" 生成 F2T2EA 六阶段杀伤链动作包（可执行、可审计）。\n");
        sb.append("输出结构：\n");
        sb.append("{\n");
        sb.append("  \"stageActions\": [\n");
        sb.append("    {\"stage\":\"FIND|FIX|TRACK|TARGET|ENGAGE|ASSESS\",\"actionType\":\"SCAN|FUSION|MAINTAIN|ALLOCATE|ENGAGE|ASSESS\",\"title\":\"短标题\",\"detail\":\"一句话说明\",\"intensity\":0.0-1.0}\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("约束：\n");
        sb.append("1) stageActions 必须覆盖六个 stage，每个 stage 至少 1 条。\n");
        sb.append("2) intensity 0~1，LOSS_MIN 更保守，WIN_MAX 更激进，SPEED_MAX 更快节奏，BALANCED 均衡。\n");
        sb.append("3) detail 必须可落地（不要空话）。\n");
        sb.append("用户目标：").append(goal == null ? "未提供" : goal).append("\n");
        sb.append("上下文（摘要）：").append(context == null ? "{}" : context.toString()).append("\n");
        return callQwenAPI(sb.toString());
    }

    private String callQwenAPI(String prompt) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_DASHSCOPE_API_KEY")) {
            return "错误：未配置通义千问 API Key。请在 application.properties 中设置 spring.ai.openai.api-key";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            
            Map<String, Object> input = new HashMap<>();
            input.put("messages", new Object[]{
                Map.of("role", "user", "content", prompt)
            });
            requestBody.put("input", input);
            requestBody.put("parameters", Map.of("temperature", 0.7));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(DASHSCOPE_API_URL, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                return "API 调用失败: " + response.getStatusCode() + " - " + response.getBody();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode output = root.path("output");
            // 兼容模式 / Chat：choices[0].message.content
            if (output.has("choices") && output.get("choices").isArray() && output.get("choices").size() > 0) {
                JsonNode choice = output.get("choices").get(0);
                JsonNode message = choice.path("message");
                if (message.has("content")) {
                    return message.get("content").asText();
                }
                if (choice.has("text")) {
                    return choice.get("text").asText();
                }
            }
            // 文本生成同步接口：output.text（generation 端点常见格式）
            if (output.has("text") && !output.get("text").isNull()) {
                return output.get("text").asText();
            }
            return "API 返回格式异常: " + response.getBody();
        } catch (Exception e) {
            return "调用通义千问 API 失败: " + e.getMessage();
        }
    }

    private String buildPrompt(String userGoal) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名军事实战参谋，擅长将用户的作战需求拆解为可执行的想定数据模型。\n");
        sb.append("请围绕下面的系统目标，输出一份\"可落地的建模方案建议\"，供后续在系统中录入：\n");
        sb.append("要求：\n");
        sb.append("1. 只用中文回答，结构清晰，分条列出。\n");
        sb.append("2. 覆盖三个层面：\n");
        sb.append("   2.1 想定数据建模：红蓝双方兵力编成、兵种结构、武器装备与部署地区（可结合典型战例）。\n");
        sb.append("   2.2 作战活动建模：主要作战阶段划分（进攻、防守、侦察等），每个阶段的核心任务与关键时间节点。\n");
        sb.append("   2.3 交互过程建模：双方重要的协同/对抗关系，如火力支援、侧翼攻击、防御协调等，可对应到规则类型和效果类型。\n");
        sb.append("3. 注意结果要尽量贴近日常军事推演的表达方式，避免空洞口号。\n\n");
        sb.append("系统总体目标：\n");
        sb.append("本课题的目标是开发一套作战想定建模系统，系统能够自动化地建模和模拟作战过程，包括兵力部署、武器装备配置、作战活动及交互过程的分析与模拟。系统将基于AI技术帮助用户快速生成作战想定数据，自动定义作战活动及交互过程，并进行模拟，提供直观的战术评估与决策支持，最终提高作战准备和战术执行的效率和精度。\n\n");
        sb.append("用户当前作战目标/背景描述：\n");
        sb.append(userGoal == null ? "（用户暂未填写，给出一套通用模板方案）" : userGoal);
        return sb.toString();
    }
}
