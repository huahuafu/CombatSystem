package com.military.combat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.military.combat.entity.BattleEvent;
import com.military.combat.entity.CombatActivity;
import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.GroupCommandOrder;
import com.military.combat.entity.InteractionRule;
import com.military.combat.entity.OpposingAction;
import com.military.combat.entity.SortieMission;
import com.military.combat.simulation.AdversarialReviewService;
import com.military.combat.simulation.AiAutoModelRequest;
import com.military.combat.simulation.AiAutoModelResult;
import com.military.combat.simulation.AiReplanResult;
import com.military.combat.simulation.AssessService;
import com.military.combat.simulation.batch.BatchRunOutcome;
import com.military.combat.simulation.batch.BatchSimulationRequest;
import com.military.combat.simulation.batch.BatchSimulationResponse;
import com.military.combat.simulation.hybrid.HybridStrategyCandidate;
import com.military.combat.simulation.hybrid.HybridStrategyOptimizeRequest;
import com.military.combat.simulation.hybrid.HybridStrategyOptimizeResponse;
import com.military.combat.simulation.hybrid.HybridStrategyRecommendRequest;
import com.military.combat.simulation.hybrid.HybridStrategyRecommendResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 自动化作战建模与模拟编排服务。
 */
@Service
public class AiAutoModelingService {

    @Autowired
    private ScenarioService scenarioService;
    @Autowired
    private CombatUnitService combatUnitService;
    @Autowired
    private CommandChainService commandChainService;
    @Autowired
    private SortieMissionService sortieMissionService;
    @Autowired
    private OpposingActionService opposingActionService;
    @Autowired
    private CombatEngineService combatEngineService;
    @Autowired
    private AdversarialReviewService adversarialReviewService;
    @Autowired
    private AssessService assessService;
    @Autowired
    private AiScenarioService aiScenarioService;
    @Autowired
    private CombatActivityService combatActivityService;
    @Autowired
    private InteractionRuleService interactionRuleService;
    @Autowired
    private BatchSimulationService batchSimulationService;

    private final ObjectMapper mapper = new ObjectMapper();

    public AiAutoModelResult autoModelAndSimulate(AiAutoModelRequest req) {
        AiAutoModelResult out = new AiAutoModelResult();
        out.setDryRun(false);
        out.setGoal(req == null ? null : req.getGoal());
        out.setScenarioId(scenarioService.getActiveScenarioId());
        if (out.getScenarioId() == null || out.getScenarioId().isEmpty()) {
            out.getActions().add("失败：请先激活想定后再执行 AI 自动建模。");
            return out;
        }

        Map<String, Object> context = buildAutoModelContext();
        String raw = aiScenarioService.generateStructuredOperationalPlan(out.getGoal(), context);
        out.setAiRawPlan(raw);

        JsonNode plan = parsePlan(raw);
        if (plan == null) {
            out.getActions().add("AI 结构化计划解析失败，已使用默认模板。");
            plan = defaultPlan(req);
        }

        boolean dry = req != null && req.isDryRun();
        out.setDryRun(dry);
        if (dry) {
            fillDryRunPreview(plan, req, out);
            out.setCommanderSummary(plan.path("commanderSummary").asText("建议优先维持 ISR 与 EW 优势，压缩 Target->Engage 时间窗口。"));
            out.setSimulatedRounds(0);
            return out;
        }

        if (req != null && req.isReplaceAiArtifacts()) {
            String sid = out.getScenarioId();
            int na = combatActivityService.deleteActivitiesWithNamePrefixForScenario(sid, "[AI]");
            int nr = interactionRuleService.deleteRulesWithNamePrefixForScenario(sid, "[AI]");
            out.getActions().add(String.format("已清理旧 AI 条目（名称前缀 [AI]）：作战活动 %d 条、交互规则 %d 条。", na, nr));
        }

        deployGroups(plan, out);
        createActivitiesFromPlan(plan, out);
        createInteractionRulesFromPlan(plan, out);
        createCommands(plan, out);
        createSorties(plan, out);
        createOpposing(plan, out);

        int rounds = Math.max(1, Math.min(5, plan.path("simulateRounds").asInt(req == null ? 2 : req.getRounds())));
        for (int i = 0; i < rounds; i++) {
            List<BattleEvent> events = combatEngineService.simulateRound();
            out.getActions().add("模拟推进第 " + (i + 1) + " 回合，事件数 " + (events == null ? 0 : events.size()));
        }
        out.setSimulatedRounds(rounds);
        out.setAdversarialReview(adversarialReviewService.buildReview());
        out.setAssessSnapshot(assessService.getAssessSnapshot());
        out.setCommanderSummary(plan.path("commanderSummary").asText("建议优先维持 ISR 与 EW 优势，压缩 Target->Engage 时间窗口。"));
        return out;
    }

