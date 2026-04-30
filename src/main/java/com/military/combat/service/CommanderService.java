package com.military.combat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.military.combat.entity.CombatUnit;
import com.military.combat.repository.CommanderRunRepository;
import com.military.combat.simulation.KillChainAssessment;
import com.military.combat.simulation.KillChainRunResult;
import com.military.combat.simulation.KillChainSimulationService;
import com.military.combat.simulation.commander.*;
import com.military.combat.simulation.hybrid.HybridStrategyCandidate;
import com.military.combat.simulation.hybrid.HybridStrategyOptimizeRequest;
import com.military.combat.simulation.hybrid.HybridStrategyOptimizeResponse;
import com.military.combat.simulation.hybrid.HybridStrategyRecommendRequest;
import com.military.combat.simulation.hybrid.HybridStrategyRecommendResponse;
import com.military.combat.util.NavalDomainValidator;
import com.military.combat.util.KillChainActionValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

@Service
public class CommanderService {

    @Autowired
    private ScenarioActivationService scenarioActivationService;
    @Autowired
    private ScenarioService scenarioService;
    @Autowired
    private AiAutoModelingService aiAutoModelingService;
    @Autowired
    private AiScenarioService aiScenarioService;
    @Autowired
    private KillChainSimulationService killChainSimulationService;
    @Autowired
    private CombatUnitService combatUnitService;
    @Autowired
    private CommanderRunRepository commanderRunRepository;
    @Autowired
    private FastApiStrategyClient fastApiStrategyClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String ENGINE_VERSION_FALLBACK = "0.0.1-SNAPSHOT";

    public CommanderGenerateResponse generate(CommanderGenerateRequest req) {
        CommanderGenerateRequest r = req == null ? new CommanderGenerateRequest() : req;
        String requestId = UUID.randomUUID().toString();

        String scenarioId = (r.getScenarioId() == null || r.getScenarioId().isEmpty())
                ? scenarioService.getActiveScenarioId()
                : r.getScenarioId().trim();
        if (scenarioId == null || scenarioId.isEmpty()) {
            throw new IllegalArgumentException("未指定 scenarioId 且当前无激活想定。");
        }
        scenarioActivationService.activateScenario(scenarioId);

        String goal = r.getGoal() == null ? "" : r.getGoal().trim();
        if (r.getSeed() != null) {
            scenarioService.setSimulationSeed(r.getSeed());
        }
        r.setScenarioId(scenarioId);
        r.setGoal(goal);

        CommanderGenerateResponse fastApiResponse = fastApiStrategyClient.tryGenerate(r);
        if (fastApiResponse != null) {
            if (fastApiResponse.getRequestId() == null || fastApiResponse.getRequestId().isEmpty()) {
                fastApiResponse.setRequestId(requestId);
            }
            if (fastApiResponse.getScenarioId() == null || fastApiResponse.getScenarioId().isEmpty()) {
                fastApiResponse.setScenarioId(scenarioId);
            }
            fastApiResponse.getNotes().add("strategy source: fastapi");
            return fastApiResponse;
        }

        HybridStrategyRecommendRequest recommendReq = new HybridStrategyRecommendRequest();
        recommendReq.setGoal(goal);
        recommendReq.setTopN(4);
        HybridStrategyRecommendResponse recommended = aiAutoModelingService.recommendTopStrategies(recommendReq);

        HybridStrategyOptimizeRequest optimizeReq = new HybridStrategyOptimizeRequest();
        optimizeReq.setScenarioId(scenarioId);
        optimizeReq.setGoal(goal);
        optimizeReq.setTopN(4);
        optimizeReq.setRunsPerStrategy(clamp(r.getRunsPerStrategy(), 1, 20));
        optimizeReq.setRoundsPerRun(clamp(r.getRoundsPerRun(), 1, 200));
        optimizeReq.setScorePerspective(normalizePerspective(r.getScorePerspective()));
        optimizeReq.setStrategies(recommended.getStrategies());
        HybridStrategyOptimizeResponse optimized = aiAutoModelingService.optimizeTopStrategies(optimizeReq);

        List<HybridStrategyCandidate> merged = recommended.getStrategies() == null ? List.of() : recommended.getStrategies();
        CommanderStrategyCard win = pickWin(scenarioId, goal, merged, optimized);
        CommanderStrategyCard loss = pickLoss(scenarioId, goal, merged, optimized, win);
        CommanderStrategyCard speed = pickSpeed(scenarioId, goal, merged, optimized, win, loss);
        CommanderStrategyCard balanced = pickBalanced(scenarioId, goal, merged, optimized, win, loss, speed);

        CommanderGenerateResponse out = new CommanderGenerateResponse();
        out.setRequestId(requestId);
        out.setScenarioId(scenarioId);
        out.setGoal(goal);
        out.setSeed(r.getSeed());
        if (win != null) out.getStrategies().add(win);
        if (loss != null) out.getStrategies().add(loss);
        if (speed != null) out.getStrategies().add(speed);
        if (balanced != null) out.getStrategies().add(balanced);
        if (out.getStrategies().size() != 4) {
            throw new IllegalArgumentException("STRATEGY_SCHEMA_INVALID: 四类策略生成不完整");
        }
        out.getStrategies().forEach(CommanderStrategyValidator::validate);
        out.getNotes().add("四策略语义固定：WIN_MAX/LOSS_MIN/SPEED_MAX/BALANCED。");
        out.getNotes().add("六阶段杀伤链：Find/Fix/Track/Target/Engage/Assess 将用于推演与战报分段。");
        out.getNotes().add("strategy source: java-fallback");
        return out;
    }

