package com.military.combat.simulation.debrief;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 战后简报：规则引擎为主，可选调用 OpenAI 兼容接口（如 DashScope）生成简短叙事。
 */
@Service
public class DebriefService {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(45);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.base-url:}")
    private String openAiBaseUrl;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String chatModel;

    @Value("${combat.debrief.llm.enabled:false}")
    private boolean llmEnabled;

    public DebriefResponse generate(DebriefRequest req) {
        if (req == null) {
            req = new DebriefRequest();
        }
        List<String> bullets = buildRuleBullets(req);
        String narrative;
        String source;
        boolean wantLlm = llmEnabled && openAiApiKey != null && !openAiApiKey.isBlank()
                && openAiBaseUrl != null && !openAiBaseUrl.isBlank();
        if (wantLlm) {
            try {
                narrative = callCompatibleChat(bullets, req).trim();
                if (narrative.isEmpty()) {
                    narrative = joinBullets(bullets);
                    source = "RULES";
                } else {
                    source = "HYBRID";
                }
            } catch (Exception e) {
                narrative = joinBullets(bullets);
                source = "RULES";
            }
        } else {
            narrative = joinBullets(bullets);
            source = "RULES";
        }
        return new DebriefResponse(bullets, narrative, source);
    }

    private static String joinBullets(List<String> bullets) {
        return String.join("\n", bullets);
    }

    private List<String> buildRuleBullets(DebriefRequest req) {
        List<String> out = new ArrayList<>();
        List<DebriefRoundSummary> rounds = req.getRounds() == null ? List.of() : req.getRounds();
        if (rounds.isEmpty()) {
            out.add("暂无回合摘要：请先在仿真页刷新或推进杀伤链以积累归档数据。");
            return out;
        }

        DebriefRoundSummary last = rounds.get(rounds.size() - 1);
        boolean hasStat = last.getStatRound() >= 0 && (last.getRedHp() + last.getBlueHp()) > 0;
        if (!hasStat) {
            out.add("后端回合统计尚未对齐：通常需在完成含 ASSESS 的整回合后刷新，或检查会话与后端连通。");
        }

        double iaLast = last.getInfoAdvantage();
        double ownLast = last.getOwnCombatShare();
        if (iaLast < 0.38 && ownLast < 0.42) {
            out.add("最近摘要：信息优势与己方兵力占比均偏低，宜优先恢复探测/数据链或收缩接敌距离。");
        } else if (iaLast >= 0.55 && ownLast < 0.45) {
            out.add("信息态势尚可但己方战力占比偏低：可能存在火力交换不利或目标优先级不当，建议对照交互事件高峰回合。");
        }

        if (rounds.size() >= 3) {
            double early = avgInfoAdv(rounds.subList(0, Math.min(2, rounds.size())));
            double late = avgInfoAdv(rounds.subList(Math.max(0, rounds.size() - 2), rounds.size()));
            double ownFirst = rounds.get(0).getOwnCombatShare();
            if (late > early + 0.08 && ownLast < ownFirst - 0.06) {
                out.add("信息优势整体走强，但己方战力占比走弱：信息未有效转化为交战成果，可检查 TARGET/ENGAGE 节奏或敌方规避。");
            }
        }

        int intrMax = rounds.stream().mapToInt(DebriefRoundSummary::getInteractionCount).max().orElse(0);
        if (intrMax >= 6 && rounds.size() >= 2) {
            out.add("交互事件较为密集（单回合最多约 " + intrMax + " 条）：建议结合图表中交互强度与己方战力曲线对照战损交换。");
        }

        String side = req.getCommanderSide() == null ? "RED" : req.getCommanderSide().trim().toUpperCase();
        if (hasStat) {
            int rh = last.getRedHp();
            int bh = last.getBlueHp();
            double ratio = "BLUE".equals(side)
                    ? (double) bh / (rh + bh)
                    : (double) rh / (rh + bh);
            if (ratio >= 0.52) {
                out.add("后端血量汇总显示己方仍占一定血量优势（RoundStat），可结合目标完成情况决定是否巩固战果。");
            }
        }

        if (req.getWinner() != null && !req.getWinner().isBlank()) {
            String wr = req.getWinReason() != null ? req.getWinReason() : "";
            out.add("会话胜负：" + req.getWinner().trim() + (wr.isEmpty() ? "" : "（" + wr + "）"));
        }

        if (!out.stream().anyMatch(s -> s.contains("血量优势"))
                && iaLast >= 0.5 && ownLast >= 0.48) {
            out.add("最近回合信息与兵力占比相对均衡：可持续当前杀伤链节奏并观察对方破绽。");
        }

        if (out.size() == 1 && out.get(0).contains("暂无回合摘要")) {
            return out;
        }
        if (out.stream().noneMatch(s -> s.contains("会话胜负"))) {
            out.add("可导出综合复盘包，将 roundArchive 与后端 stats/交互事件交叉核对。");
        }

        while (out.size() > 6) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private static double avgInfoAdv(List<DebriefRoundSummary> slice) {
        if (slice.isEmpty()) {
            return 0;
        }
        double s = 0;
        for (DebriefRoundSummary r : slice) {
            s += r.getInfoAdvantage();
        }
        return s / slice.size();
    }

    private String callCompatibleChat(List<String> bullets, DebriefRequest req) throws Exception {
        String user = buildUserPrompt(bullets, req);
        String body = objectMapper.createObjectNode()
                .put("model", chatModel)
                .set("messages", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("role", "system")
                                .put("content", "你是作战仿真复盘参谋。根据给定结构化要点写一段中文简报，不超过 280 字；不要编造未提供的事实；语气客观。"))
                        .add(objectMapper.createObjectNode()
                                .put("role", "user")
                                .put("content", user)))
                .toString();

        HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        String url = openAiBaseUrl.endsWith("/") ? openAiBaseUrl + "v1/chat/completions" : openAiBaseUrl + "/v1/chat/completions";
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + openAiApiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("LLM HTTP " + resp.statusCode());
        }
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        return content.isMissingNode() ? "" : content.asText("");
    }

    private String buildUserPrompt(List<String> bullets, DebriefRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("指挥侧：").append(req.getCommanderSide()).append("\n");
        if (req.getWinner() != null) {
            sb.append("胜负：").append(req.getWinner());
            if (req.getWinReason() != null && !req.getWinReason().isBlank()) {
                sb.append("，原因：").append(req.getWinReason());
            }
            sb.append("\n");
        }
        sb.append("要点：\n");
        for (String b : bullets) {
            sb.append("- ").append(b).append("\n");
        }
        sb.append("请写一段连贯复盘总结。");
        return sb.toString();
    }
}
