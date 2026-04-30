package com.military.combat.service;

import com.military.combat.entity.*;
import com.military.combat.repository.InteractionEventRepository;
import com.military.combat.simulation.FindService;
import com.military.combat.simulation.FixService;
import com.military.combat.simulation.TrackService;
import com.military.combat.simulation.TargetService;
import com.military.combat.simulation.EngageService;
import com.military.combat.simulation.AssessService;
import com.military.combat.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.military.combat.simulation.random.SimulationRandom;
import java.util.SplittableRandom;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CombatEngineService {

    @Autowired
    private CombatUnitService unitService;

    @Autowired
    private CombatActivityService activityService;

    @Autowired
    private CoordinationService coordinationService;

    @Autowired
    private InteractionProcessService interactionProcessService;

    @Autowired
    private InteractionRuleService ruleService;

    @Autowired
    private TerrainService terrainService;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private InteractionEventRepository interactionEventRepository;

    @Autowired
    private CombatObjectiveDirectiveService objectiveDirectiveService;

    @Autowired
    private FindService findService;

    @Autowired
    private FixService fixService;

    @Autowired
    private TrackService trackService;

    @Autowired
    private TargetService targetService;

    @Autowired
    private EngageService engageService;

    @Autowired
    private AssessService assessService;

    @Autowired
    private SortieMissionService sortieMissionService;

    @Autowired
    private CommandChainService commandChainService;

    @Autowired
    private OpposingActionService opposingActionService;

    @Autowired
    private SimulationRandom simulationRandom;

    /**
     * 模拟一个回合的战斗。
     * <p>本回合若有作战活动：编入活动的单位由活动步骤推进；未编入的单位仍按作战目标与默认战术行动。
     * 当前战场单位集与「当前想定」内嵌兵力部署一致（见 {@link CombatUnitService#getUnitsForBattleEngine()}）。</p>
     */
    public List<BattleEvent> simulateRound() {
        if (scenarioService.getWinner() != null && !scenarioService.getWinner().isEmpty()) {
            BattleEvent endEvent = new BattleEvent();
            endEvent.setSource("SYSTEM");
            endEvent.setTarget("SYSTEM");
            endEvent.setAction("SIMULATION_ALREADY_DECIDED");
            endEvent.setSide("SYSTEM");
            endEvent.setMessage("推演已决出胜负：" + scenarioService.getWinner() + "（" + scenarioService.getWinReason() + "）");
            return List.of(endEvent);
        }
        List<CombatUnit> activeUnits = unitService.getUnitsForBattleEngine().stream()
                .filter(u -> u.getCombatPower() > 0)
                .collect(Collectors.toList());

        TurnContext ctx = new TurnContext(scenarioService.getCurrentRound(), activeUnits);
        runTurnPipeline(ctx);
        scenarioService.setCurrentRound(ctx.currentRound + 1);
        return ctx.events;
    }

    private void runTurnPipeline(TurnContext ctx) {
        announceKillChainDoctrine(ctx);
        // 以六阶段作战链条驱动回合执行（而非仅前端看板展示）
        announcePhaseStart(ctx, "FIND", "发现");
        // 先推进对抗/指挥/架次状态，再进入六环节评估，避免“评估滞后一个回合”
        stageOpposingExecution(ctx);
        stageCommandChainExecution(ctx);
        stageSortieExecution(ctx);
        stageNavalCommandExecution(ctx);
        stageFindExecution(ctx);

        announcePhaseStart(ctx, "FIX", "定位");
        stageFixExecution(ctx);

        announcePhaseStart(ctx, "TRACK", "跟踪");
        stageTrackExecution(ctx);

        announcePhaseStart(ctx, "TARGET", "瞄准");
        stageTargetExecution(ctx);

        stageCampaignCheck(ctx);
        stageActivityExecution(ctx);
        stageCoordinationExecution(ctx);
        stageInteractionProcess(ctx);
        stageAutonomousCombat(ctx);

        // 交战与评估必须放在战斗执行之后，保证读取到本回合最新战果
        announcePhaseStart(ctx, "ENGAGE", "交战");
        stageEngageExecution(ctx);
        announcePhaseStart(ctx, "ASSESS", "评估");
        stageAssessExecution(ctx);
        stageVictoryCheck(ctx);
        stageRecordStat(ctx);
    }

    private void announceKillChainDoctrine(TurnContext ctx) {
        BattleEvent event = new BattleEvent();
        event.setSource("SYSTEM");
        event.setTarget("SYSTEM");
        event.setAction("DOCTRINE_US_KILL_CHAIN");
        event.setSide("SYSTEM");
        event.setMessage("本回合作战采用六阶段杀伤链驱动：发现-定位-跟踪-瞄准-交战-评估。");
        ctx.events.add(event);
    }

    private void announcePhaseStart(TurnContext ctx, String phaseKey, String phaseName) {
        BattleEvent event = new BattleEvent();
        event.setSource("SYSTEM");
        event.setTarget("SYSTEM");
        event.setAction("KILL_CHAIN_PHASE_" + phaseKey);
        event.setSide("SYSTEM");
        event.setMessage("进入阶段：" + phaseName + "（" + phaseKey + "）");
        ctx.events.add(event);
    }

    private void stageFindExecution(TurnContext ctx) {
        int detected = findService.runFindScanForCurrentRound().size();
        BattleEvent findEvent = new BattleEvent();
        findEvent.setSource("SYSTEM");
        findEvent.setTarget("SYSTEM");
        findEvent.setAction("FIND_SCAN");
        findEvent.setSide("SYSTEM");
        findEvent.setMessage("Find环节扫描完成：本回合形成接触报告 " + detected + " 条。");
        ctx.events.add(findEvent);
    }

    private void stageFixExecution(TurnContext ctx) {
        var fix = fixService.runFusionForCurrentRound();
        BattleEvent fixEvent = new BattleEvent();
        fixEvent.setSource("SYSTEM");
        fixEvent.setTarget("SYSTEM");
        fixEvent.setAction("FIX_FUSION");
        fixEvent.setSide("SYSTEM");
        fixEvent.setMessage("Fix环节融合完成：候选 " + fix.getCandidateCount() + " / 融合 " + fix.getFusedTargetCount()
                + " / 可移交 " + fix.getFixedTargetCount());
        ctx.events.add(fixEvent);
    }

    private void stageTrackExecution(TurnContext ctx) {
        var track = trackService.runTrackForCurrentRound();
        BattleEvent trackEvent = new BattleEvent();
        trackEvent.setSource("SYSTEM");
        trackEvent.setTarget("SYSTEM");
        trackEvent.setAction("TRACK_RUN");
        trackEvent.setSide("SYSTEM");
        trackEvent.setMessage("Track环节执行：移交 " + track.getHandoffTargetCount() + " / 稳定跟踪 "
                + track.getTrackedTargetCount() + " / 丢轨率 " + (int) Math.round(track.getTrackLossRate() * 100) + "%");
        ctx.events.add(trackEvent);
    }

    private void stageTargetExecution(TurnContext ctx) {
        var target = targetService.runTargetingForCurrentRound();
        BattleEvent targetEvent = new BattleEvent();
        targetEvent.setSource("SYSTEM");
        targetEvent.setTarget("SYSTEM");
        targetEvent.setAction("TARGET_PLAN");
        targetEvent.setSide("SYSTEM");
        targetEvent.setMessage("Target环节完成：候选 " + target.getTrackedCandidateCount() + " / 分配 "
                + target.getTargetAssignedCount() + " / 火力解 " + target.getFireSolutionCount());
        ctx.events.add(targetEvent);
    }

    private void stageEngageExecution(TurnContext ctx) {
        var engage = engageService.runEngageForCurrentRound();
        BattleEvent engageEvent = new BattleEvent();
        engageEvent.setSource("SYSTEM");
        engageEvent.setTarget("SYSTEM");
        engageEvent.setAction("ENGAGE_RUN");
        engageEvent.setSide("SYSTEM");
        engageEvent.setMessage("Engage环节执行：计划 " + engage.getPlannedFireSolutionCount() + " / 执行 "
                + engage.getExecutedStrikeCount() + " / 命中 " + engage.getSuccessfulHitCount());
        ctx.events.add(engageEvent);
    }

    private void stageAssessExecution(TurnContext ctx) {
        var assess = assessService.runAssessNow();
        BattleEvent assessEvent = new BattleEvent();
        assessEvent.setSource("SYSTEM");
        assessEvent.setTarget("SYSTEM");
        assessEvent.setAction("ASSESS_RUN");
        assessEvent.setSide("SYSTEM");
        assessEvent.setMessage("Assess环节完成：任务效能 " + (int) Math.round(assess.getMissionEffectivenessRate() * 100)
                + "% / 闭环完成度 " + (int) Math.round(assess.getLoopClosureRate() * 100) + "%");
        ctx.events.add(assessEvent);
    }

    private void stageVictoryCheck(TurnContext ctx) {
        VictoryDecision decision = evaluateVictory(ctx);
        if (decision == null) {
            return;
        }
        scenarioService.updateWinner(decision.winner, decision.reason);
        BattleEvent event = new BattleEvent();
        event.setSource("SYSTEM");
        event.setTarget("SYSTEM");
        event.setAction("VICTORY_DECIDED");
        event.setSide("SYSTEM");
        event.setMessage("判胜完成：胜方 " + decision.winner + "，原因：" + decision.reason);
        ctx.events.add(event);
    }

    private void stageSortieExecution(TurnContext ctx) {
        var changed = sortieMissionService.runForCurrentRound();
        if (changed == null || changed.isEmpty()) {
            return;
        }
        BattleEvent sortieEvent = new BattleEvent();
        sortieEvent.setSource("SYSTEM");
        sortieEvent.setTarget("SYSTEM");
        sortieEvent.setAction("SORTIE_RUN");
        sortieEvent.setSide("SYSTEM");
        sortieEvent.setMessage("架次任务推进：" + changed.size() + " 条任务状态更新。");
        ctx.events.add(sortieEvent);
    }

    private void stageNavalCommandExecution(TurnContext ctx) {
        NavalTurnMetrics metrics = new NavalTurnMetrics();
        int updated = 0;
        for (CombatUnit unit : ctx.activeUnits) {
            if (unit == null || !"SEA".equalsIgnoreCase(unit.getDomain())) {
                continue;
            }
            applyNavalSustainment(unit, metrics);
            applyNavalMissionBehavior(unit, ctx.activeUnits, metrics);
            unitService.updateUnit(unit);
            updated++;
        }
        if (updated <= 0) {
            return;
        }
        BattleEvent navalEvent = new BattleEvent();
        navalEvent.setSource("SYSTEM");
        navalEvent.setTarget("SYSTEM");
        navalEvent.setAction("NAVAL_COMMAND_RUN");
        navalEvent.setSide("SYSTEM");
        navalEvent.setMessage("海上作战行为推进：" + updated + " 个海上单位执行机动/补给/态势动作；"
                + "区域拒止影响 " + metrics.areaDeniedTargets + " 次，编队防空强化 " + metrics.airDefenseBoosts
                + " 次，反潜搜索命中 " + metrics.antiSubContacts + " 次，油弹耗竭预警 " + metrics.supplyWarnings + " 次。");
        ctx.events.add(navalEvent);
    }

    private void stageCommandChainExecution(TurnContext ctx) {
        var changed = commandChainService.runOrdersForCurrentRound();
        if (changed == null || changed.isEmpty()) {
            return;
        }
        BattleEvent cmdEvent = new BattleEvent();
        cmdEvent.setSource("SYSTEM");
        cmdEvent.setTarget("SYSTEM");
        cmdEvent.setAction("COMMAND_CHAIN_RUN");
        cmdEvent.setSide("SYSTEM");
        cmdEvent.setMessage("编组指挥链推进：" + changed.size() + " 条命令更新。");
        ctx.events.add(cmdEvent);
    }

    private void stageOpposingExecution(TurnContext ctx) {
        var changed = opposingActionService.runForCurrentRound();
        if (changed == null || changed.isEmpty()) {
            return;
        }
        BattleEvent oppEvent = new BattleEvent();
        oppEvent.setSource("SYSTEM");
        oppEvent.setTarget("SYSTEM");
        oppEvent.setAction("OPPOSING_RUN");
        oppEvent.setSide("SYSTEM");
        oppEvent.setMessage("敌方对抗动作推进：" + changed.size() + " 条状态更新。");
        ctx.events.add(oppEvent);
    }

    private void stageCampaignCheck(TurnContext ctx) {
        checkCampaignStatus(ctx.events);
    }

    private void stageActivityExecution(TurnContext ctx) {
        ctx.activeActivities = activityService.getActiveActivities(ctx.currentRound);
        for (CombatActivity activity : ctx.activeActivities) {
            if ("EXECUTING".equals(activity.getStatus()) || "PLANNED".equals(activity.getStatus())) {
                activityService.executeActivityStep(activity);
                BattleEvent activityEvent = new BattleEvent();
                activityEvent.setSource("SYSTEM");
                activityEvent.setTarget("SYSTEM");
                activityEvent.setAction("ACTIVITY_EXECUTE");
                activityEvent.setSide(activity.getSide());
                activityEvent.setMessage("执行作战活动：" + activity.getName() + " (步骤"
                        + (activity.getCurrentStep() + 1) + "/" + activity.getSteps().size() + ")");
                ctx.events.add(activityEvent);
            }
        }
    }

    private void stageCoordinationExecution(TurnContext ctx) {
        List<CoordinationAction> activeCoordinationActions = coordinationService.getCoordinationActionsForEngine().stream()
                .filter(a -> "EXECUTING".equals(a.getStatus()) ||
                        ("PLANNED".equals(a.getStatus()) && a.getCurrentRound() == 0))
                .collect(Collectors.toList());
        for (CoordinationAction action : activeCoordinationActions) {
            if (action.getDuration() > 0 && action.getCurrentRound() < action.getDuration()) {
                coordinationService.executeCoordinationAction(action, ctx.currentRound);
                BattleEvent coordEvent = new BattleEvent();
                coordEvent.setSource("SYSTEM");
                coordEvent.setTarget("SYSTEM");
                coordEvent.setAction("COORDINATION_EXECUTE");
                coordEvent.setSide(action.getSide());
                coordEvent.setMessage("执行协同作战：" + action.getName() + " (回合"
                        + (action.getCurrentRound() + 1) + "/" + action.getDuration() + ")");
                ctx.events.add(coordEvent);
            }
        }
    }

    private void stageInteractionProcess(TurnContext ctx) {
        interactionProcessService.runProcessesForSimulationRound(ctx.currentRound, ctx.events);
    }

    private void stageAutonomousCombat(TurnContext ctx) {
        Set<String> unitsBusyWithActivities = collectUnitIdsFromActivities(ctx.activeActivities);
        for (CombatUnit me : ctx.activeUnits) {
            if (unitsBusyWithActivities.contains(me.getId())) {
                continue;
            }
            runAutonomousUnitTurn(me, ctx.activeUnits, ctx.currentRound, ctx.events);
        }
    }

    private void stageRecordStat(TurnContext ctx) {
        recordStat();
    }

    private static class TurnContext {
        private final int currentRound;
        private final List<CombatUnit> activeUnits;
        private final List<BattleEvent> events = new ArrayList<>();
        private List<CombatActivity> activeActivities = new ArrayList<>();

        private TurnContext(int currentRound, List<CombatUnit> activeUnits) {
            this.currentRound = currentRound;
            this.activeUnits = activeUnits;
        }
    }

    private Set<String> collectUnitIdsFromActivities(List<CombatActivity> activities) {
        Set<String> ids = new HashSet<>();
        if (activities == null) {
            return ids;
        }
        for (CombatActivity a : activities) {
            if (a.getUnitIds() != null) {
                ids.addAll(a.getUnitIds());
            }
        }
        return ids;
    }

    /**
     * 未编入作战活动的单位：优先按想定作战目标机动/交战，否则沿用最近敌人逻辑。
     */
    private void runAutonomousUnitTurn(CombatUnit me, List<CombatUnit> activeUnits, int currentRound, List<BattleEvent> events) {
        CombatObjective obj = objectiveDirectiveService.resolveObjectiveForUnit(me);
        if (obj != null && !obj.isCompleted()) {
            String type = obj.getType() != null ? obj.getType() : "";
            boolean hasPos = objectiveDirectiveService.hasObjectivePosition(obj);

            if ("DEFEND".equals(type) && hasPos) {
                if (!objectiveDirectiveService.isWithinDefendHold(me, obj)) {
                    objectiveDirectiveService.moveUnitOneStepTowardsObjective(me, obj, terrainService, unitService);
                    return;
                }
                CombatUnit enemy = findNearestEnemy(me, activeUnits);
                if (enemy != null) {
                    double dist = calculateDistance(me, enemy);
                    if (dist <= me.getAttackRange()) {
                        attackEnemyWithRules(me, enemy, currentRound, events);
                    }
                }
                return;
            }

            if ("CAPTURE".equals(type) && hasPos) {
                if (!objectiveDirectiveService.isWithinArrivalRadius(me, obj)) {
                    objectiveDirectiveService.moveUnitOneStepTowardsObjective(me, obj, terrainService, unitService);
                    return;
                }
                scenarioService.markObjectiveCompletedInActiveScenario(obj.getId());
                CombatUnit enemy = findNearestEnemy(me, activeUnits);
                if (enemy != null) {
                    double dist = calculateDistance(me, enemy);
                    if (dist <= me.getAttackRange()) {
                        attackEnemyWithRules(me, enemy, currentRound, events);
                    }
                }
                return;
            }

            if ("DESTROY".equals(type)) {
                if (!hasPos) {
                    runDefaultNearestEnemyTurn(me, activeUnits, currentRound, events);
                    return;
                }
                if (!objectiveDirectiveService.isWithinArrivalRadius(me, obj)) {
                    objectiveDirectiveService.moveUnitOneStepTowardsObjective(me, obj, terrainService, unitService);
                    return;
                }
                runDefaultNearestEnemyTurn(me, activeUnits, currentRound, events);
                return;
            }
        }

        runDefaultNearestEnemyTurn(me, activeUnits, currentRound, events);
    }

    private void runDefaultNearestEnemyTurn(CombatUnit me, List<CombatUnit> activeUnits, int currentRound, List<BattleEvent> events) {
        CombatUnit target = findNearestEnemy(me, activeUnits);
        if (target == null) {
            return;
        }
        double dist = calculateDistance(me, target);
        double attackRange = me.getAttackRange();
        if (dist > attackRange) {
            moveTowards(me, target);
        } else {
            attackEnemyWithRules(me, target, currentRound, events);
        }
    }

    private void attackEnemyWithRules(CombatUnit me, CombatUnit target, int currentRound, List<BattleEvent> events) {
        List<InteractionRule> appliedRules = new ArrayList<>();
        List<InteractionRule> enabledRules = ruleService.getEnabledInteractionRulesForEngine();
        for (InteractionRule rule : enabledRules) {
            if (ruleService.ruleAppliesToPair(rule, me, target, currentRound)) {
                ruleService.applyMatchedRule(rule, me, target);
                appliedRules.add(rule);

                InteractionEvent event = new InteractionEvent();
                event.setId(java.util.UUID.randomUUID().toString());
                event.setRuleId(rule.getId());
                event.setRuleName(rule.getName());
                event.setType(rule.getType());
                event.setSourceUnitId(me.getId());
                event.setSourceUnitName(me.getName());
                event.setTargetUnitId(target.getId());
                event.setTargetUnitName(target.getName());
                event.setResult("SUCCESS");
                event.setEffectValue(rule.getEffectValue());
                event.setDescription(rule.getDescription());
                event.setRound(currentRound + 1);
                event.setTimestamp(System.currentTimeMillis());
                if (me.getLatitude() != null && me.getLongitude() != null) {
                    event.setLatitude(me.getLatitude());
                    event.setLongitude(me.getLongitude());
                }
                interactionEventRepository.save(event);
            }
        }
        BattleEvent battleEvent = executeAttack(me, target, appliedRules);
        events.add(battleEvent);
    }

    private CombatUnit findNearestEnemy(CombatUnit me, List<CombatUnit> allUnits) {
        CombatUnit nearest = null;
        double minDesc = Double.MAX_VALUE;
        for (CombatUnit other : allUnits) {
            if (!other.getSide().equals(me.getSide()) && other.getCombatPower() > 0) {
                double d = calculateDistance(me, other);
                if (d < minDesc) { minDesc = d; nearest = other; }
            }
        }
        return nearest;
    }

    private double calculateDistance(CombatUnit a, CombatUnit b) {
        // 优先使用真实地理坐标
        if (a.getLatitude() != null && a.getLongitude() != null && 
            b.getLatitude() != null && b.getLongitude() != null) {
            return GeoUtils.calculateDistance(
                a.getLatitude(), a.getLongitude(),
                b.getLatitude(), b.getLongitude()
            );
        }
        // 回退到百分比坐标系统
        return Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2));
    }

    private void moveTowards(CombatUnit mover, CombatUnit target) {
        double baseDistance = mover.getSpeed() * 10000; // 增加移动距离，使移动更明显
        double speedFactor = 1.0;
        Terrain t = terrainService.getUnitTerrain(mover);
        if (t != null) {
            speedFactor = terrainService.getTerrainSpeedModifier(t);
        }
        double step = Math.max(baseDistance * speedFactor, 50000 * speedFactor); // 增加最小移动距离
        
        // 优先使用真实地理坐标
        if (mover.getLatitude() != null && mover.getLongitude() != null &&
            target.getLatitude() != null && target.getLongitude() != null) {
            double[] newPos = GeoUtils.moveTowards(
                mover.getLatitude(), mover.getLongitude(),
                target.getLatitude(), target.getLongitude(),
                step
            );
            mover.setLatitude(newPos[0]);
            mover.setLongitude(newPos[1]);
            // 同步更新百分比坐标（用于兼容旧系统）
            syncPercentageCoordinates(mover);
        } else {
            // 回退到百分比坐标系统
            double dx = target.getX() - mover.getX();
            double dy = target.getY() - mover.getY();
            double angle = Math.atan2(dy, dx);
            mover.setX(mover.getX() + Math.cos(angle) * step);
            mover.setY(mover.getY() + Math.sin(angle) * step);
        }
        
        // 保存单位移动后的状态
        unitService.updateUnit(mover);
    }
    
    /**
     * 同步百分比坐标（用于兼容旧系统）
     */
    private void syncPercentageCoordinates(CombatUnit unit) {
        // 暂时保留，后续可以根据实际地图范围计算
    }

    /**
     * 战役与推演联动：先更新关键事件/目标状态并推送战报，再视情况自动推进阶段。
     */
    private void checkCampaignStatus(List<BattleEvent> events) {
        try {
            Campaign campaign = campaignService.getCurrentCampaign();
            if (campaign == null) {
                return;
            }
            List<CombatUnit> allUnits = unitService.getUnitsForBattleEngine();
            checkCampaignEvents(events, campaign, allUnits);
            checkCampaignObjectives(events, campaign, allUnits);
            checkCampaignPhase(events, campaign, allUnits);
        } catch (Exception e) {
            System.out.println("战役管理检查出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkCampaignPhase(List<BattleEvent> events, Campaign campaign, List<CombatUnit> units) {
        int phaseIdx = campaignService.getCurrentPhase();
        List<String> phases = campaign.getPhases();
        if (phases == null || phaseIdx < 0 || phaseIdx >= phases.size()) {
            return;
        }
        if (!checkPhaseCompletion(campaign, phaseIdx, units)) {
            return;
        }
        if (phaseIdx >= phases.size() - 1) {
            return;
        }
        String fromName = phases.get(phaseIdx);
        String toName = phases.get(phaseIdx + 1);
        campaignService.setCurrentPhase(phaseIdx + 1);
        BattleEvent phaseEvent = new BattleEvent();
        phaseEvent.setSource("SYSTEM");
        phaseEvent.setTarget("SYSTEM");
        phaseEvent.setAction("PHASE_ADVANCE");
        phaseEvent.setSide("SYSTEM");
        phaseEvent.setMessage("战役阶段推进：" + fromName + " → " + toName);
        events.add(phaseEvent);
    }

    private boolean checkPhaseCompletion(Campaign campaign, int phaseIndex, List<CombatUnit> units) {
        List<CampaignObjective> objectives = campaign.getObjectives();
        if (objectives == null || campaign.getPhases() == null || phaseIndex < 0 || phaseIndex >= campaign.getPhases().size()) {
            return false;
        }
        String phaseName = campaign.getPhases().get(phaseIndex);
        for (CampaignObjective objective : objectives) {
            if (phaseName.equals(objective.getPhase())) {
                if (!campaignService.isCampaignObjectiveDone(objective, units)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void checkCampaignEvents(List<BattleEvent> events, Campaign campaign, List<CombatUnit> units) {
        List<CampaignEvent> campaignEvents = campaign.getEvents();
        if (campaignEvents == null || campaign.getPhases() == null) {
            return;
        }
        int currentPhase = campaignService.getCurrentPhase();
        if (currentPhase < 0 || currentPhase >= campaign.getPhases().size()) {
            return;
        }
        String currentPhaseName = campaign.getPhases().get(currentPhase);
        for (CampaignEvent event : campaignEvents) {
            if (!currentPhaseName.equals(event.getPhase()) || event.isTriggered()) {
                continue;
            }
            if (campaignService.isCampaignEventTriggered(event, units)) {
                event.setTriggered(true);
                BattleEvent triggerEvent = new BattleEvent();
                triggerEvent.setSource("SYSTEM");
                triggerEvent.setTarget("SYSTEM");
                triggerEvent.setAction("EVENT_TRIGGER");
                triggerEvent.setSide("SYSTEM");
                triggerEvent.setMessage("战役事件触发：" + event.getName() + " - " + event.getDescription());
                events.add(triggerEvent);
            }
        }
    }

    private void checkCampaignObjectives(List<BattleEvent> events, Campaign campaign, List<CombatUnit> units) {
        List<CampaignObjective> objectives = campaign.getObjectives();
        if (objectives == null || campaign.getPhases() == null) {
            return;
        }
        int currentPhase = campaignService.getCurrentPhase();
        if (currentPhase < 0 || currentPhase >= campaign.getPhases().size()) {
            return;
        }
        String currentPhaseName = campaign.getPhases().get(currentPhase);
        for (CampaignObjective objective : objectives) {
            if (!currentPhaseName.equals(objective.getPhase()) || objective.isCompleted()) {
                continue;
            }
            if (campaignService.isCampaignObjectiveDone(objective, units)) {
                objective.setCompleted(true);
                BattleEvent objEvent = new BattleEvent();
                objEvent.setSource("SYSTEM");
                objEvent.setTarget("SYSTEM");
                objEvent.setAction("OBJECTIVE_COMPLETE");
                objEvent.setSide("SYSTEM");
                objEvent.setMessage("战役目标完成：" + objective.getName() + " - " + objective.getDescription());
                events.add(objEvent);
            }
        }
    }

    private BattleEvent executeAttack(CombatUnit attacker, CombatUnit target, List<InteractionRule> appliedRules) {
        BattleEvent event = new BattleEvent();
        event.setSource(attacker.getName());
        event.setTarget(target.getName());
        event.setAction("ATTACK");
        event.setSide(attacker.getSide());

        SplittableRandom rng = simulationRandom.rng("ENGINE_ATTACK",
                scenarioService.getCurrentRound(),
                attacker == null ? null : (attacker.getId() + "->" + (target == null ? "" : target.getId())));
        int baseDamage = 20 + rng.nextInt(10);
        double typeMultiplier = 1.0;
        if ("TANK".equals(attacker.getType()) && "INFANTRY".equals(target.getType())) typeMultiplier = 1.5;
        if ("INFANTRY".equals(attacker.getType()) && "TANK".equals(target.getType())) typeMultiplier = 0.3;
        if ("MECH_INFANTRY".equals(attacker.getType()) && "INFANTRY".equals(target.getType())) typeMultiplier = 1.2;
        if ("ARTILLERY".equals(attacker.getType()) && "TANK".equals(target.getType())) typeMultiplier = 1.3;
        if ("ROCKET_ARTILLERY".equals(attacker.getType()) && "TANK".equals(target.getType())) typeMultiplier = 1.4;
        if ("AA_GUN".equals(attacker.getType()) && "FIGHTER".equals(target.getType())) typeMultiplier = 1.5;
        if ("FIGHTER".equals(attacker.getType()) && "INFANTRY".equals(target.getType())) typeMultiplier = 1.3;
        if ("SEA".equalsIgnoreCase(attacker.getDomain()) && "SEA".equalsIgnoreCase(target.getDomain())) {
            typeMultiplier *= 1.15 + clampNaval(attacker.getAntiShipCapability()) * 0.45;
            typeMultiplier *= (1.0 - clampNaval(target.getAntiAirCapability()) * 0.18);
        }
        if ("SEA".equalsIgnoreCase(attacker.getDomain()) && "SUBMARINE".equalsIgnoreCase(attacker.getVesselType())) {
            typeMultiplier *= 1.1 + clampNaval(attacker.getStealthFactor()) * 0.25;
        }

        double terrainDefMultiplier = 1.0;
        Terrain t = terrainService.getUnitTerrain(target);
        String terrainMsg = "";
        if (t != null) {
            terrainDefMultiplier = terrainService.getTerrainDefenseModifier(t);
            if ("FOREST".equals(t.getType())) { terrainMsg = "【🌲林地掩护】"; }
            if ("SWAMP".equals(t.getType())) { terrainMsg = "【💀陷入泥潭】"; }
        }

        // 应用交互规则的伤害加成
        double ruleDamageMultiplier = 1.0;
        for (InteractionRule rule : appliedRules) {
            if ("DAMAGE_BOOST".equals(rule.getEffectType())) {
                ruleDamageMultiplier *= rule.getEffectValue();
            }
        }

        double fleetAirDefenseShield = 1.0 - computeFleetAirDefenseShield(target);
        double areaDenialPenalty = computeAreaDenialPenalty(attacker);
        int finalDamage = (int) (baseDamage * typeMultiplier * terrainDefMultiplier * ruleDamageMultiplier
                * fleetAirDefenseShield * areaDenialPenalty);
        target.setCombatPower(target.getCombatPower() - finalDamage);
        event.setDamage(finalDamage);

        StringBuilder msg = new StringBuilder();
        msg.append(attacker.getName()).append(" 攻击 ").append(target.getName()).append(terrainMsg);
        if(typeMultiplier > 1.2) msg.append(" (效果拔群!)");
        if(typeMultiplier < 0.8) msg.append(" (收效甚微...)");
        msg.append(" 造成 ").append(finalDamage).append(" 点伤害");
        if (fleetAirDefenseShield < 0.98) {
            msg.append("【编队防空拦截】");
        }
        if (areaDenialPenalty < 0.99) {
            msg.append("【区域拒止干扰】");
        }

        event.setMessage(msg.toString());

        if (target.getCombatPower() <= 0) {
            target.setCombatPower(0);
            target.setStatus("DESTROYED");
            event.setAction("DESTROY");
            event.setMessage(target.getName() + " 被 " + attacker.getName() + " 击毁！");
        }
        
        // 保存单位状态更改
        unitService.updateUnit(target);
        
        return event;
    }

    private void applyNavalSustainment(CombatUnit unit, NavalTurnMetrics metrics) {
        double fuelTick = 0.8;
        int ammoTick = 0;
        String mission = unit.getMission() == null ? "" : unit.getMission().toUpperCase();
        if (mission.contains("STRIKE") || mission.contains("FIRE_ALLOCATION")) {
            fuelTick = 1.8;
            ammoTick = 4;
        } else if (mission.contains("ANTI_SUBMARINE") || mission.contains("PATROL")) {
            fuelTick = 1.3;
            ammoTick = 1;
        } else if (mission.contains("ELECTRONIC_SUPPRESSION")) {
            fuelTick = 1.4;
        }
        unit.setFuelLevel(Math.max(0, unit.getFuelLevel() - fuelTick));
        unit.setAmmoLevel(Math.max(0, unit.getAmmoLevel() - ammoTick));
        if (metrics != null) {
            metrics.fuelConsumed += fuelTick;
            metrics.ammoConsumed += ammoTick;
        }
        if (unit.getFuelLevel() < 8 || unit.getAmmoLevel() < 8) {
            unit.setSupplyStatus("CRITICAL");
            if (metrics != null) {
                metrics.supplyWarnings++;
            }
        } else if (unit.getFuelLevel() < 20 || unit.getAmmoLevel() < 20) {
            unit.setSupplyStatus("LOW");
            if (metrics != null) {
                metrics.supplyWarnings++;
            }
        } else {
            unit.setSupplyStatus("ADEQUATE");
        }
    }

    private void applyNavalMissionBehavior(CombatUnit unit, List<CombatUnit> allUnits, NavalTurnMetrics metrics) {
        String mission = missionKey(unit);
        if (mission.contains("WITHDRAW_REORGANIZE")) {
            unit.setReadinessLevel(Math.min(1.0, unit.getReadinessLevel() + 0.08));
            if (unit.getFuelLevel() < 95) {
                unit.setFuelLevel(Math.min(100, unit.getFuelLevel() + 1.5));
            }
            if (unit.getAmmoLevel() < 90) {
                unit.setAmmoLevel(Math.min(100, unit.getAmmoLevel() + 2));
            }
            unit.setSupplyStatus(unit.getFuelLevel() > 35 && unit.getAmmoLevel() > 35 ? "ADEQUATE" : unit.getSupplyStatus());
            return;
        }
        if (mission.contains("ELECTRONIC_SUPPRESSION")) {
            unit.setDetectionChainStatus("DEGRADED");
            if (metrics != null) {
                metrics.airDefenseBoosts++;
            }
            return;
        }
        if (mission.contains("AIR_DEFENSE")) {
            unit.setDetectionChainStatus("OPEN");
            unit.setReadinessLevel(Math.min(1.0, unit.getReadinessLevel() + 0.03));
            unit.setAntiAirCapability(Math.min(1.0, unit.getAntiAirCapability() + 0.02));
            if (metrics != null) {
                metrics.airDefenseBoosts++;
            }
            return;
        }
        if (mission.contains("ROUTE_MANEUVER")) {
            CombatUnit anchor = selectSeaAnchor(unit, allUnits);
            if (anchor != null) {
                moveTowards(unit, anchor);
            }
            return;
        }
        if (mission.contains("ESCORT")) {
            CombatUnit escortTarget = findEscortTarget(unit, allUnits);
            if (escortTarget != null) {
                moveTowards(unit, escortTarget);
            }
            return;
        }
        if (mission.contains("FIRE_ALLOCATION")) {
            unit.setDetectionChainStatus("OPEN");
            unit.setSeaControlContribution(Math.min(1.0, unit.getSeaControlContribution() + 0.04));
            return;
        }
        if (mission.contains("BLOCKADE") || mission.contains("SEA_DENIAL")) {
            int impacted = applySeaDenialPressure(unit, allUnits);
            if (metrics != null) {
                metrics.areaDeniedTargets += impacted;
            }
            unit.setSeaControlContribution(Math.min(1.0, unit.getSeaControlContribution() + 0.05));
            return;
        }
        if (mission.contains("ANTI_SUBMARINE") || mission.contains("PATROL")) {
            int contacts = runAntiSubPatrol(unit, allUnits);
            if (metrics != null) {
                metrics.antiSubContacts += contacts;
            }
        }
    }

    private int applySeaDenialPressure(CombatUnit unit, List<CombatUnit> allUnits) {
        int impacted = 0;
        for (CombatUnit enemy : allUnits) {
            if (enemy == null || enemy.getCombatPower() <= 0 || unit.getSide().equals(enemy.getSide())) {
                continue;
            }
            if (!"SEA".equalsIgnoreCase(enemy.getDomain())) {
                continue;
            }
            double d = calculateDistance(unit, enemy);
            if (d > 180000) {
                continue;
            }
            enemy.setReadinessLevel(Math.max(0.35, enemy.getReadinessLevel() - 0.03));
            enemy.setDetectionChainStatus("JAMMED");
            unitService.updateUnit(enemy);
            impacted++;
        }
        return impacted;
    }

    private int runAntiSubPatrol(CombatUnit unit, List<CombatUnit> allUnits) {
        int contacts = 0;
        for (CombatUnit enemy : allUnits) {
            if (enemy == null || enemy.getCombatPower() <= 0 || unit.getSide().equals(enemy.getSide())) {
                continue;
            }
            if (!"SEA".equalsIgnoreCase(enemy.getDomain()) || !"SUBMARINE".equalsIgnoreCase(enemy.getVesselType())) {
                continue;
            }
            double d = calculateDistance(unit, enemy);
            if (d > 220000) {
                continue;
            }
            enemy.setStealthFactor(Math.max(0, enemy.getStealthFactor() - 0.08));
            enemy.setDetectionChainStatus("DEGRADED");
            unitService.updateUnit(enemy);
            contacts++;
        }
        return contacts;
    }

    private double computeFleetAirDefenseShield(CombatUnit target) {
        if (target == null || !"SEA".equalsIgnoreCase(target.getDomain())) {
            return 0.0;
        }
        List<CombatUnit> all = unitService.getUnitsForBattleEngine();
        double shield = 0.0;
        for (CombatUnit ally : all) {
            if (ally == null || ally.getCombatPower() <= 0 || !target.getSide().equals(ally.getSide())) {
                continue;
            }
            if (!"SEA".equalsIgnoreCase(ally.getDomain())) {
                continue;
            }
            if (ally.getId() != null && ally.getId().equals(target.getId())) {
                continue;
            }
            double d = calculateDistance(target, ally);
            if (d > 140000) {
                continue;
            }
            String mission = missionKey(ally);
            double contribution = clampNaval(ally.getAntiAirCapability()) * ("AIR_DEFENSE".equals(ally.getNavalTaskType()) || mission.contains("AIR_DEFENSE") ? 0.35 : 0.2);
            if ("SCREEN".equalsIgnoreCase(ally.getFormationRole())) {
                contribution += 0.08;
            }
            shield += contribution;
        }
        return Math.min(0.45, shield);
    }

    private double computeAreaDenialPenalty(CombatUnit attacker) {
        if (attacker == null || !"SEA".equalsIgnoreCase(attacker.getDomain())) {
            return 1.0;
        }
        List<CombatUnit> all = unitService.getUnitsForBattleEngine();
        double pressure = 0.0;
        for (CombatUnit enemy : all) {
            if (enemy == null || enemy.getCombatPower() <= 0 || attacker.getSide().equals(enemy.getSide())) {
                continue;
            }
            if (!"SEA".equalsIgnoreCase(enemy.getDomain())) {
                continue;
            }
            String mission = missionKey(enemy);
            if (!mission.contains("BLOCKADE") && !mission.contains("SEA_DENIAL")) {
                continue;
            }
            double d = calculateDistance(attacker, enemy);
            if (d > 220000) {
                continue;
            }
            pressure += 0.08 + clampNaval(enemy.getSeaControlContribution()) * 0.12;
        }
        return Math.max(0.72, 1.0 - pressure);
    }

    private String missionKey(CombatUnit unit) {
        String mission = unit == null || unit.getMission() == null ? "" : unit.getMission().toUpperCase();
        String task = unit == null || unit.getNavalTaskType() == null ? "" : unit.getNavalTaskType().toUpperCase();
        if (!task.isEmpty()) {
            mission = mission + "|" + task;
        }
        return mission;
    }

    private CombatUnit selectSeaAnchor(CombatUnit unit, List<CombatUnit> allUnits) {
        return allUnits.stream()
                .filter(u -> u != null
                        && u.getCombatPower() > 0
                        && unit.getSide().equals(u.getSide())
                        && !"DESTROYED".equalsIgnoreCase(u.getStatus())
                        && "SEA".equalsIgnoreCase(u.getDomain())
                        && "CORE".equalsIgnoreCase(u.getFormationRole()))
                .min(Comparator.comparingDouble(u -> calculateDistance(unit, u)))
                .orElse(null);
    }

    private CombatUnit findEscortTarget(CombatUnit unit, List<CombatUnit> allUnits) {
        return allUnits.stream()
                .filter(u -> u != null
                        && u.getCombatPower() > 0
                        && unit.getSide().equals(u.getSide())
                        && "SEA".equalsIgnoreCase(u.getDomain())
                        && ("SUPPORT_SHIP".equalsIgnoreCase(u.getVesselType())
                        || "CARRIER".equalsIgnoreCase(u.getVesselType())))
                .min(Comparator.comparingDouble(u -> calculateDistance(unit, u)))
                .orElse(null);
    }

    private VictoryDecision evaluateVictory(TurnContext ctx) {
        Map<String, List<CombatUnit>> bySide = ctx.activeUnits.stream()
                .filter(u -> u != null && u.getCombatPower() > 0)
                .collect(Collectors.groupingBy(CombatUnit::getSide));
        boolean redAlive = bySide.containsKey("RED") && !bySide.get("RED").isEmpty();
        boolean blueAlive = bySide.containsKey("BLUE") && !bySide.get("BLUE").isEmpty();
        if (redAlive && !blueAlive) {
            return new VictoryDecision("RED", "FLEET_ELIMINATED");
        }
        if (blueAlive && !redAlive) {
            return new VictoryDecision("BLUE", "FLEET_ELIMINATED");
        }
        if (!redAlive && !blueAlive) {
            return new VictoryDecision("DRAW", "MUTUAL_ELIMINATION");
        }

        String scenarioId = scenarioService.getActiveScenarioId();
        ScenarioData data = scenarioId == null ? null : scenarioService.getScenarioDataById(scenarioId);
        if (data != null && data.getObjectives() != null && !data.getObjectives().isEmpty()) {
            VictoryDecision navalDecision = evaluateNavalObjectiveVictory(data, bySide);
            if (navalDecision != null) {
                return navalDecision;
            }
            Map<String, List<CombatObjective>> objBySide = new HashMap<>();
            for (CombatObjective objective : data.getObjectives()) {
                if (objective == null || objective.getSide() == null || objective.getSide().isEmpty()) {
                    continue;
                }
                objBySide.computeIfAbsent(objective.getSide(), key -> new ArrayList<>()).add(objective);
            }
            for (Map.Entry<String, List<CombatObjective>> entry : objBySide.entrySet()) {
                if (!entry.getValue().isEmpty() && entry.getValue().stream().allMatch(CombatObjective::isCompleted)) {
                    return new VictoryDecision(entry.getKey(), "OBJECTIVES_COMPLETED");
                }
            }
            int maxRounds = data.getMaxRounds() > 0 ? data.getMaxRounds() : Integer.MAX_VALUE;
            if (ctx.currentRound >= maxRounds) {
                int redPower = bySide.getOrDefault("RED", List.of()).stream().mapToInt(CombatUnit::getCombatPower).sum();
                int bluePower = bySide.getOrDefault("BLUE", List.of()).stream().mapToInt(CombatUnit::getCombatPower).sum();
                if (redPower == bluePower) {
                    return new VictoryDecision("DRAW", "ROUND_LIMIT_POWER_TIE");
                }
                return new VictoryDecision(redPower > bluePower ? "RED" : "BLUE", "ROUND_LIMIT_REMAINING_POWER");
            }
        }
        return null;
    }

    private VictoryDecision evaluateNavalObjectiveVictory(ScenarioData data, Map<String, List<CombatUnit>> bySide) {
        if (!"SEA".equalsIgnoreCase(data.getScenarioDomain())) {
            return null;
        }
        Map<String, Integer> escortDone = new HashMap<>();
        Map<String, Integer> blockadeDone = new HashMap<>();
        Map<String, Double> areaControl = new HashMap<>();
        for (CombatObjective objective : data.getObjectives()) {
            if (objective == null || objective.getSide() == null || objective.getSide().isEmpty()) {
                continue;
            }
            String side = objective.getSide().toUpperCase();
            String type = objective.getObjectiveType() == null ? "" : objective.getObjectiveType().toUpperCase();
            if ("ESCORT".equals(type) && objective.isCompleted()) {
                escortDone.merge(side, 1, Integer::sum);
            }
            if ("BLOCKADE".equals(type) && objective.isCompleted()) {
                blockadeDone.merge(side, 1, Integer::sum);
            }
            if ("AREA_CONTROL".equals(type)) {
                double sideControl = computeSideSeaControl(bySide.getOrDefault(side, List.of()));
                if (sideControl >= Math.max(0.3, objective.getControlThreshold())) {
                    areaControl.merge(side, 1.0, Double::sum);
                }
            }
        }
        for (String side : List.of("RED", "BLUE")) {
            int escortScore = escortDone.getOrDefault(side, 0);
            int blockadeScore = blockadeDone.getOrDefault(side, 0);
            double areaScore = areaControl.getOrDefault(side, 0.0);
            if (escortScore + blockadeScore + areaScore >= 2.0) {
                return new VictoryDecision(side, "NAVAL_OBJECTIVE_SUPERIORITY");
            }
        }
        return null;
    }

    private double computeSideSeaControl(List<CombatUnit> units) {
        if (units == null || units.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        int count = 0;
        for (CombatUnit unit : units) {
            if (unit == null || unit.getCombatPower() <= 0 || !"SEA".equalsIgnoreCase(unit.getDomain())) {
                continue;
            }
            total += clampNaval(unit.getSeaControlContribution()) * 0.55
                    + clampNaval(unit.getReadinessLevel()) * 0.25
                    + clampNaval(unit.getAntiAirCapability()) * 0.2;
            count++;
        }
        if (count == 0) {
            return 0.0;
        }
        return total / count;
    }

    private double clampNaval(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static class VictoryDecision {
        private final String winner;
        private final String reason;

        private VictoryDecision(String winner, String reason) {
            this.winner = winner;
            this.reason = reason;
        }
    }

    private static class NavalTurnMetrics {
        private int areaDeniedTargets;
        private int airDefenseBoosts;
        private int antiSubContacts;
        private int supplyWarnings;
        private double fuelConsumed;
        private int ammoConsumed;
    }

    private void recordStat() {
        List<CombatUnit> all = unitService.getUnitsForBattleEngine();
        int redHp = all.stream().filter(u -> "RED".equals(u.getSide())).mapToInt(CombatUnit::getCombatPower).sum();
        int blueHp = all.stream().filter(u -> "BLUE".equals(u.getSide())).mapToInt(CombatUnit::getCombatPower).sum();
        int redCnt = (int) all.stream().filter(u -> "RED".equals(u.getSide()) && u.getCombatPower() > 0).count();
        int blueCnt = (int) all.stream().filter(u -> "BLUE".equals(u.getSide()) && u.getCombatPower() > 0).count();
        scenarioService.recordStat(redHp, blueHp, redCnt, blueCnt);
    }

    /**
     * 重置战斗场景
     */
    public void resetScenario() {
        // 删除所有作战单位
        List<CombatUnit> allUnits = unitService.getAllUnits();
        for (CombatUnit unit : allUnits) {
            unitService.deleteUnit(unit.getId());
        }
        // 重置统计数据
        scenarioService.resetStats();
    }
}