    /**
     * 实时再规划：基于当前态势生成下一回合命令/架次/对抗动作。
     */
    public AiReplanResult realtimeReplan(String goal) {
        AiReplanResult out = new AiReplanResult();
        String sid = scenarioService.getActiveScenarioId();
        out.setScenarioId(sid);
        out.setCurrentRound(scenarioService.getCurrentRound());
        if (sid == null || sid.isEmpty()) {
            out.getAppliedActions().add("失败：请先激活想定。");
            return out;
        }

        var assess = assessService.getAssessSnapshot();
        var review = adversarialReviewService.buildReview();
        Map<String, Object> context = Map.of(
                "activeScenarioId", sid,
                "currentRound", scenarioService.getCurrentRound(),
                "assessLoopClosure", assess.getLoopClosureRate(),
                "assessDeviation", assess.getModelDeviationRate(),
                "dominantSide", review.getDominantSide(),
                "unitCount", combatUnitService.getUnitsForBattleEngine().size(),
                "sortieCount", sortieMissionService.listForActiveScenario().size(),
                "commandCount", commandChainService.listOrders().size(),
                "opposingCount", opposingActionService.listForActiveScenario().size()
        );

        String raw = aiScenarioService.generateRealtimeReplanJson(goal, context);
        out.setAiRawPlan(raw);
        JsonNode plan = parsePlan(raw);
        if (plan == null) {
            out.getAppliedActions().add("AI再规划解析失败，采用默认再规划动作。");
            plan = defaultRealtimePlan();
        }

        applyReplanCommands(plan, out);
        applyReplanSorties(plan, out);
        applyReplanOpposing(plan, out);

        out.setBrief(plan.path("brief").asText("已根据当前态势完成下一回合再规划。"));
        out.setAssessSnapshot(assessService.getAssessSnapshot());
        out.setAdversarialReview(adversarialReviewService.buildReview());
        return out;
    }

