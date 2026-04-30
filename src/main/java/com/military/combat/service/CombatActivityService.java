package com.military.combat.service;

import com.military.combat.entity.Campaign;
import com.military.combat.entity.CombatActivity;
import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.ScenarioData;
import com.military.combat.entity.CoordinationAction;
import com.military.combat.entity.InteractionRule;
import com.military.combat.repository.CombatActivityRepository;
import com.military.combat.repository.CombatObjectiveRepository;
import com.military.combat.repository.CombatUnitRepository;
import com.military.combat.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.SplittableRandom;

@Service
public class CombatActivityService {

    @Autowired
    private CombatActivityRepository activityRepository;

    @Autowired
    private CombatObjectiveRepository objectiveRepository;

    @Autowired
    private CombatUnitRepository unitRepository;



    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CoordinationService coordinationService;

    @Autowired
    private InteractionRuleService interactionRuleService;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private TerrainService terrainService;

    @Autowired
    private CombatObjectiveDirectiveService objectiveDirectiveService;

    @Autowired
    private com.military.combat.simulation.random.SimulationRandom simulationRandom;

    // --- 作战活动生命周期管理 ---
    public CombatActivity createActivity(CombatActivity activity) {
        if (activity.getId() == null) {
            activity.setId(java.util.UUID.randomUUID().toString());
        }
        if (activity.getStatus() == null) {
            activity.setStatus("PLANNED");
        }
        if (activity.getCurrentStep() == 0 && activity.getSteps() != null && !activity.getSteps().isEmpty()) {
            activity.setCurrentStep(0);
        }
        if ((activity.getScenarioId() == null || activity.getScenarioId().isEmpty())
                && scenarioService.getActiveScenarioId() != null
                && !scenarioService.getActiveScenarioId().isEmpty()) {
            activity.setScenarioId(scenarioService.getActiveScenarioId());
        }

        resolveCampaignId(activity);
        bindActivityToScenarioCampaign(activity);

        activity.setCreatedAt(new Date());
        activity.setUpdatedAt(new Date());
        return activityRepository.save(activity);
    }

    /**
     * 战役 ID：显式填写 &gt; 当前会话战役 &gt; 当前想定内嵌战役（均可能为历史预设或自定义）。
     */
    private void resolveCampaignId(CombatActivity activity) {
        if (activity.getCampaignId() != null && !activity.getCampaignId().isEmpty()) {
            return;
        }
        Campaign cur = campaignService.getCurrentCampaign();
        if (cur != null && cur.getId() != null && !cur.getId().isEmpty()) {
            activity.setCampaignId(cur.getId());
            return;
        }
        String sid = activity.getScenarioId() != null && !activity.getScenarioId().isEmpty()
                ? activity.getScenarioId()
                : scenarioService.getActiveScenarioId();
        if (sid != null && !sid.isEmpty()) {
            ScenarioData sd = scenarioService.getScenarioDataById(sid);
            if (sd != null && sd.getCampaign() != null) {
                campaignService.normalizeCampaign(sd.getCampaign());
                activity.setCampaignId(sd.getCampaign().getId());
            }
        }
    }