    public CommanderExecuteResponse execute(CommanderExecuteRequest req) {
        return executeInternal(req, true);
    }

    public CommanderExecuteResponse replayFromRecord(CommanderRunRecord record, Integer roundsOverride, Long seedOverride, boolean persist) {
        if (record == null) {
            throw new IllegalArgumentException("run 记录不存在");
        }
        CommanderExecuteRequest req = new CommanderExecuteRequest();
        // persist=false：仅复现结果；persist=true：作为“新 run”落库，避免覆盖旧记录
        req.setRequestId(persist ? UUID.randomUUID().toString() : record.getRequestId());
        req.setScenarioId(record.getScenarioId());
        req.setGoal(record.getGoal());
        req.setLabel(record.getLabel());
        req.setStrategyId(record.getStrategyId());
        req.setRounds(roundsOverride == null ? record.getRoundsRequested() : roundsOverride);
        req.setSeed(seedOverride == null ? record.getSeed() : seedOverride);
        req.setScorePerspective("RED");
        return executeInternal(req, persist);
    }

    private CommanderExecuteResponse executeInternal(CommanderExecuteRequest req, boolean persistRunRecord) {
        if (req == null) {
            throw new IllegalArgumentException("请求为空");
        }
        String scenarioId = (req.getScenarioId() == null || req.getScenarioId().isEmpty())
                ? scenarioService.getActiveScenarioId()
                : req.getScenarioId().trim();
        if (scenarioId == null || scenarioId.isEmpty()) {
            throw new IllegalArgumentException("未指定 scenarioId 且当前无激活想定。");
        }
        scenarioActivationService.activateScenario(scenarioId);

        String requestId = (req.getRequestId() == null || req.getRequestId().isEmpty())
                ? UUID.randomUUID().toString()
                : req.getRequestId().trim();

        long seed = (req.getSeed() != null) ? req.getSeed()
                : (System.currentTimeMillis() ^ (requestId.hashCode() * 2654435761L));
        scenarioService.setSimulationSeed(seed);

        CommanderStrategyLabel planLabel = req.getLabel();
        if (req.getStrategy() != null) {
            CommanderStrategyValidator.validate(req.getStrategy());
            if (planLabel == null) {
                planLabel = req.getStrategy().getLabel();
            }
        }
        if (planLabel == null) {
            throw new IllegalArgumentException("PLAN_TYPE_MISSING: 执行前必须指定 label 或 strategy.label");
        }
        planLabel = normalizeLabel(planLabel);
        // 执行前注入任务偏好，使推演走向差异化
        applyStrategyBias(planLabel);

        CommanderBattleReport report = new CommanderBattleReport();
        List<KillChainRunResult> rounds = new ArrayList<>();
        int nRounds = clamp(req.getRounds(), 1, 60);
        for (int i = 0; i < nRounds; i++) {
            KillChainRunResult rr = killChainSimulationService.runOneRoundByKillChain();
            rounds.add(rr);
            appendPhaseReports(report, rr);
        }

        KillChainAssessment assess = rounds.isEmpty() ? null : rounds.get(rounds.size() - 1).getAssessment();
        if (assess != null) {
            report.getExplanations().add("杀伤链成熟度 " + pct(assess.getOverallMaturity()) + "（越高代表闭环越稳定）。");
        }

        // 判胜信息
        report.setWinner(scenarioService.getWinner());
        report.setWinReason(scenarioService.getWinReason());

        // 聚合六阶段最新快照（用于前端看板）
        report.setStageSnapshots(buildLatestStageSnapshots(report.getPhaseReports()));

        // 轻量战损/任务达成：用当前单位存活与目标完成数近似
        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine();
        int redAlive = (int) units.stream().filter(u -> u != null && u.getCombatPower() > 0 && "RED".equalsIgnoreCase(u.getSide())).count();
        int blueAlive = (int) units.stream().filter(u -> u != null && u.getCombatPower() > 0 && "BLUE".equalsIgnoreCase(u.getSide())).count();
        report.getExplanations().add(String.format(Locale.ROOT, "当前存活：RED %d，BLUE %d。", redAlive, blueAlive));

        CommanderExecuteResponse out = new CommanderExecuteResponse();
        out.setRequestId(requestId);
        out.setScenarioId(scenarioId);
        out.setLabel(planLabel);
        out.setStrategyId(req.getStrategyId());
        out.setSeed(seed);
        out.setReport(report);

        if (persistRunRecord) {
            CommanderRunRecord record = new CommanderRunRecord();
            record.setRequestId(requestId);
            record.setScenarioId(scenarioId);
            record.setGoal(req.getGoal());
            record.setLabel(planLabel);
            record.setStrategyId(req.getStrategyId());
            record.setCreatedAt(System.currentTimeMillis());
            record.setSeed(seed);
            record.setEngineVersion(resolveEngineVersion());
            record.setRoundsRequested(nRounds);
            record.setReport(report);
            record.setRounds(rounds);
            commanderRunRepository.save(record);
        }

        return out;
    }