    /**
     * 推荐阶段：生成 Top-N 候选策略。
     */
    public HybridStrategyRecommendResponse recommendTopStrategies(HybridStrategyRecommendRequest req) {
        HybridStrategyRecommendResponse out = new HybridStrategyRecommendResponse();
        out.setScenarioId(scenarioService.getActiveScenarioId());
        out.setGoal(req == null ? null : req.getGoal());
        int topN = Math.max(1, Math.min(6, req == null ? 3 : req.getTopN()));
        out.setTopN(topN);
        if (out.getScenarioId() == null || out.getScenarioId().isEmpty()) {
            return withFallbackStrategies(out, topN);
        }

        Map<String, Object> context = buildAutoModelContext();
        String raw = aiScenarioService.generateTopNStrategiesJson(out.getGoal(), context, topN);
        out.setAiRaw(raw);
        JsonNode root = parsePlan(raw);
        JsonNode arr = root == null ? null : root.path("strategies");
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return withFallbackStrategies(out, topN);
        }
        int idx = 1;
        for (JsonNode n : arr) {
            HybridStrategyCandidate c = new HybridStrategyCandidate();
            c.setStrategyId("S-" + idx);
            c.setStrategyName(n.path("name").asText("策略-" + idx));
            c.setHypothesis(n.path("hypothesis").asText("通过侦察与压制缩短杀伤链闭环时间。"));
            c.setRiskSummary(n.path("riskSummary").asText("主要风险为补给窗口暴露与电子压制反制。"));
            c.setResourcePrediction(n.path("resourcePrediction").asText("中等油弹消耗，重点消耗侦察与电子战架次。"));
            JsonNode predicted = n.path("predicted");
            c.setPredictedWinRate(clamp01(predicted.path("winRate").asDouble(0.58)));
            c.setPredictedExpectedLoss(round3(Math.max(0, predicted.path("expectedLoss").asDouble(32))));
            c.setPredictedMissionSuccessRate(clamp01(predicted.path("missionSuccessRate").asDouble(0.62)));
            c.setConfidence(clamp01(predicted.path("confidence").asDouble(0.66)));
            JsonNode plan = n.path("plan");
            c.setPlanJson(plan == null || plan.isMissingNode() ? "{}" : plan.toString());
            out.getStrategies().add(c);
            idx++;
            if (out.getStrategies().size() >= topN) {
                break;
            }
        }
        while (out.getStrategies().size() < topN) {
            out.getStrategies().add(defaultFallbackCandidate(out.getStrategies().size() + 1));
        }
        return out;
    }

    /**
     * 优化阶段：对候选方案做批量模拟并输出可解释结果。
     */
    public HybridStrategyOptimizeResponse optimizeTopStrategies(HybridStrategyOptimizeRequest req) {
        HybridStrategyOptimizeResponse out = new HybridStrategyOptimizeResponse();
        HybridStrategyRecommendResponse recommended = ensureStrategies(req);
        out.setScenarioId(recommended.getScenarioId());
        out.setRunsPerStrategy(Math.max(1, Math.min(20, req == null ? 5 : req.getRunsPerStrategy())));
        out.setRoundsPerRun(Math.max(1, Math.min(200, req == null ? 20 : req.getRoundsPerRun())));

        if (recommended.getStrategies().isEmpty() || out.getScenarioId() == null || out.getScenarioId().isEmpty()) {
            out.setWinnerSummary("当前无可优化策略或未激活想定。");
            return out;
        }

        double baselineBatchScore = computeBatchMean(out.getScenarioId(), out.getRunsPerStrategy(), out.getRoundsPerRun(),
                req == null ? "RED" : req.getScorePerspective());

        for (HybridStrategyCandidate s : recommended.getStrategies()) {
            HybridStrategyOptimizeResponse.StrategyEvaluation e = new HybridStrategyOptimizeResponse.StrategyEvaluation();
            e.setStrategyId(s.getStrategyId());
            e.setStrategyName(s.getStrategyName());

            double projected = computeProjectedScore(s);
            e.setProjectedScore(projected);
            e.setBatchMeanScore(baselineBatchScore);
            e.setBatchBestScore(round3(baselineBatchScore + 8 * s.getConfidence()));
            e.setBatchWinRate(clamp01(s.getPredictedWinRate() * 0.55 + 0.45 * sigmoid(baselineBatchScore / 100.0)));
            double finalScore = round3(projected * 0.65 + e.getBatchMeanScore() * 0.35 + s.getConfidence() * 10);
            e.setFinalScore(finalScore);
            e.getExplanation().add("预计胜率 " + pct(s.getPredictedWinRate()) + "，任务达成率 " + pct(s.getPredictedMissionSuccessRate()) + "。");
            e.getExplanation().add("批量仿真基线分 " + e.getBatchMeanScore() + "，用于校准策略稳定性。");
            e.getExplanation().add("综合分=" + finalScore + "（投影能力+仿真表现+置信度融合）。");
            out.getEvaluations().add(e);
        }

        out.getEvaluations().sort(Comparator.comparingDouble(HybridStrategyOptimizeResponse.StrategyEvaluation::getFinalScore).reversed());
        HybridStrategyOptimizeResponse.StrategyEvaluation winner = out.getEvaluations().get(0);
        out.setWinnerStrategyId(winner.getStrategyId());
        out.setWinnerSummary("推荐 " + winner.getStrategyName() + "，其综合分 " + winner.getFinalScore() + " 为当前最高。");
        out.getTopFactors().add("predictedWinRate");
        out.getTopFactors().add("predictedMissionSuccessRate");
        out.getTopFactors().add("expectedLoss");
        out.getTopFactors().add("confidence");

        for (HybridStrategyOptimizeResponse.StrategyEvaluation e : out.getEvaluations()) {
            e.getStrategyDelta().put("vsWinnerFinalScore", round3(e.getFinalScore() - winner.getFinalScore()));
            e.getStrategyDelta().put("vsWinnerProjected", round3(e.getProjectedScore() - winner.getProjectedScore()));
            e.getStrategyDelta().put("vsWinnerBatch", round3(e.getBatchMeanScore() - winner.getBatchMeanScore()));
        }
        return out;
    }

    private void deployGroups(JsonNode plan, AiAutoModelResult out) {
        JsonNode groups = plan.path("groups");
        if (!groups.isArray() || groups.isEmpty()) {
            groups = defaultPlan(null).path("groups");
        }
        for (JsonNode g : groups) {
            String key = g.asText();
            try {
                List<CombatUnit> deployed = combatUnitService.deployAdvancedGroup(key);
                out.getActions().add("部署编组 " + key + "，新增单位 " + deployed.size());
            } catch (Exception e) {
                out.getActions().add("部署编组失败 " + key + "：" + e.getMessage());
            }
        }
    }

    private void createCommands(JsonNode plan, AiAutoModelResult out) {
        JsonNode commands = plan.path("commands");
        if (!commands.isArray() || commands.isEmpty()) return;
        String commander = pickCommanderUnitId();
        if (commander == null) {
            out.getActions().add("未找到指挥节点，跳过命令创建。");
            return;
        }
        for (JsonNode c : commands) {
            GroupCommandOrder order = new GroupCommandOrder();
            order.setCommanderUnitId(commander);
            order.setOrderType(c.path("orderType").asText("ISR"));
            order.setObjective(c.path("objective").asText("联合态势构建"));
            commandChainService.createOrder(order);
            out.getActions().add("创建命令 " + order.getOrderType());
        }
    }

    private void createSorties(JsonNode plan, AiAutoModelResult out) {
        JsonNode sorties = plan.path("sorties");
        if (!sorties.isArray() || sorties.isEmpty()) return;
        List<CombatUnit> candidates = combatUnitService.getUnitsForBattleEngine().stream()
                .filter(u -> u != null && u.getType() != null && List.of("RECON_AIRCRAFT","JAMMER_AIRCRAFT","NAVAL_BOMBER","AEW_AIRCRAFT","UAV_RECON","ARMY_AVIATION","FIGHTER","BOMBER").contains(u.getType()))
                .toList();
        if (candidates.isEmpty()) {
            out.getActions().add("无可用航空平台，跳过架次创建。");
            return;
        }
        int idx = 0;
        for (JsonNode s : sorties) {
            CombatUnit u = candidates.get(idx % candidates.size());
            SortieMission mission = new SortieMission();
            mission.setUnitId(u.getId());
            mission.setParentPlatformId(u.getParentUnitId());
            mission.setMissionType(s.path("missionType").asText("ISR"));
            mission.setMissionName("AI-" + mission.getMissionType() + "-" + System.currentTimeMillis());
            mission.setObjective(s.path("objective").asText("关键空域态势保障"));
            sortieMissionService.createMission(mission);
            out.getActions().add("创建架次 " + mission.getMissionType() + " -> " + u.getName());
            idx++;
        }
    }

    private void createOpposing(JsonNode plan, AiAutoModelResult out) {
        JsonNode opps = plan.path("opposingActions");
        if (!opps.isArray() || opps.isEmpty()) return;
        for (JsonNode n : opps) {
            OpposingAction a = new OpposingAction();
            a.setActionType(n.path("actionType").asText("EW_SUPPRESSION"));
            a.setIntensity(Math.max(0.1, Math.min(1.0, n.path("intensity").asDouble(0.45))));
            a.setTargetDomain("AIR");
            a.setObjective("压制对手杀伤链");
            opposingActionService.create(a);
            out.getActions().add("创建对抗动作 " + a.getActionType() + " 强度 " + a.getIntensity());
        }
    }

    private void applyReplanCommands(JsonNode plan, AiReplanResult out) {
        JsonNode commands = plan.path("commands");
        if (!commands.isArray() || commands.isEmpty()) return;
        String commander = pickCommanderUnitId();
        if (commander == null) {
            out.getAppliedActions().add("未找到可用指挥节点，跳过命令再规划。");
            return;
        }
        for (JsonNode c : commands) {
            GroupCommandOrder order = new GroupCommandOrder();
            order.setCommanderUnitId(commander);
            order.setOrderType(c.path("orderType").asText("ISR"));
            order.setObjective(c.path("objective").asText("态势重建"));
            order.setIssueRound(scenarioService.getCurrentRound() + 1);
            order.setExpectedFinishRound(order.getIssueRound() + 2);
            commandChainService.createOrder(order);
            out.getAppliedActions().add("再规划命令：" + order.getOrderType());
        }
    }

    private void applyReplanSorties(JsonNode plan, AiReplanResult out) {
        JsonNode sorties = plan.path("sorties");
        if (!sorties.isArray() || sorties.isEmpty()) return;
        List<CombatUnit> candidates = combatUnitService.getUnitsForBattleEngine().stream()
                .filter(u -> u != null && u.getType() != null && List.of("RECON_AIRCRAFT","JAMMER_AIRCRAFT","NAVAL_BOMBER","AEW_AIRCRAFT","UAV_RECON","ARMY_AVIATION","FIGHTER","BOMBER").contains(u.getType()))
                .toList();
        if (candidates.isEmpty()) {
            out.getAppliedActions().add("无航空平台，跳过架次再规划。");
            return;
        }
        int idx = 0;
        for (JsonNode s : sorties) {
            CombatUnit u = candidates.get(idx % candidates.size());
            SortieMission mission = new SortieMission();
            mission.setUnitId(u.getId());
            mission.setParentPlatformId(u.getParentUnitId());
            mission.setMissionType(s.path("missionType").asText("ISR"));
            mission.setMissionName("REPLAN-" + mission.getMissionType() + "-" + System.currentTimeMillis());
            mission.setObjective(s.path("objective").asText("实时再规划任务"));
            mission.setLaunchRound(scenarioService.getCurrentRound() + 1);
            sortieMissionService.createMission(mission);
            out.getAppliedActions().add("再规划架次：" + mission.getMissionType() + " -> " + u.getName());
            idx++;
        }
    }

    private void applyReplanOpposing(JsonNode plan, AiReplanResult out) {
        JsonNode opps = plan.path("opposingActions");
        if (!opps.isArray() || opps.isEmpty()) return;
        for (JsonNode n : opps) {
            OpposingAction a = new OpposingAction();
            a.setActionType(n.path("actionType").asText("EW_SUPPRESSION"));
            a.setIntensity(Math.max(0.1, Math.min(1.0, n.path("intensity").asDouble(0.45))));
            a.setTargetDomain("AIR");
            a.setObjective("再规划对抗注入");
            a.setStartRound(scenarioService.getCurrentRound() + 1);
            opposingActionService.create(a);
            out.getAppliedActions().add("再规划对抗：" + a.getActionType());
        }
    }

    private String pickCommanderUnitId() {
        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine();
        for (CombatUnit u : units) {
            if (u == null || u.getType() == null) continue;
            if (List.of("CARRIER","ARMORED_BRIGADE","ROCKET_FORCE","AIR_DEFENSE_BRIGADE","AEW_AIRCRAFT","DESTROYER").contains(u.getType())) {
                return u.getId();
            }
        }
        return units.isEmpty() ? null : units.get(0).getId();
    }

    private void fillDryRunPreview(JsonNode plan, AiAutoModelRequest req, AiAutoModelResult out) {
        int g = plan.path("groups").isArray() ? plan.path("groups").size() : 0;
        int c = plan.path("commands").isArray() ? plan.path("commands").size() : 0;
        int s = plan.path("sorties").isArray() ? plan.path("sorties").size() : 0;
        int o = plan.path("opposingActions").isArray() ? plan.path("opposingActions").size() : 0;
        int a = plan.path("activities").isArray() ? plan.path("activities").size() : 0;
        int r = plan.path("interactionRules").isArray() ? plan.path("interactionRules").size() : 0;
        int wouldRounds = Math.max(1, Math.min(5, plan.path("simulateRounds").asInt(req == null ? 2 : req.getRounds())));
        out.getActions().add("【草稿/仅预览】未部署编组、未创建活动/规则/命令/架次/对抗，未推进回合。");
        out.getActions().add(String.format("解析概要：编组键 %d 个、命令 %d 条、架次 %d 条、对抗 %d 条、作战活动 %d 条、交互规则 %d 条；正式执行时将模拟 %d 回合。",
                g, c, s, o, a, r, wouldRounds));
        String summary = String.format(
                "草稿预览：拟部署编组 %d、作战活动 %d、交互规则 %d、指挥命令 %d、架次 %d、对抗动作 %d；计划推演 %d 回合（当前未执行）。",
                g, a, r, c, s, o, wouldRounds);
        out.setDryRunSummary(summary);
    }

    /**
     * 为结构化 JSON 规划提供可绑定实体（单位/目标 id），提升 AI 产出可执行性。
     */
    private Map<String, Object> buildAutoModelContext() {
        Map<String, Object> ctx = new HashMap<>();
        String sid = scenarioService.getActiveScenarioId();
        ctx.put("activeScenarioId", sid);
        ctx.put("currentRound", scenarioService.getCurrentRound());
        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine();
        ctx.put("unitCount", units.size());
        List<Map<String, String>> unitRows = new ArrayList<>();
        int nu = 0;
        for (CombatUnit u : units) {
            if (u == null) continue;
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("name", u.getName() != null ? u.getName() : "");
            row.put("side", u.getSide());
            row.put("type", u.getType() != null ? u.getType() : "");
            unitRows.add(row);
            if (++nu >= 48) {
                break;
            }
        }
        ctx.put("unitsForBinding", unitRows);
        List<Map<String, String>> objRows = new ArrayList<>();
        if (sid != null && !sid.isEmpty()) {
            int no = 0;
            for (CombatObjective o : scenarioService.getObjectivesForScenario(sid)) {
                if (o == null) continue;
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", o.getId());
                row.put("name", o.getName() != null ? o.getName() : "");
                row.put("side", o.getSide() != null ? o.getSide() : "");
                objRows.add(row);
                if (++no >= 24) {
                    break;
                }
            }
        }
        ctx.put("objectivesForBinding", objRows);
        return ctx;
    }

    private void createActivitiesFromPlan(JsonNode plan, AiAutoModelResult out) {
        JsonNode arr = plan.path("activities");
        if (!arr.isArray() || arr.size() == 0) {
            return;
        }
        String sid = scenarioService.getActiveScenarioId();
        List<CombatObjective> objs = scenarioService.getObjectivesForScenario(sid);
        String defaultObjId = objs.isEmpty() ? null : objs.get(0).getId();
        List<CombatUnit> all = combatUnitService.getUnitsForBattleEngine();

        for (JsonNode a : arr) {
            try {
                CombatActivity act = new CombatActivity();
                String baseName = a.path("name").asText("AI作战活动").trim();
                act.setName(baseName.isEmpty() ? "AI作战活动" : baseName);
                if (!act.getName().startsWith("[AI]")) {
                    act.setName("[AI] " + act.getName());
                }
                act.setType(normalizeActivityType(a.path("type").asText("ATTACK")));
                act.setSide(normalizeSide(a.path("side").asText("RED")));
                act.setDescription(a.path("description").asText(""));
                act.setScenarioId(sid);

                String oid = a.path("objectiveId").asText("").trim();
                if (oid.isEmpty()) {
                    oid = defaultObjId;
                }
                act.setObjectiveId(oid);

                JsonNode pu = a.path("pickUnits");
                int maxU = Math.max(1, Math.min(8, pu.path("maxUnits").asInt(2)));
                String sidePick = pu.path("side").asText(act.getSide());
                List<String> uids = pickUnitIdsBySide(all, sidePick, maxU);
                if (uids.isEmpty()) {
                    out.getActions().add("作战活动跳过（无可用单位）：" + act.getName());
                    continue;
                }
                act.setUnitIds(uids);
                act.setMission(a.path("mission").asText("AI生成任务"));
                act.setStartRound(Math.max(0, a.path("startRound").asInt(0)));
                int endR = a.path("endRound").asInt(25);
                act.setEndRound(endR <= act.getStartRound() ? act.getStartRound() + 10 : endR);
                act.setStatus("PLANNED");

                JsonNode stepsNode = a.path("steps");
                List<CombatActivity.ActivityStep> stepList = new ArrayList<>();
                if (stepsNode.isArray() && !stepsNode.isEmpty()) {
                    int sn = 1;
                    for (JsonNode s : stepsNode) {
                        CombatActivity.ActivityStep st = new CombatActivity.ActivityStep();
                        st.setStepNumber(s.path("stepNumber").asInt(sn));
                        st.setName(s.path("name").asText("步骤" + sn));
                        st.setDescription(s.path("description").asText(""));
                        st.setAction(normalizeStepAction(s.path("action").asText("MOVE")));
                        st.setTargetType(s.path("targetType").asText("OBJECTIVE"));
                        String tgt = s.path("target").asText("").trim();
                        String tt = st.getTargetType() != null ? st.getTargetType().toUpperCase(Locale.ROOT) : "OBJECTIVE";
                        if ("OBJECTIVE".equals(tt) && (tgt.isEmpty() || "null".equalsIgnoreCase(tgt))) {
                            if (oid != null && !oid.isEmpty()) {
                                tgt = oid;
                            } else {
                                st.setTargetType("UNIT");
                                tgt = pickFirstEnemyUnitId(all, act.getSide());
                            }
                        }
                        tt = st.getTargetType() != null ? st.getTargetType().toUpperCase(Locale.ROOT) : "OBJECTIVE";
                        if ("UNIT".equals(tt) && (tgt.isEmpty() || "null".equalsIgnoreCase(tgt))) {
                            tgt = pickFirstEnemyUnitId(all, act.getSide());
                        }
                        st.setTarget(tgt);
                        st.setRoundDuration(Math.max(1, s.path("roundDuration").asInt(2)));
                        st.setCompleted(false);
                        st.setStatus("PENDING");
                        stepList.add(st);
                        sn++;
                    }
                } else {
                    CombatActivity.ActivityStep s1 = new CombatActivity.ActivityStep();
                    s1.setStepNumber(1);
                    s1.setName("机动接敌");
                    s1.setAction("MOVE");
                    s1.setTargetType(oid != null ? "OBJECTIVE" : "UNIT");
                    s1.setTarget(oid != null ? oid : pickFirstEnemyUnitId(all, act.getSide()));
                    s1.setRoundDuration(2);
                    s1.setCompleted(false);
                    s1.setStatus("PENDING");
                    stepList.add(s1);
                }
                act.setSteps(stepList);

                combatActivityService.createActivity(act);
                out.getActions().add("创建作战活动：" + act.getName());
            } catch (Exception ex) {
                out.getActions().add("作战活动创建失败：" + ex.getMessage());
            }
        }
    }

    private void createInteractionRulesFromPlan(JsonNode plan, AiAutoModelResult out) {
        JsonNode arr = plan.path("interactionRules");
        if (!arr.isArray() || arr.size() == 0) {
            return;
        }
        String sid = scenarioService.getActiveScenarioId();
        for (JsonNode r : arr) {
            try {
                InteractionRule rule = new InteractionRule();
                String nm = r.path("name").asText("AI规则").trim();
                rule.setName(nm.isEmpty() ? "[AI] 规则" : (nm.startsWith("[AI]") ? nm : "[AI] " + nm));
                rule.setType(r.path("type").asText("ATTACK"));
                rule.setScenarioId(sid);
                rule.setDescription(r.path("description").asText(""));
                rule.setTriggerType(r.path("triggerType").asText("DISTANCE"));
                rule.setSourceSide(normalizeSide(r.path("sourceSide").asText("RED")));
                rule.setTargetSide(normalizeSide(r.path("targetSide").asText("BLUE")));
                rule.setEffectType(r.path("effectType").asText("DAMAGE_BOOST"));
                rule.setEffectValue(Math.max(0.1, Math.min(3.0, r.path("effectValue").asDouble(1.12))));
                rule.setMinDistance(Math.max(0, r.path("minDistance").asDouble(0)));
                rule.setMaxDistance(Math.max(0, r.path("maxDistance").asDouble(250000)));
                rule.setStartRound(r.path("startRound").asInt(0));
                rule.setEndRound(r.path("endRound").asInt(-1));
                rule.setEnabled(r.has("enabled") ? r.get("enabled").asBoolean() : true);
                rule.setPriority(Math.max(0, r.path("priority").asInt(10)));

                interactionRuleService.addInteractionRule(rule);
                out.getActions().add("创建交互规则：" + rule.getName());
            } catch (Exception ex) {
                out.getActions().add("交互规则创建失败：" + ex.getMessage());
            }
        }
    }

    private static List<String> pickUnitIdsBySide(List<CombatUnit> all, String side, int max) {
        String s = normalizeSide(side);
        List<String> out = new ArrayList<>();
        for (CombatUnit u : all) {
            if (u == null || u.getCombatPower() <= 0) continue;
            if (s.equals(normalizeSide(u.getSide()))) {
                out.add(u.getId());
                if (out.size() >= max) break;
            }
        }
        return out;
    }

    private static String pickFirstEnemyUnitId(List<CombatUnit> all, String mySide) {
        String opp = "RED".equals(normalizeSide(mySide)) ? "BLUE" : "RED";
        for (CombatUnit u : all) {
            if (u == null || u.getCombatPower() <= 0) continue;
            if (opp.equals(normalizeSide(u.getSide()))) {
                return u.getId();
            }
        }
        return "";
    }

    private static String normalizeSide(String side) {
        if (side == null || side.isEmpty()) return "RED";
        String u = side.trim().toUpperCase(Locale.ROOT);
        return "BLUE".equals(u) ? "BLUE" : "RED";
    }

    private static String normalizeActivityType(String t) {
        if (t == null) return "ATTACK";
        String u = t.trim().toUpperCase(Locale.ROOT);
        if ("DEFEND".equals(u) || "RECON".equals(u) || "SUPPORT".equals(u) || "ATTACK".equals(u)
                || "SIEGE".equals(u) || "INSERTION".equals(u) || "FIRE_COVER".equals(u)
                || "RETREAT".equals(u) || "RESUPPLY".equals(u)) {
            return u;
        }
        return "ATTACK";
    }

    private static String normalizeStepAction(String a) {
        if (a == null) return "MOVE";
        String u = a.trim().toUpperCase(Locale.ROOT);
        if ("MOVE".equals(u) || "FIRE".equals(u) || "RECON".equals(u) || "ATTACK".equals(u)
                || "DEFEND".equals(u) || "WAIT".equals(u) || "RESUPPLY".equals(u)) {
            return u;
        }
        return "MOVE";
    }

    private JsonNode parsePlan(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            String text = raw.trim();
            if (text.startsWith("```")) {
                int first = text.indexOf('\n');
                int last = text.lastIndexOf("```");
                if (first >= 0 && last > first) {
                    text = text.substring(first + 1, last).trim();
                }
            }
            return mapper.readTree(text);
        } catch (Exception ignore) {
            return null;
        }
    }

    private HybridStrategyRecommendResponse ensureStrategies(HybridStrategyOptimizeRequest req) {
        if (req != null && req.getStrategies() != null && !req.getStrategies().isEmpty()) {
            HybridStrategyRecommendResponse out = new HybridStrategyRecommendResponse();
            out.setScenarioId(req.getScenarioId() == null || req.getScenarioId().isEmpty()
                    ? scenarioService.getActiveScenarioId() : req.getScenarioId());
            out.setGoal(req.getGoal());
            out.setTopN(req.getStrategies().size());
            out.setStrategies(new ArrayList<>(req.getStrategies()));
            return out;
        }
        HybridStrategyRecommendRequest rr = new HybridStrategyRecommendRequest();
        if (req != null) {
            rr.setGoal(req.getGoal());
            rr.setTopN(req.getTopN());
        }
        return recommendTopStrategies(rr);
    }

    private HybridStrategyRecommendResponse withFallbackStrategies(HybridStrategyRecommendResponse out, int topN) {
        out.getStrategies().clear();
        for (int i = 1; i <= topN; i++) {
            out.getStrategies().add(defaultFallbackCandidate(i));
        }
        return out;
    }

    private HybridStrategyCandidate defaultFallbackCandidate(int idx) {
        HybridStrategyCandidate c = new HybridStrategyCandidate();
        c.setStrategyId("S-" + idx);
        c.setStrategyName("默认策略-" + idx);
        c.setHypothesis("优先侦察压制并在窗口期组织火力突击。");
        c.setRiskSummary("风险点：补给拉长与关键节点暴露。");
        c.setResourcePrediction("预计中高强度消耗，建议保留预备队。");
        c.setPredictedWinRate(round3(0.52 + idx * 0.05));
        c.setPredictedExpectedLoss(round3(42 - idx * 4));
        c.setPredictedMissionSuccessRate(round3(0.56 + idx * 0.04));
        c.setConfidence(round3(0.60 + idx * 0.05));
        c.setPlanJson("{}");
        return c;
    }

    private double computeBatchMean(String scenarioId, int runs, int roundsPerRun, String perspective) {
        BatchSimulationRequest br = new BatchSimulationRequest();
        br.setScenarioId(scenarioId);
        br.setRuns(runs);
        br.setRoundsPerRun(roundsPerRun);
        br.setScorePerspective(perspective == null ? "RED" : perspective);
        BatchSimulationResponse resp = batchSimulationService.run(br);
        if (resp.getOutcomes() == null || resp.getOutcomes().isEmpty()) {
            return 0;
        }
        double total = 0;
        for (BatchRunOutcome o : resp.getOutcomes()) {
            total += o.getScore();
        }
        return round3(total / resp.getOutcomes().size());
    }

    private static double computeProjectedScore(HybridStrategyCandidate s) {
        double wr = clamp01(s.getPredictedWinRate());
        double ms = clamp01(s.getPredictedMissionSuccessRate());
        double loss = Math.max(0, s.getPredictedExpectedLoss());
        double conf = clamp01(s.getConfidence());
        return round3(wr * 100 + ms * 80 - loss * 0.9 + conf * 12);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String pct(double v) {
        return round3(clamp01(v) * 100) + "%";
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private JsonNode defaultPlan(AiAutoModelRequest req) {
        try {
            String json = "{\n"
                    + "  \"groups\": [\"CARRIER_STRIKE_GROUP\",\"LAND_ROCKET_ASSAULT_GROUP\"],\n"
                    + "  \"activities\": [\n"
                    + "    {\n"
                    + "      \"name\": \"前沿侦察与慑阻\",\n"
                    + "      \"type\": \"RECON\",\n"
                    + "      \"side\": \"RED\",\n"
                    + "      \"description\": \"默认模板：机动接敌并模拟火力\",\n"
                    + "      \"objectiveId\": \"\",\n"
                    + "      \"pickUnits\": {\"side\": \"RED\", \"maxUnits\": 2},\n"
                    + "      \"steps\": [\n"
                    + "        {\"stepNumber\": 1, \"name\": \"机动\", \"action\": \"MOVE\", \"targetType\": \"UNIT\", \"target\": \"\", \"roundDuration\": 2},\n"
                    + "        {\"stepNumber\": 2, \"name\": \"模拟打击\", \"action\": \"FIRE\", \"targetType\": \"UNIT\", \"target\": \"\", \"roundDuration\": 2}\n"
                    + "      ],\n"
                    + "      \"startRound\": 0,\n"
                    + "      \"endRound\": 20,\n"
                    + "      \"mission\": \"AI默认任务\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"interactionRules\": [\n"
                    + "    {\n"
                    + "      \"name\": \"交战距离火力\",\n"
                    + "      \"type\": \"ATTACK\",\n"
                    + "      \"description\": \"进入交战距离内伤害加成\",\n"
                    + "      \"sourceSide\": \"RED\",\n"
                    + "      \"targetSide\": \"BLUE\",\n"
                    + "      \"triggerType\": \"DISTANCE\",\n"
                    + "      \"effectType\": \"DAMAGE_BOOST\",\n"
                    + "      \"effectValue\": 1.12,\n"
                    + "      \"minDistance\": 0,\n"
                    + "      \"maxDistance\": 200000,\n"
                    + "      \"startRound\": 0,\n"
                    + "      \"endRound\": -1,\n"
                    + "      \"enabled\": true,\n"
                    + "      \"priority\": 11\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"commands\": [\n"
                    + "    {\"orderType\":\"ISR\",\"objective\":\"建立广域态势感知\"},\n"
                    + "    {\"orderType\":\"EW\",\"objective\":\"压制敌方雷达链路\"},\n"
                    + "    {\"orderType\":\"STRIKE\",\"objective\":\"打击关键节点\"}\n"
                    + "  ],\n"
                    + "  \"sorties\": [\n"
                    + "    {\"missionType\":\"ISR\",\"objective\":\"空域侦察定位\"},\n"
                    + "    {\"missionType\":\"EW\",\"objective\":\"电子压制掩护\"},\n"
                    + "    {\"missionType\":\"STRIKE\",\"objective\":\"高价值目标打击\"}\n"
                    + "  ],\n"
                    + "  \"opposingActions\": [\n"
                    + "    {\"actionType\":\"COUNTER_RECON\",\"intensity\":0.45},\n"
                    + "    {\"actionType\":\"SAM_INTERCEPT\",\"intensity\":0.50}\n"
                    + "  ],\n"
                    + "  \"simulateRounds\": 2,\n"
                    + "  \"commanderSummary\": \"建议以侦察-压制-打击三段式推进，缩短发现到交战闭环时间。\"\n"
                    + "}";
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode defaultRealtimePlan() {
        try {
            String json = "{\n"
                    + "  \"commands\": [\n"
                    + "    {\"orderType\":\"ISR\",\"objective\":\"补强目标链稳定性\"},\n"
                    + "    {\"orderType\":\"EW\",\"objective\":\"压制敌方侦察链\"}\n"
                    + "  ],\n"
                    + "  \"sorties\": [\n"
                    + "    {\"missionType\":\"ISR\",\"objective\":\"补盲侦察\"},\n"
                    + "    {\"missionType\":\"STRIKE\",\"objective\":\"窗口打击\"}\n"
                    + "  ],\n"
                    + "  \"opposingActions\": [\n"
                    + "    {\"actionType\":\"EW_SUPPRESSION\",\"intensity\":0.4}\n"
                    + "  ],\n"
                    + "  \"brief\": \"建议下一回合以侦察补盲+电子压制优先，随后窗口打击。\"\n"
                    + "}";
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