    /**
     * 活动建模从属战役：若未显式填写 campaignId，则从关联想定上的战役同步（主线：战役 → 想定 → 活动命令）。
     */
    private void bindActivityToScenarioCampaign(CombatActivity activity) {
        if (activity.getCampaignId() != null && !activity.getCampaignId().isEmpty()) {
            return;
        }
        String sid = activity.getScenarioId() != null && !activity.getScenarioId().isEmpty()
                ? activity.getScenarioId()
                : scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) {
            return;
        }
        ScenarioData sd = scenarioService.getScenarioDataById(sid);
        if (sd == null) {
            return;
        }
        if (sd.getCampaign() != null && sd.getCampaign().getId() != null) {
            activity.setCampaignId(sd.getCampaign().getId());
        } else if (sd.getCampaignId() != null) {
            activity.setCampaignId(sd.getCampaignId());
        }
    }
    
    public CombatActivity prepareActivity(String id) {
        CombatActivity activity = activityRepository.findById(id).orElse(null);
        if (activity != null) {
            activity.setStatus("READY");
            activity.setUpdatedAt(new Date());
            return activityRepository.save(activity);
        }
        return null;
    }
    
    public CombatActivity startActivity(String id) {
        CombatActivity activity = activityRepository.findById(id).orElse(null);
        if (activity != null) {
            activity.setStatus("EXECUTING");
            activity.setStartTime(new Date());
            activity.setUpdatedAt(new Date());
            return activityRepository.save(activity);
        }
        return null;
    }
    
    public CombatActivity pauseActivity(String id) {
        CombatActivity activity = activityRepository.findById(id).orElse(null);
        if (activity != null) {
            activity.setStatus("PAUSED");
            activity.setUpdatedAt(new Date());
            return activityRepository.save(activity);
        }
        return null;
    }
    
    public CombatActivity resumeActivity(String id) {
        CombatActivity activity = activityRepository.findById(id).orElse(null);
        if (activity != null) {
            activity.setStatus("EXECUTING");
            activity.setUpdatedAt(new Date());
            return activityRepository.save(activity);
        }
        return null;
    }
    
    public CombatActivity cancelActivity(String id) {
        CombatActivity activity = activityRepository.findById(id).orElse(null);
        if (activity != null) {
            activity.setStatus("CANCELLED");
            activity.setEndTime(new Date());
            activity.setExecutionDuration(calculateExecutionDuration(activity.getStartTime(), activity.getEndTime()));
            activity.setResult("CANCELLED");
            activity.setUpdatedAt(new Date());
            return activityRepository.save(activity);
        }
        return null;
    }
    
    public CombatActivity completeActivity(String id) {
        CombatActivity activity = activityRepository.findById(id).orElse(null);
        if (activity != null) {
            activity.setStatus("COMPLETED");
            activity.setEndTime(new Date());
            activity.setExecutionDuration(calculateExecutionDuration(activity.getStartTime(), activity.getEndTime()));
            activity.setResult("SUCCESS");
            activity.setSuccessRate(calculateSuccessRate(activity));
            activity.setUpdatedAt(new Date());
            CombatActivity savedActivity = activityRepository.save(activity);
            
            // 检查是否影响战役目标和触发关键事件
            checkCampaignIntegration(savedActivity);
            
            return savedActivity;
        }
        return null;
    }
    
    /**
     * 检查战役集成：更新目标状态和触发事件
     */
    private void checkCampaignIntegration(CombatActivity activity) {
        if (activity == null) return;
        
        // 更新战役目标状态
        campaignService.checkAndUpdateObjectivesByActivity(activity);
        
        // 检查关键事件触发
        campaignService.checkEventTriggers(activity);
    }
    
    public CombatActivity failActivity(String id, String reason) {
        CombatActivity activity = activityRepository.findById(id).orElse(null);
        if (activity != null) {
            activity.setStatus("FAILED");
            activity.setEndTime(new Date());
            activity.setExecutionDuration(calculateExecutionDuration(activity.getStartTime(), activity.getEndTime()));
            activity.setResult("FAILED: " + reason);
            activity.setSuccessRate(0);
            activity.setUpdatedAt(new Date());
            return activityRepository.save(activity);
        }
        return null;
    }
    
    // --- 活动查询方法 ---
    public List<CombatActivity> getAllActivities() {
        return activityRepository.findAll();
    }
    
    public List<CombatActivity> getActivitiesBySide(String side) {
        return activityRepository.findBySide(side);
    }
    
    public List<CombatActivity> getActivitiesByStatus(String status) {
        return activityRepository.findByStatus(status);
    }
    
    public List<CombatActivity> getActiveActivities(int currentRound) {
        List<CombatActivity> list = activityRepository.findActiveActivitiesByRound(currentRound);
        String active = scenarioService.getActiveScenarioId();
        if (active == null || active.isEmpty()) {
            return list;
        }
        return list.stream()
                .filter(a -> {
                    String sid = a.getScenarioId();
                    return active.equals(sid);
                })
                .collect(Collectors.toList());
    }

    /**
     * 当前回合是否存在应执行的作战活动（与当前想定过滤一致）。
     * 用于推演引擎：有活动时进入「想定任务驱动」，不执行全场自主交战。
     */
    public boolean hasTaskDrivingActivities(int currentRound) {
        return !getActiveActivities(currentRound).isEmpty();
    }

    /** 活动建模列表：按想定严格过滤。 */
    public List<CombatActivity> getActivitiesForEditor(String scenarioId) {
        List<CombatActivity> all = activityRepository.findAll();
        if (scenarioId == null || scenarioId.isEmpty()) {
            return all;
        }
        return all.stream()
                .filter(a -> {
                    String sid = a.getScenarioId();
                    return scenarioId.equals(sid);
                })
                .collect(Collectors.toList());
    }

    /**
     * 批量推演「同想定重放」前，将活动恢复为计划态，避免上一轮执行状态影响下一轮。
     */
    public void resetActivitiesForReplay(String scenarioId) {
        if (scenarioId == null || scenarioId.isEmpty()) {
            return;
        }
        for (CombatActivity a : activityRepository.findByScenarioId(scenarioId)) {
            if (a == null) {
                continue;
            }
            a.setStatus("PLANNED");
            a.setCurrentStep(0);
            a.setCurrentStepRound(0);
            a.setStartTime(null);
            a.setEndTime(null);
            a.setExecutionDuration(0);
            a.setResult(null);
            a.setSuccessRate(0);
            if (a.getSteps() != null) {
                for (CombatActivity.ActivityStep s : a.getSteps()) {
                    if (s == null) {
                        continue;
                    }
                    s.setCompleted(false);
                    s.setCurrentRound(0);
                    s.setStatus("PENDING");
                    s.setStartTime(null);
                    s.setEndTime(null);
                    s.setResult(null);
                }
            }
            activityRepository.save(a);
        }
    }

    /**
     * 删除指定想定下、名称以某前缀开头的作战活动（供 AI 正式落库前「替换旧条」使用，当前与 [AI] 前缀配合）。
     */
    public int deleteActivitiesWithNamePrefixForScenario(String scenarioId, String namePrefix) {
        if (scenarioId == null || scenarioId.isEmpty() || namePrefix == null || namePrefix.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (CombatActivity a : activityRepository.findByScenarioId(scenarioId)) {
            if (a == null || a.getName() == null) {
                continue;
            }
            if (a.getName().startsWith(namePrefix)) {
                activityRepository.deleteById(a.getId());
                n++;
            }
        }
        return n;
    }
    
    public List<CombatActivity> getActivitiesByCampaign(String campaignId) {
        return activityRepository.findByCampaignId(campaignId);
    }
    
    public List<CombatActivity> getChildActivities(String parentActivityId) {
        return activityRepository.findByParentActivityId(parentActivityId);
    }
    
    public List<CombatActivity> getActivitiesByObjective(String objectiveId) {
        return activityRepository.findByObjectiveId(objectiveId);
    }
    
    // --- 活动执行方法 ---
    public CombatActivity executeActivity(String id) {
        CombatActivity activity = activityRepository.findById(id).orElse(null);
        if (activity != null) {
            if (activity.getStatus().equals("PLANNED")) {
                activity = prepareActivity(id);
            }
            if (activity.getStatus().equals("READY")) {
                activity = startActivity(id);
            }
            if (activity.getStatus().equals("EXECUTING")) {
                executeActivityStep(activity);
                // 检查活动是否完成
                if (activity.getCurrentStep() >= activity.getSteps().size()) {
                    activity = completeActivity(id);
                }
            }
        }
        return activity;
    }
    
    /**
     * 执行活动步骤
     */
    public void executeActivityStep(CombatActivity activity) {
        if (activity.getSteps() == null || activity.getSteps().isEmpty()) {
            return;
        }
        
        if (activity.getCurrentStep() >= activity.getSteps().size()) {
            activity.setStatus("COMPLETED");
            activity.setEndTime(new Date());
            activity.setExecutionDuration(calculateExecutionDuration(activity.getStartTime(), activity.getEndTime()));
            activity.setResult("SUCCESS");
            activity.setSuccessRate(calculateSuccessRate(activity));
            activity.setUpdatedAt(new Date());
            activityRepository.save(activity);
            return;
        }
        
        CombatActivity.ActivityStep step = activity.getSteps().get(activity.getCurrentStep());
        
        // 检查步骤是否已完成
        if (step.isCompleted()) {
            activity.setCurrentStep(activity.getCurrentStep() + 1);
            activity.setCurrentStepRound(0);
            activity.setUpdatedAt(new Date());
            activityRepository.save(activity);
            return;
        }
        
        // 设置步骤状态为执行中
        if (step.getStatus() == null || step.getStatus().equals("PENDING")) {
            step.setStatus("EXECUTING");
            step.setStartTime(new Date());
        }
        
        // 增加当前步骤执行的回合数
        step.setCurrentRound(step.getCurrentRound() + 1);
        
        // 根据步骤类型执行相应动作
        boolean stepCompleted = false;
        switch (step.getAction()) {
            case "MOVE":
                stepCompleted = executeMoveStep(activity, step);
                break;
            case "ATTACK":
                stepCompleted = executeAttackStep(activity, step);
                break;
            case "DEFEND":
                stepCompleted = executeDefendStep(activity, step);
                break;
            case "RECON":
                stepCompleted = executeReconStep(activity, step);
                break;
            case "SUPPORT":
                stepCompleted = executeSupportStep(activity, step);
                break;
            case "FIRE":
                stepCompleted = executeFireStep(activity, step);
                break;
            case "RESUPPLY":
                stepCompleted = executeResupplyStep(activity, step);
                break;
            case "COMMUNICATE":
                stepCompleted = executeCommunicateStep(activity, step);
                break;
            case "WAIT":
                stepCompleted = executeWaitStep(activity, step);
                break;
            case "COORDINATE":
                stepCompleted = executeCoordinateStep(activity, step);
                break;
        }
        
        // 检查步骤是否完成
        if (stepCompleted || step.getCurrentRound() >= step.getRoundDuration()) {
            step.setCompleted(true);
            step.setStatus("COMPLETED");
            step.setEndTime(new Date());
            step.setResult("SUCCESS");
            activity.setCurrentStep(activity.getCurrentStep() + 1);
            activity.setCurrentStepRound(0);
        }
        
        activity.setUpdatedAt(new Date());
        activityRepository.save(activity);
    }
    
    private CombatObjective resolveCombatObjectiveForActivity(CombatActivity activity) {
        if (activity == null || activity.getObjectiveId() == null || activity.getObjectiveId().isEmpty()) {
            return null;
        }
        String oid = activity.getObjectiveId();
        String sid = activity.getScenarioId() != null && !activity.getScenarioId().isEmpty()
                ? activity.getScenarioId()
                : scenarioService.getActiveScenarioId();
        if (sid != null && !sid.isEmpty()) {
            for (CombatObjective o : scenarioService.getObjectivesForScenario(sid)) {
                if (oid.equals(o.getId())) {
                    return o;
                }
            }
        }
        return objectiveRepository.findById(oid).orElse(null);
    }

    private boolean executeMoveStep(CombatActivity activity, CombatActivity.ActivityStep step) {
        // 移动逻辑：让参与活动的单位移动到目标位置（显式坐标 / 作战目标经纬度或百分比坐标）
        if (activity.getUnitIds() != null) {
            CombatObjective linkedObjective = resolveCombatObjectiveForActivity(activity);
            for (String unitId : activity.getUnitIds()) {
                CombatUnit unit = unitRepository.findById(unitId).orElse(null);
                if (unit != null && unit.getCombatPower() > 0) {
                    Double targetLat = activity.getTargetLatitude();
                    Double targetLon = activity.getTargetLongitude();

                    if (targetLat == null || targetLon == null) {
                        if (linkedObjective != null && linkedObjective.getLatitude() != null && linkedObjective.getLongitude() != null) {
                            targetLat = linkedObjective.getLatitude();
                            targetLon = linkedObjective.getLongitude();
                        }
                    }

                    if (targetLat != null && targetLon != null && unit.getLatitude() != null && unit.getLongitude() != null) {
                        double moveDistance = Math.max(unit.getSpeed() * 1000, 5000);
                        double[] newPos = GeoUtils.moveTowards(
                                unit.getLatitude(), unit.getLongitude(),
                                targetLat, targetLon,
                                moveDistance);
                        unit.setLatitude(newPos[0]);
                        unit.setLongitude(newPos[1]);
                        unitRepository.save(unit);
                    } else if (linkedObjective != null
                            && (linkedObjective.getX() != 0 || linkedObjective.getY() != 0)) {
                        double ox = linkedObjective.getX();
                        double oy = linkedObjective.getY();
                        double dx = ox - unit.getX();
                        double dy = oy - unit.getY();
                        double len = Math.sqrt(dx * dx + dy * dy);
                        if (len > 1e-9) {
                            double move = Math.min(0.04, len * 0.45);
                            unit.setX(unit.getX() + dx / len * move);
                            unit.setY(unit.getY() + dy / len * move);
                            unitRepository.save(unit);
                        }
                    }
                }
            }
        }
        return true;
    }
    
    private boolean executeAttackStep(CombatActivity activity, CombatActivity.ActivityStep step) {
        // 攻击逻辑：让参与活动的单位攻击目标
        if (activity.getUnitIds() != null) {
            List<CombatUnit> allUnits = unitRepository.findAll();
            for (String unitId : activity.getUnitIds()) {
                CombatUnit unit = unitRepository.findById(unitId).orElse(null);
                if (unit != null && unit.getCombatPower() > 0) {
                    // 查找目标
                    final CombatUnit target;
                    if (step.getTarget() != null) {
                        // 如果指定了目标ID
                        target = unitRepository.findById(step.getTarget()).orElse(null);
                    } else {
                        // 否则查找最近的敌方单位
                        target = allUnits.stream()
                            .filter(u -> !u.getSide().equals(unit.getSide()) && u.getCombatPower() > 0)
                            .min((u1, u2) -> {
                                double d1 = calculateDistance(unit, u1);
                                double d2 = calculateDistance(unit, u2);
                                return Double.compare(d1, d2);
                            })
                            .orElse(null);
                    }
                    
                    // 执行攻击
                    if (target != null && calculateDistance(unit, target) <= unit.getAttackRange()) {
                        int currentRound = scenarioService.getCurrentRound();
                        List<InteractionRule> applicableRules = interactionRuleService.getEnabledInteractionRulesForEngine().stream()
                            .filter(rule -> interactionRuleService.ruleAppliesToPair(rule, unit, target, currentRound))
                            .collect(Collectors.toList());
                        
                        // 计算伤害
                        SplittableRandom rng = simulationRandom.rng("ACTIVITY_ATTACK",
                                scenarioService.getCurrentRound(),
                                unit.getId() + "->" + (target.getId() == null ? "" : target.getId()));
                        int baseDamage = 20 + rng.nextInt(10);
                        double typeMultiplier = 1.0;
                        if ("TANK".equals(unit.getType()) && "INFANTRY".equals(target.getType())) typeMultiplier = 1.5;
                        if ("INFANTRY".equals(unit.getType()) && "TANK".equals(target.getType())) typeMultiplier = 0.3;
                        if ("MECH_INFANTRY".equals(unit.getType()) && "INFANTRY".equals(target.getType())) typeMultiplier = 1.2;
                        if ("ARTILLERY".equals(unit.getType()) && "TANK".equals(target.getType())) typeMultiplier = 1.3;
                        if ("ROCKET_ARTILLERY".equals(unit.getType()) && "TANK".equals(target.getType())) typeMultiplier = 1.4;
                        if ("AA_GUN".equals(unit.getType()) && "FIGHTER".equals(target.getType())) typeMultiplier = 1.5;
                        if ("FIGHTER".equals(unit.getType()) && "INFANTRY".equals(target.getType())) typeMultiplier = 1.3;
                        
                        // 应用交互规则的伤害加成
                        double ruleDamageMultiplier = 1.0;
                        for (InteractionRule rule : applicableRules) {
                            if ("DAMAGE_BOOST".equals(rule.getEffectType())) {
                                ruleDamageMultiplier *= rule.getEffectValue();
                            }
                        }
                        
                        int finalDamage = (int) (baseDamage * typeMultiplier * ruleDamageMultiplier);
                        target.setCombatPower(target.getCombatPower() - finalDamage);
                        
                        // 检查目标是否被摧毁
                        if (target.getCombatPower() <= 0) {
                            target.setCombatPower(0);
                            target.setStatus("DESTROYED");
                        }
                        
                        // 保存单位状态
                        unitRepository.save(target);
                    }
                }
            }
        }
        return true;
    }
    
    private boolean executeDefendStep(CombatActivity activity, CombatActivity.ActivityStep step) {
        // 防守逻辑：依托作战目标位置回撤/驻扎，并小幅恢复战力
        if (activity.getUnitIds() == null) {
            return true;
        }
        CombatObjective linkedObjective = resolveCombatObjectiveForActivity(activity);
        for (String unitId : activity.getUnitIds()) {
            CombatUnit unit = unitRepository.findById(unitId).orElse(null);
            if (unit == null || unit.getCombatPower() <= 0) {
                continue;
            }
            if (linkedObjective != null && objectiveDirectiveService.hasObjectivePosition(linkedObjective)) {
                if (!objectiveDirectiveService.isWithinDefendHold(unit, linkedObjective)) {
                    objectiveDirectiveService.moveUnitOneStepTowardsObjective(unit, linkedObjective, terrainService, combatUnitService);
                    continue;
                }
            }
            int max = unit.getMaxPower() > 0 ? unit.getMaxPower() : unit.getCombatPower();
            int heal = Math.max(1, (int) (max * 0.025));
            unit.setCombatPower(Math.min(max, unit.getCombatPower() + heal));
            combatUnitService.updateUnit(unit);
        }
        return true;
    }
    
    private boolean executeReconStep(CombatActivity activity, CombatActivity.ActivityStep step) {
        // 侦察逻辑：单位执行侦察任务
        if (activity.getUnitIds() != null) {
            for (String unitId : activity.getUnitIds()) {
                CombatUnit unit = unitRepository.findById(unitId).orElse(null);
                if (unit != null && unit.getCombatPower() > 0) {
                    // 执行侦察操作
                    // 这里可以添加侦察逻辑，如发现敌方单位等
                }
            }
        }
        return true;
    }
    
    private boolean executeSupportStep(CombatActivity activity, CombatActivity.ActivityStep step) {
        // 支援逻辑：单位为友方提供支援
        if (activity.getUnitIds() != null) {
            for (String unitId : activity.getUnitIds()) {
                CombatUnit unit = unitRepository.findById(unitId).orElse(null);
                if (unit != null && unit.getCombatPower() > 0) {
                    // 执行支援操作
                    // 这里可以添加支援逻辑，如治疗、修复等
                }
            }
        }
        return true;
    }
    
    private boolean executeFireStep(CombatActivity activity, CombatActivity.ActivityStep step) {
        // 开火逻辑：单位进行火力打击
        if (activity.getUnitIds() != null) {
            for (String unitId : activity.getUnitIds()) {
                CombatUnit unit = unitRepository.findById(unitId).orElse(null);
                if (unit != null && unit.getCombatPower() > 0) {
                    // 执行开火操作
                    // 这里可以添加火力打击逻辑
                }
            }
        }
        return true;
    }
    
    private boolean executeResupplyStep(CombatActivity activity, CombatActivity.ActivityStep step) {
        // 补给逻辑：单位进行补给
        if (activity.getUnitIds() != null) {
            for (String unitId : activity.getUnitIds()) {
                CombatUnit unit = unitRepository.findById(unitId).orElse(null);
                if (unit != null && unit.getCombatPower() > 0) {
                    // 执行补给操作
                    // 这里可以添加补给逻辑，如恢复生命值等
                    unit.setCombatPower(Math.min(unit.getCombatPower() + 50, unit.getMaxPower()));
                    unitRepository.save(unit);
                }
            }
        }
        return true;
    }
    
    private boolean executeCommunicateStep(CombatActivity activity, CombatActivity.ActivityStep step) {
        // 通信逻辑：单位进行通信
        if (activity.getUnitIds() != null) {
            for (String unitId : activity.getUnitIds()) {
                CombatUnit unit = unitRepository.findById(unitId).orElse(null);
                if (unit != null && unit.getCombatPower() > 0) {
                    // 执行通信操作
                    // 这里可以添加通信逻辑，如传递情报等
                }
            }
        }
        return true;
    }
    
    private boolean executeWaitStep(CombatActivity activity, CombatActivity.ActivityStep step) {
        // 等待逻辑：单位保持当前位置
        return step.getRoundDuration() <= step.getCurrentRound();
    }
    
    private boolean executeCoordinateStep(CombatActivity activity, CombatActivity.ActivityStep step) {
        // 协同作战逻辑：多个单位协同行动
        if (activity.getUnitIds() != null && activity.getUnitIds().size() > 1) {
            // 创建协同作战行动
            CoordinationAction coordinationAction = new CoordinationAction();
            coordinationAction.setId(java.util.UUID.randomUUID().toString());
            coordinationAction.setName(activity.getName() + " - 协同行动");
            coordinationAction.setDescription("由活动 " + activity.getName() + " 触发的协同作战行动");
            coordinationAction.setSide(activity.getSide());
            coordinationAction.setUnitIds(activity.getUnitIds());
            coordinationAction.setDuration(step.getRoundDuration());
            coordinationAction.setCurrentRound(0);
            coordinationAction.setStatus("PLANNED");
            
            // 保存协同作战行动
            CoordinationAction savedAction = coordinationService.addCoordinationAction(coordinationAction);
            
            // 执行协同作战行动
            coordinationService.executeCoordinationAction(savedAction, 0);
        }
        return true;
    }
    
    // --- 活动模板管理 ---
    /** 与旧全参构造等价，避免 ActivityStep 保留 Lombok 全参构造导致 Jackson 反序列化失败 */
    private static CombatActivity.ActivityStep activityTemplateStep(int stepNumber, String name, String description,
            String action, String target, String targetType, int roundDuration) {
        CombatActivity.ActivityStep s = new CombatActivity.ActivityStep();
        s.setStepNumber(stepNumber);
        s.setName(name);
        s.setDescription(description);
        s.setAction(action);
        s.setTarget(target);
        s.setTargetType(targetType);
        s.setRoundDuration(roundDuration);
        s.setCurrentRound(0);
        s.setCompleted(false);
        s.setStatus("PENDING");
        s.setLogs(new ArrayList<>());
        return s;
    }

    public List<CombatActivity> getActivityTemplates() {
        List<CombatActivity> templates = new ArrayList<>();
        
        // 进攻活动模板
        CombatActivity attackTemplate = new CombatActivity();
        attackTemplate.setName("标准进攻");
        attackTemplate.setType("ATTACK");
        attackTemplate.setDescription("标准进攻活动：移动-攻击-占领");
        List<CombatActivity.ActivityStep> attackSteps = new ArrayList<>();
        attackSteps.add(activityTemplateStep(1, "集结", "单位向集结地点移动", "MOVE", "", "LOCATION", 2));
        attackSteps.add(activityTemplateStep(2, "攻击", "向目标发起攻击", "ATTACK", "", "UNIT", 3));
        attackSteps.add(activityTemplateStep(3, "占领", "占领目标区域", "MOVE", "", "AREA", 1));
        attackTemplate.setSteps(attackSteps);
        templates.add(attackTemplate);
        
        // 防守活动模板
        CombatActivity defendTemplate = new CombatActivity();
        defendTemplate.setName("阵地防守");
        defendTemplate.setType("DEFEND");
        defendTemplate.setDescription("阵地防守活动：建立防线-坚守");
        List<CombatActivity.ActivityStep> defendSteps = new ArrayList<>();
        defendSteps.add(activityTemplateStep(1, "建立防线", "单位移动到防守位置", "MOVE", "", "LOCATION", 2));
        defendSteps.add(activityTemplateStep(2, "坚守", "坚守阵地", "DEFEND", "", "AREA", -1));
        defendTemplate.setSteps(defendSteps);
        templates.add(defendTemplate);
        
        // 侦察活动模板
        CombatActivity reconTemplate = new CombatActivity();
        reconTemplate.setName("侦察任务");
        reconTemplate.setType("RECON");
        reconTemplate.setDescription("侦察活动：快速移动-侦察-返回");
        List<CombatActivity.ActivityStep> reconSteps = new ArrayList<>();
        reconSteps.add(activityTemplateStep(1, "前往目标", "快速移动到侦察区域", "MOVE", "", "LOCATION", 2));
        reconSteps.add(activityTemplateStep(2, "侦察", "执行侦察任务", "RECON", "", "AREA", 1));
        reconSteps.add(activityTemplateStep(3, "返回", "返回基地", "MOVE", "", "LOCATION", 2));
        reconTemplate.setSteps(reconSteps);
        templates.add(reconTemplate);
        
        // 支援活动模板
        CombatActivity supportTemplate = new CombatActivity();
        supportTemplate.setName("火力支援");
        supportTemplate.setType("SUPPORT");
        supportTemplate.setDescription("火力支援活动：移动到支援位置-提供火力支援");
        List<CombatActivity.ActivityStep> supportSteps = new ArrayList<>();
        supportSteps.add(activityTemplateStep(1, "移动到位", "移动到支援位置", "MOVE", "", "LOCATION", 2));
        supportSteps.add(activityTemplateStep(2, "火力支援", "为友军提供火力支援", "FIRE", "", "AREA", 3));
        supportTemplate.setSteps(supportSteps);
        templates.add(supportTemplate);
        
        // 撤退活动模板
        CombatActivity retreatTemplate = new CombatActivity();
        retreatTemplate.setName("有序撤退");
        retreatTemplate.setType("RETREAT");
        retreatTemplate.setDescription("有序撤退活动：掩护-撤退-集结");
        List<CombatActivity.ActivityStep> retreatSteps = new ArrayList<>();
        retreatSteps.add(activityTemplateStep(1, "掩护", "部分单位进行掩护", "DEFEND", "", "AREA", 2));
        retreatSteps.add(activityTemplateStep(2, "撤退", "主力单位撤退", "MOVE", "", "LOCATION", 3));
        retreatSteps.add(activityTemplateStep(3, "集结", "撤退到集结地点", "MOVE", "", "LOCATION", 1));
        retreatTemplate.setSteps(retreatSteps);
        templates.add(retreatTemplate);
        
        // 补给活动模板
        CombatActivity resupplyTemplate = new CombatActivity();
        resupplyTemplate.setName("战场补给");
        resupplyTemplate.setType("RESUPPLY");
        resupplyTemplate.setDescription("战场补给活动：前往补给点-进行补给");
        List<CombatActivity.ActivityStep> resupplySteps = new ArrayList<>();
        resupplySteps.add(activityTemplateStep(1, "前往补给点", "移动到补给点", "MOVE", "", "LOCATION", 2));
        resupplySteps.add(activityTemplateStep(2, "进行补给", "补充弹药和燃料", "RESUPPLY", "", "LOCATION", 1));
        resupplyTemplate.setSteps(resupplySteps);
        templates.add(resupplyTemplate);
        
        return templates;
    }
    
    // --- 辅助方法 ---
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
    
    private long calculateExecutionDuration(Date startTime, Date endTime) {
        if (startTime != null && endTime != null) {
            return endTime.getTime() - startTime.getTime();
        }
        return 0;
    }
    
    private int calculateSuccessRate(CombatActivity activity) {
        // 简单计算成功率，实际应该根据活动执行情况进行更复杂的评估
        int successRate = 70; // 基础成功率
        
        // 根据活动类型调整成功率
        switch (activity.getType()) {
            case "ATTACK":
                successRate += 10;
                break;
            case "DEFEND":
                successRate += 15;
                break;
            case "RECON":
                successRate += 20;
                break;
            case "SUPPORT":
                successRate += 15;
                break;
        }
        
        // 根据单位数量调整成功率
        if (activity.getUnitIds() != null && activity.getUnitIds().size() > 3) {
            successRate += 10;
        }
        
        // 确保成功率在0-100之间
        return Math.min(100, Math.max(0, successRate));
    }
    
    // --- 活动管理方法 ---
    public void deleteActivity(String id) {
        activityRepository.deleteById(id);
    }
    
    public CombatActivity updateActivity(CombatActivity activity) {
        activity.setUpdatedAt(new Date());
        return activityRepository.save(activity);
    }
    
    public CombatActivity getActivityById(String id) {
        return activityRepository.findById(id).orElse(null);
    }
}