    private String resolveEngineVersion() {
        try {
            String v = CommanderService.class.getPackage().getImplementationVersion();
            return (v == null || v.isEmpty()) ? ENGINE_VERSION_FALLBACK : v;
        } catch (Exception e) {
            return ENGINE_VERSION_FALLBACK;
        }
    }

    private List<KillChainStageSnapshotV3> buildLatestStageSnapshots(List<CommanderPhaseReport> phases) {
        List<KillChainStageSnapshotV3> out = new ArrayList<>();
        if (phases == null || phases.isEmpty()) {
            return out;
        }
        for (KillChainStageKey key : KillChainStageKey.values()) {
            CommanderPhaseReport latest = phases.stream()
                    .filter(p -> p != null && key == p.getStage())
                    .max(Comparator.comparingInt(CommanderPhaseReport::getRound))
                    .orElse(null);
            if (latest != null && latest.getSnapshot() != null) {
                KillChainStageSnapshotV3 s = latest.getSnapshot();
                s.setStage(key);
                out.add(s);
            }
        }
        return out;
    }

    public CommanderRunRecord getRun(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return null;
        }
        return commanderRunRepository.findById(requestId).orElse(null);
    }

    public List<CommanderRunRecord> listRecentRuns(String scenarioId, int limit) {
        int lim = clamp(limit, 1, 100);
        if (scenarioId == null || scenarioId.isEmpty()) {
            return commanderRunRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, lim));
        }
        return commanderRunRepository.findByScenarioIdOrderByCreatedAtDesc(scenarioId, PageRequest.of(0, lim));
    }

    private void appendPhaseReports(CommanderBattleReport report, KillChainRunResult rr) {
        if (rr == null) {
            return;
        }
        int round = rr.getRound();
        // FIND
        report.getPhaseReports().add(phase(round, KillChainStageKey.FIND, metric("contacts", rr.getFindContacts()),
                "Find：形成接触 " + rr.getFindContacts() + " 条。"));
        // FIX
        report.getPhaseReports().add(phase(round, KillChainStageKey.FIX, metric("fixedTargets", rr.getFixTargets()),
                "Fix：融合定位 " + rr.getFixTargets() + " 个目标。"));
        // TRACK
        report.getPhaseReports().add(phase(round, KillChainStageKey.TRACK, metric("trackedTargets", rr.getTrackTargets()),
                "Track：稳定跟踪 " + rr.getTrackTargets() + " 个目标。"));
        // TARGET
        report.getPhaseReports().add(phase(round, KillChainStageKey.TARGET, metric("fireSolutions", rr.getTargetSolutions()),
                "Target：形成火力解 " + rr.getTargetSolutions() + " 个。"));
        // ENGAGE（附带本回合战斗事件）
        CommanderPhaseReport engage = phase(round, KillChainStageKey.ENGAGE, metric("battleEvents", rr.getEngageEvents()),
                "Engage：战斗事件 " + rr.getEngageEvents() + " 条。");
        if (rr.getBattleEvents() != null) {
            engage.setEvents(new ArrayList<>(rr.getBattleEvents()));
        }
        report.getPhaseReports().add(engage);
        // ASSESS
        KillChainStageSnapshotV3 assessSnap = metric("loopClosureRate", rr.getLoopClosureRate());
        assessSnap.getMetrics().put("readiness", rr.getAssessReadiness());
        report.getPhaseReports().add(phase(round, KillChainStageKey.ASSESS, assessSnap,
                "Assess：闭环完成度 " + pct(rr.getLoopClosureRate()) + "。"));
    }

    private static CommanderPhaseReport phase(int round, KillChainStageKey stage, KillChainStageSnapshotV3 snapshot, String sysMessage) {
        CommanderPhaseReport p = new CommanderPhaseReport();
        p.setRound(round);
        p.setStage(stage);
        p.setSnapshot(snapshot);
        if (sysMessage != null && !sysMessage.isEmpty()) {
            var ev = new com.military.combat.entity.BattleEvent();
            ev.setSource("SYSTEM");
            ev.setTarget("SYSTEM");
            ev.setSide("SYSTEM");
            ev.setAction("KILL_CHAIN_PHASE_" + stage.name());
            ev.setMessage(sysMessage);
            p.getEvents().add(ev);
        }
        return p;
    }

    private static KillChainStageSnapshotV3 metric(String key, Object value) {
        KillChainStageSnapshotV3 s = new KillChainStageSnapshotV3();
        s.setStatus("READY");
        s.getMetrics().put(key, value);
        return s;
    }

    private void applyStrategyBias(CommanderStrategyLabel label) {
        label = normalizeLabel(label);
        if (label == null) {
            return;
        }
        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine();
        if (units == null || units.isEmpty()) {
            return;
        }
        for (CombatUnit u : units) {
            if (u == null || u.getCombatPower() <= 0) {
                continue;
            }
            if (!"SEA".equalsIgnoreCase(u.getDomain())) {
                continue;
            }
            // 以现有 CombatEngineService 的海上行为规则为准：mission/navalTaskType 会触发不同效果
            if (label == CommanderStrategyLabel.LOSS_MIN) {
                // 保守：防空、撤离重组、保持供给
                u.setMission("AIR_DEFENSE|WITHDRAW_REORGANIZE");
                u.setNavalTaskType("AIR_DEFENSE");
                u.setReadinessLevel(Math.min(1.0, Math.max(0.65, u.getReadinessLevel())));
            } else if (label == CommanderStrategyLabel.WIN_MAX) {
                // 激进：火力分配/打击/封锁
                u.setMission("FIRE_ALLOCATION|STRIKE|BLOCKADE");
                u.setNavalTaskType("STRIKE");
                u.setReadinessLevel(Math.min(1.0, Math.max(0.75, u.getReadinessLevel())));
            } else if (label == CommanderStrategyLabel.SPEED_MAX) {
                u.setMission("ROUTE_MANEUVER|STRIKE|ESCORT");
                u.setNavalTaskType("ROUTE_MANEUVER");
                u.setReadinessLevel(Math.min(1.0, Math.max(0.8, u.getReadinessLevel())));
            } else if (label == CommanderStrategyLabel.BALANCED) {
                // 均衡：护航与区域控制
                u.setMission("ESCORT|ROUTE_MANEUVER|AIR_DEFENSE");
                u.setNavalTaskType("ESCORT");
                u.setReadinessLevel(Math.min(1.0, Math.max(0.7, u.getReadinessLevel())));
            }
            NavalDomainValidator.normalizeAndValidateUnit(u);
            combatUnitService.updateUnit(u);
        }
    }

    private CommanderStrategyCard pickWin(String scenarioId, String goal, List<HybridStrategyCandidate> list, HybridStrategyOptimizeResponse optimized) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        HybridStrategyCandidate best = list.stream()
                .max(Comparator.comparingDouble(HybridStrategyCandidate::getPredictedWinRate))
                .orElse(list.get(0));
        return toCard(CommanderStrategyLabel.WIN_MAX, scenarioId, goal, best, optimized);
    }

    private CommanderStrategyCard pickLoss(String scenarioId, String goal, List<HybridStrategyCandidate> list, HybridStrategyOptimizeResponse optimized, CommanderStrategyCard win) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        String avoid = win == null ? null : win.getStrategyId();
        return list.stream()
                .filter(x -> avoid == null || !avoid.equals(x.getStrategyId()))
                .min(Comparator.comparingDouble(HybridStrategyCandidate::getPredictedExpectedLoss))
                .map(x -> toCard(CommanderStrategyLabel.LOSS_MIN, scenarioId, goal, x, optimized))
                .orElse(null);
    }

    private CommanderStrategyCard pickSpeed(String scenarioId, String goal, List<HybridStrategyCandidate> list, HybridStrategyOptimizeResponse optimized, CommanderStrategyCard win, CommanderStrategyCard loss) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        String avoidA = win == null ? null : win.getStrategyId();
        String avoidB = loss == null ? null : loss.getStrategyId();
        List<HybridStrategyCandidate> remain = list.stream()
                .filter(x -> (avoidA == null || !avoidA.equals(x.getStrategyId())) && (avoidB == null || !avoidB.equals(x.getStrategyId())))
                .toList();
        List<HybridStrategyCandidate> candidates = remain.isEmpty() ? list : remain;
        HybridStrategyCandidate best = candidates.stream()
                .max(Comparator.comparingDouble(x -> (x.getPredictedMissionSuccessRate() * 0.65 + x.getPredictedWinRate() * 0.35)))
                .orElse(candidates.get(0));
        return toCard(CommanderStrategyLabel.SPEED_MAX, scenarioId, goal, best, optimized);
    }

    private CommanderStrategyCard pickBalanced(String scenarioId, String goal, List<HybridStrategyCandidate> list, HybridStrategyOptimizeResponse optimized, CommanderStrategyCard win, CommanderStrategyCard loss, CommanderStrategyCard speed) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        String avoidA = win == null ? null : win.getStrategyId();
        String avoidB = loss == null ? null : loss.getStrategyId();
        String avoidC = speed == null ? null : speed.getStrategyId();
        List<HybridStrategyCandidate> remain = list.stream()
                .filter(x -> (avoidA == null || !avoidA.equals(x.getStrategyId()))
                        && (avoidB == null || !avoidB.equals(x.getStrategyId()))
                        && (avoidC == null || !avoidC.equals(x.getStrategyId())))
                .toList();
        List<HybridStrategyCandidate> candidates = remain.isEmpty() ? list : remain;
        double maxLoss = candidates.stream().mapToDouble(HybridStrategyCandidate::getPredictedExpectedLoss).max().orElse(1.0);
        HybridStrategyCandidate best = candidates.stream()
                .max(Comparator.comparingDouble(x -> (x.getPredictedWinRate() * 0.4 + x.getPredictedMissionSuccessRate() * 0.35 + (1.0 - x.getPredictedExpectedLoss() / maxLoss) * 0.25)))
                .orElse(candidates.get(0));
        return toCard(CommanderStrategyLabel.BALANCED, scenarioId, goal, best, optimized);
    }

    private CommanderStrategyCard toCard(CommanderStrategyLabel label, String scenarioId, String goal, HybridStrategyCandidate s, HybridStrategyOptimizeResponse optimized) {
        CommanderStrategyCard c = new CommanderStrategyCard();
        c.setLabel(label);
        c.setScenarioId(scenarioId);
        c.setGoal(goal);
        c.setStrategyId(s.getStrategyId());
        c.setStrategyName(s.getStrategyName());
        c.setHypothesis(s.getHypothesis());
        c.setRiskSummary(s.getRiskSummary());
        c.setPredictedWinRate(s.getPredictedWinRate());
        c.setPredictedExpectedLoss(s.getPredictedExpectedLoss());
        c.setPredictedMissionSuccessRate(s.getPredictedMissionSuccessRate());
        c.setConfidence(s.getConfidence());
        List<KillChainStageAction> fallback = defaultActionsFor(label);
        List<KillChainStageAction> aiActions = tryGenerateActionsByAi(goal, label, fallback);
        c.getStageActions().addAll(KillChainActionValidator.safeActionsOrFallback(aiActions, fallback));
        c.getDeploymentPlan().addAll(defaultDeploymentPlan(label));
        c.getRoutePlan().addAll(defaultRoutePlan(label));
        c.getSequencePlan().addAll(defaultSequencePlan(label));
        c.getTempoPlan().addAll(defaultTempoPlan(label));
        c.setIntentProsCons(defaultIntent(label));
        c.getExplanation().add("假设：" + safe(s.getHypothesis()));
        c.getExplanation().add("风险：" + safe(s.getRiskSummary()));
        if (optimized != null && optimized.getEvaluations() != null) {
            optimized.getEvaluations().stream()
                    .filter(e -> e != null && s.getStrategyId().equals(e.getStrategyId()))
                    .findFirst()
                    .ifPresent(e -> c.getExplanation().addAll(e.getExplanation() == null ? List.of() : e.getExplanation()));
        }
        return c;
    }

    private List<KillChainStageAction> tryGenerateActionsByAi(String goal, CommanderStrategyLabel label, List<KillChainStageAction> fallback) {
        try {
            // 复用已有“结构化上下文”能力：AiAutoModelingService 内部 buildAutoModelContext 是 private，
            // 这里采用轻量上下文（不阻塞）：只传入基本信息，让 AI 输出动作包。
            String raw = aiScenarioService.generateKillChainStageActionsJson(goal, java.util.Map.of(
                    "activeScenarioId", scenarioService.getActiveScenarioId(),
                    "currentRound", scenarioService.getCurrentRound(),
                    "unitCount", combatUnitService.getUnitsForBattleEngine().size()
            ), label.name());
            if (raw == null || raw.isEmpty() || raw.startsWith("错误：") || raw.startsWith("API 调用失败")) {
                return fallback;
            }
            JsonNode root = parseJson(raw);
            JsonNode arr = root == null ? null : root.path("stageActions");
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                return fallback;
            }
            List<KillChainStageAction> out = new ArrayList<>();
            for (JsonNode n : arr) {
                KillChainStageAction a = new KillChainStageAction();
                String stage = n.path("stage").asText("");
                try {
                    a.setStage(KillChainStageKey.valueOf(stage.trim().toUpperCase(Locale.ROOT)));
                } catch (Exception ignore) {
                    continue;
                }
                a.setActionType(n.path("actionType").asText("ACTION"));
                a.setTitle(n.path("title").asText(""));
                a.setDetail(n.path("detail").asText(""));
                a.setIntensity(Math.max(0, Math.min(1, n.path("intensity").asDouble(0.6))));
                out.add(a);
            }
            return out.isEmpty() ? fallback : out;
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private JsonNode parseJson(String raw) {
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
        } catch (Exception e) {
            return null;
        }
    }

    private List<KillChainStageAction> defaultActionsFor(CommanderStrategyLabel label) {
        label = normalizeLabel(label);
        List<KillChainStageAction> out = new ArrayList<>();
        out.add(action(KillChainStageKey.FIND, "SCAN", "广域搜索", label == CommanderStrategyLabel.WIN_MAX ? "扩大搜索扇区，优先发现高价值目标。" : "保持稳定搜索覆盖，避免暴露。", label == CommanderStrategyLabel.WIN_MAX ? 0.85 : 0.65));
        out.add(action(KillChainStageKey.FIX, "FUSION", "融合定位", "提高多源融合权重，缩短定位时间。", label == CommanderStrategyLabel.LOSS_MIN ? 0.75 : 0.82));
        out.add(action(KillChainStageKey.TRACK, "MAINTAIN", "保持跟踪", label == CommanderStrategyLabel.LOSS_MIN ? "优先稳定跟踪，降低丢轨风险。" : "以窗口期为导向维护跟踪。", label == CommanderStrategyLabel.LOSS_MIN ? 0.82 : 0.7));
        out.add(action(KillChainStageKey.TARGET, "ALLOCATE", "火力分配", label == CommanderStrategyLabel.WIN_MAX ? "优先任务目标打击，集中优势火力。" : "分配更保守，保留预备队。", label == CommanderStrategyLabel.WIN_MAX ? 0.88 : 0.66));
        out.add(action(KillChainStageKey.ENGAGE, "ENGAGE", "交战执行", label == CommanderStrategyLabel.LOSS_MIN ? "严格交战窗口与撤离条件，控制战损。" : "抓住窗口期快速交战，换取任务达成。", label == CommanderStrategyLabel.LOSS_MIN ? 0.6 : (label == CommanderStrategyLabel.SPEED_MAX ? 0.9 : 0.82)));
        out.add(action(KillChainStageKey.ASSESS, "ASSESS", "战果评估", "按闭环完成度与战损增量复盘，必要时触发再规划。", 0.75));
        return out;
    }

    private static KillChainStageAction action(KillChainStageKey stage, String type, String title, String detail, double intensity) {
        KillChainStageAction a = new KillChainStageAction();
        a.setStage(stage);
        a.setActionType(type);
        a.setTitle(title);
        a.setDetail(detail);
        a.setIntensity(Math.max(0, Math.min(1, intensity)));
        return a;
    }

    private List<DeploymentItem> defaultDeploymentPlan(CommanderStrategyLabel label) {
        label = normalizeLabel(label);
        List<DeploymentItem> out = new ArrayList<>();
        out.add(deploy("DESTROYER", 2, "RED", 22.81, 121.35, 65, "前沿海域压制"));
        out.add(deploy("FRIGATE", 2, "RED", 22.75, 121.28, 40, label == CommanderStrategyLabel.LOSS_MIN ? "区域防空护航" : "伴随突击"));
        out.add(deploy("UAV_RECON", 1, "RED", 22.88, 121.22, 85, "持续侦察与目标指示"));
        return out;
    }

    private List<RouteItem> defaultRoutePlan(CommanderStrategyLabel label) {
        label = normalizeLabel(label);
        List<RouteItem> out = new ArrayList<>();
        RouteItem r1 = new RouteItem();
        r1.setRouteId("R-ALPHA");
        r1.setForUnitType("DESTROYER");
        r1.setPhase("PHASE_1");
        r1.setTrigger("侦察确认目标暴露");
        r1.getWaypoints().add(waypoint(22.81, 121.35));
        r1.getWaypoints().add(waypoint(22.9, 121.42));
        r1.getWaypoints().add(waypoint(23.0, 121.48));
        out.add(r1);

        RouteItem r2 = new RouteItem();
        r2.setRouteId("R-BRAVO");
        r2.setForUnitType("FRIGATE");
        r2.setPhase("PHASE_2");
        r2.setTrigger(label == CommanderStrategyLabel.SPEED_MAX ? "主攻编队进入接敌区即启动" : "主攻进入交战前沿后启动");
        r2.getWaypoints().add(waypoint(22.75, 121.28));
        r2.getWaypoints().add(waypoint(22.84, 121.36));
        r2.getWaypoints().add(waypoint(22.95, 121.41));
        out.add(r2);
        return out;
    }

    private List<SequenceStep> defaultSequencePlan(CommanderStrategyLabel label) {
        label = normalizeLabel(label);
        List<SequenceStep> out = new ArrayList<>();
        out.add(sequence(1, "情报搜索与电磁压制", "UAV_RECON", "敌前沿侦搜节点", "HIGH", null));
        out.add(sequence(2, "主攻火力打击", "DESTROYER", "敌关键据点", "HIGH", "step-1"));
        out.add(sequence(3, label == CommanderStrategyLabel.LOSS_MIN ? "建立防空保护圈" : "快速推进控制区", "FRIGATE", "主攻编队侧翼", "MEDIUM", "step-2"));
        return out;
    }

    private List<TempoNode> defaultTempoPlan(CommanderStrategyLabel label) {
        label = normalizeLabel(label);
        List<TempoNode> out = new ArrayList<>();
        out.add(tempo(0, "编队展开", label == CommanderStrategyLabel.SPEED_MAX ? "FAST" : "MEDIUM", "前出单元进入任务海域"));
        out.add(tempo(10, "压制与突击", label == CommanderStrategyLabel.LOSS_MIN ? "MEDIUM" : "FAST", "压制敌前沿火力并形成突破口"));
        out.add(tempo(20, "稳控与评估", label == CommanderStrategyLabel.WIN_MAX ? "FAST" : "MEDIUM", "巩固控制区并评估下一阶段行动"));
        return out;
    }

    private IntentProsCons defaultIntent(CommanderStrategyLabel label) {
        label = normalizeLabel(label);
        IntentProsCons d = new IntentProsCons();
        if (label == CommanderStrategyLabel.WIN_MAX) {
            d.setIntent("集中火力快速夺控关键节点，优先确保任务胜率。");
            d.getPros().add("局部优势形成速度快");
            d.getPros().add("目标达成概率高");
            d.getCons().add("持续消耗较高");
            d.getCons().add("侧翼暴露风险上升");
        } else if (label == CommanderStrategyLabel.LOSS_MIN) {
            d.setIntent("以保全兵力为先，采用弹性防御与机会打击。");
            d.getPros().add("战损可控");
            d.getPros().add("可持续作战能力强");
            d.getCons().add("推进速度较慢");
            d.getCons().add("窗口期利用不足");
        } else if (label == CommanderStrategyLabel.SPEED_MAX) {
            d.setIntent("高节奏机动突进，抢占战场关键时机。");
            d.getPros().add("推进与夺控速度最快");
            d.getPros().add("可迅速打乱敌方部署");
            d.getCons().add("补给与协同压力增大");
            d.getCons().add("局部过伸风险明显");
        } else {
            d.setIntent("在胜率、战损与节奏之间保持均衡，追求综合最优。");
            d.getPros().add("风险收益平衡");
            d.getPros().add("适应复杂态势能力较好");
            d.getCons().add("极端场景下上限不突出");
            d.getCons().add("需要更高指挥协同质量");
        }
        return d;
    }

    private static DeploymentItem deploy(String unitType, int unitCount, String side, double lat, double lng, int facing, String task) {
        DeploymentItem d = new DeploymentItem();
        d.setUnitType(unitType);
        d.setUnitCount(unitCount);
        d.setSide(side);
        d.setLatitude(lat);
        d.setLongitude(lng);
        d.setFacing(facing);
        d.setTask(task);
        return d;
    }

    private static RouteWaypoint waypoint(double lat, double lng) {
        RouteWaypoint w = new RouteWaypoint();
        w.setLatitude(lat);
        w.setLongitude(lng);
        return w;
    }

    private static SequenceStep sequence(int step, String action, String actor, String target, String priority, String dependency) {
        SequenceStep s = new SequenceStep();
        s.setStep(step);
        s.setAction(action);
        s.setActor(actor);
        s.setTarget(target);
        s.setPriority(priority);
        s.setDependency(dependency);
        return s;
    }

    private static TempoNode tempo(int tPlusMin, String milestone, String pace, String expectedOutcome) {
        TempoNode t = new TempoNode();
        t.setTPlusMin(tPlusMin);
        t.setMilestone(milestone);
        t.setPace(pace);
        t.setExpectedOutcome(expectedOutcome);
        return t;
    }

    private CommanderStrategyLabel normalizeLabel(CommanderStrategyLabel label) {
        if (label == null) {
            return null;
        }
        if (label == CommanderStrategyLabel.A_LOSS_MIN) {
            return CommanderStrategyLabel.LOSS_MIN;
        }
        if (label == CommanderStrategyLabel.B_WINRATE_MAX) {
            return CommanderStrategyLabel.WIN_MAX;
        }
        if (label == CommanderStrategyLabel.C_BALANCED) {
            return CommanderStrategyLabel.BALANCED;
        }
        return label;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String normalizePerspective(String p) { return "BLUE".equalsIgnoreCase(p) ? "BLUE" : "RED"; }

    private static String pct(double v) { return Math.round(Math.max(0, Math.min(1, v)) * 1000.0) / 10.0 + "%"; }

    private static String safe(String s) { return s == null ? "" : s; }
}

