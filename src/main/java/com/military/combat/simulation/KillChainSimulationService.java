package com.military.combat.simulation;

import com.military.combat.entity.BattleEvent;
import com.military.combat.entity.InteractionEvent;
import com.military.combat.repository.InteractionEventRepository;
import com.military.combat.service.CombatEngineService;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 按 Find/Fix/Track/Target/Engage/Assess 六阶段串行推进一轮仿真。
 */
@Service
public class KillChainSimulationService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatEngineService combatEngineService;

    @Autowired
    private FindService findService;

    @Autowired
    private FixService fixService;

    @Autowired
    private TrackService trackService;

    @Autowired
    private TargetService targetService;

    @Autowired
    private AssessService assessService;

    @Autowired
    private KillChainAssessmentService killChainAssessmentService;

    @Autowired
    private InteractionEventRepository interactionEventRepository;

    public KillChainRunResult runOneRoundByKillChain() {
        KillChainRunResult out = new KillChainRunResult();
        out.setScenarioId(scenarioService.getActiveScenarioId());
        List<BattleEvent> all = new ArrayList<>();
        for (KillChainPhase ph : KillChainPhase.values()) {
            out.getExecutedPhases().add(ph.name());
            all.addAll(combatEngineService.simulateKillChainSegment(ph.ordinal()));
        }
        scenarioService.setKillChainSequentialStep(0);
        out.setRound(scenarioService.getCurrentRound());
        fillAggregateMetrics(out);
        List<BattleEvent> events = enrichBattleEvents(all, out);
        out.setBattleEvents(events);
        out.setEngageEvents(events == null ? 0 : events.size());
        out.setAssessment(killChainAssessmentService.getForActiveScenario());
        return out;
    }

    /**
     * 按会话内顺序每次只推进杀伤链的一个阶段（FIND→…→ASSESS），完成 ASSESS 后进入下一推演回合且片段序号归零。
     */
    public KillChainRunResult advanceSequentialKillChainStep() {
        KillChainRunResult out = new KillChainRunResult();
        out.setScenarioId(scenarioService.getActiveScenarioId());
        if (scenarioService.getWinner() != null && !scenarioService.getWinner().isEmpty()) {
            out.setRound(scenarioService.getCurrentRound());
            out.setNextSequentialStep(scenarioService.getKillChainSequentialStep());
            BattleEvent endEvent = new BattleEvent();
            endEvent.setSource("SYSTEM");
            endEvent.setTarget("SYSTEM");
            endEvent.setAction("SIMULATION_ALREADY_DECIDED");
            endEvent.setSide("SYSTEM");
            endEvent.setMessage("推演已决出胜负：" + scenarioService.getWinner() + "（" + scenarioService.getWinReason() + "）");
            out.setBattleEvents(enrichBattleEvents(List.of(endEvent), out));
            return out;
        }

        int step = scenarioService.getKillChainSequentialStep();
        KillChainPhase phase = KillChainPhase.values()[step];
        out.getExecutedPhases().add(phase.name());
        out.setExecutedPhase(phase.name());

        List<BattleEvent> segmentEvents = combatEngineService.simulateKillChainSegment(step);
        int next = (step + 1) % 6;
        scenarioService.setKillChainSequentialStep(next);
        out.setNextSequentialStep(next);
        out.setRound(scenarioService.getCurrentRound());
        fillAggregateMetrics(out);
        out.setBattleEvents(enrichBattleEvents(segmentEvents, out));
        out.setEngageEvents(segmentEvents == null ? 0 : segmentEvents.size());
        out.setAssessment(killChainAssessmentService.getForActiveScenario());
        return out;
    }

    private void fillAggregateMetrics(KillChainRunResult out) {
        out.setFindContacts(findService.getFindSnapshot().getDetectionsThisRound());
        FixSnapshot fix = fixService.getFixSnapshot();
        out.setFixTargets(Math.max(0, fix.getFixedTargetCount()));
        TrackSnapshot track = trackService.getTrackSnapshot();
        out.setTrackTargets(Math.max(0, track.getTrackedTargetCount()));
        TargetSnapshot target = targetService.getTargetSnapshot();
        out.setTargetSolutions(Math.max(0, target.getFireSolutionCount()));
        AssessSnapshot assess = assessService.getAssessSnapshot();
        if (assess != null) {
            out.setAssessReadiness(assess.getReadinessLevel());
            out.setLoopClosureRate(assess.getLoopClosureRate());
        }
    }

    /**
     * 胜负已决时仅返回一条系统事件，补充杀伤链前序摘要与当回合交互事件，便于战报区展示多行条目。
     */
    private List<BattleEvent> enrichBattleEvents(List<BattleEvent> raw, KillChainRunResult out) {
        List<BattleEvent> base = raw == null ? new ArrayList<>() : new ArrayList<>(raw);
        if (base.size() == 1 && base.get(0) != null
                && "SIMULATION_ALREADY_DECIDED".equals(base.get(0).getAction())) {
            List<BattleEvent> merged = new ArrayList<>();
            merged.add(sysEvent("KILL_CHAIN_PREFACE_FIND", "杀伤链前序（本操作）：发现环节完成，接触报告 " + out.getFindContacts() + " 条。"));
            merged.add(sysEvent("KILL_CHAIN_PREFACE_FIX", "杀伤链前序：定位融合完成，融合目标 " + out.getFixTargets() + " 个。"));
            merged.add(sysEvent("KILL_CHAIN_PREFACE_TRACK", "杀伤链前序：跟踪环节完成，稳定跟踪 " + out.getTrackTargets() + " 个。"));
            merged.add(sysEvent("KILL_CHAIN_PREFACE_TARGET", "杀伤链前序：瞄准环节完成，火力解 " + out.getTargetSolutions() + " 个。"));
            appendInteractionStrikeLines(merged, scenarioService.getCurrentRound(), 24, true);
            merged.addAll(base);
            merged.add(sysEvent("KILL_CHAIN_POSTFACE", "交战引擎已冻结；若需重新推演，请在指挥端「重置想定」或再次载入测试想定。"));
            return merged;
        }
        appendInteractionStrikeLines(base, scenarioService.getCurrentRound(), 18, false);
        return base;
    }

    private static BattleEvent sysEvent(String action, String message) {
        BattleEvent e = new BattleEvent();
        e.setSource("SYSTEM");
        e.setTarget("SYSTEM");
        e.setAction(action);
        e.setSide("SYSTEM");
        e.setMessage(message);
        return e;
    }

    private void appendInteractionStrikeLines(List<BattleEvent> dest, int round, int max, boolean placeholderWhenEmpty) {
        if (max <= 0 || round < 0) {
            return;
        }
        List<InteractionEvent> all = interactionEventRepository.findAll();
        List<InteractionEvent> hits = all.stream()
                .filter(e -> e != null && e.getRound() == round)
                .sorted(Comparator.comparingLong(InteractionEvent::getTimestamp))
                .collect(Collectors.toList());
        if (hits.size() > max) {
            hits = hits.subList(hits.size() - max, hits.size());
        }
        for (InteractionEvent e : hits) {
            String src = e.getSourceUnitName() != null ? e.getSourceUnitName() : e.getSourceUnitId();
            String tgt = e.getTargetUnitName() != null ? e.getTargetUnitName() : e.getTargetUnitId();
            String rule = e.getRuleName() != null ? e.getRuleName() : e.getType();
            String msg = "交战交互「" + rule + "」：" + src + " → " + tgt
                    + "，结果 " + (e.getResult() != null ? e.getResult() : "—");
            if (e.getDescription() != null && !e.getDescription().isEmpty()) {
                msg += "（" + e.getDescription() + "）";
            }
            dest.add(sysEvent("INTERACTION_STRIKE", msg));
        }
        if (hits.isEmpty() && placeholderWhenEmpty) {
            dest.add(sysEvent("INTERACTION_STRIKE", "本回合（第 " + round + " 回合）暂无已持久化的交互交战事件记录。"));
        }
    }
}
