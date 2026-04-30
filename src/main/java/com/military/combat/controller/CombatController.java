package com.military.combat.controller;

import com.military.combat.battleline.BattlelineOverview;
import com.military.combat.battleline.BattlelineService;
import com.military.combat.entity.*;
import com.military.combat.entity.Campaign;
import com.military.combat.repository.InteractionEventRepository;
import com.military.combat.service.*;
import com.military.combat.simulation.SimulationFacadeService;
import com.military.combat.simulation.SimulationState;
import com.military.combat.util.NavalDomainValidator;
import com.military.combat.controller.dto.FullScenarioCreateRequest;
import com.military.combat.controller.dto.ScenarioBasicUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/combat")
public class CombatController {

    @Autowired
    private CombatUnitService unitService;

    @Autowired
    private CombatActivityService activityService;

    @Autowired
    private InteractionRuleService ruleService;

    @Autowired
    private InteractionProcessService processService;

    @Autowired
    private CoordinationService coordinationService;

    @Autowired
    private TerrainService terrainService;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatEngineService engineService;

    @Autowired
    private AiScenarioService aiScenarioService;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private SimulationFacadeService simulationFacadeService;

    @Autowired
    private BattlelineService battlelineService;

    @Autowired
    private ScenarioActivationService scenarioActivationService;

    @Autowired
    private InteractionLegacyMigrationService interactionLegacyMigrationService;

    @Autowired
    private InteractionEventRepository interactionEventRepository;

    @Autowired
    private SortieMissionService sortieMissionService;

    @Autowired
    private CommandChainService commandChainService;

    @Autowired
    private OpposingActionService opposingActionService;

    @Autowired
    private AiAutoModelingService aiAutoModelingService;

    @PostMapping("/add")
    public CombatUnit addUnit(@RequestBody CombatUnit unit) {
        return unitService.addUnit(unit);
    }
    
    @PostMapping("/update")
    public CombatUnit updateUnit(@RequestBody CombatUnit unit) {
        return unitService.updateUnit(unit);
    }
    
    @PostMapping("/delete")
    public String deleteUnit(@RequestParam String id) {
        unitService.deleteUnit(id);
        return "单位删除成功";
    }

    @GetMapping("/list")
    public List<CombatUnit> listUnits() {
        return unitService.getVisibleUnitsForList();
    }

    @PostMapping("/start")
    public List<BattleEvent> startSimulation() {
        return engineService.simulateRound();
    }

    /** 推演会话统一快照（推荐前端只调此接口） */
    @GetMapping("/simulation/state")
    public SimulationState simulationState() {
        return simulationFacadeService.getState();
    }

    /**
     * 当前战役主线聚合：战役 → 想定兵力部署 → 活动命令条数 → 交互规则/过程条数。
     */
    @GetMapping("/battleline/current")
    public BattlelineOverview battlelineCurrent() {
        return battlelineService.getForActiveScenario();
    }

    @GetMapping("/battleline/scenario/{scenarioId}")
    public BattlelineOverview battlelineForScenario(@PathVariable String scenarioId) {
        return battlelineService.buildForScenarioId(scenarioId);
    }

    /** 列出某自定义战役下已保存的想定（兵力部署存档） */
    @GetMapping("/campaign/{campaignId}/scenarios")
    public List<ScenarioData> listScenariosForCampaign(@PathVariable String campaignId) {
        return battlelineService.listScenariosForCampaign(campaignId);
    }

    /**
     * @deprecated 请使用 {@link #simulationState()}，保留仅为兼容旧前端。
     */
    @Deprecated
    @GetMapping("/engine/task-driven")
    public Map<String, Object> taskDrivenStatus() {
        SimulationState s = simulationFacadeService.getState();
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("taskDriven", s.isTaskDriven());
        m.put("round", s.getRound());
        return m;
    }

    @PostMapping("/reset")
    public String resetSystem() {
        engineService.resetScenario();
        return "系统已重置";
    }

    @GetMapping("/terrains")
    public List<Terrain> getTerrains() {
        return terrainService.getTerrains();
    }

    @GetMapping("/stats")
    public List<RoundStat> getStats() {
        return scenarioService.getStats();
    }
    
    @Value("${baidu.map.api.key:YOUR_BAIDU_MAP_KEY}")
    private String baiduMapApiKey;

    @GetMapping("/map/config")
    public Map<String, Object> getMapConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("useRealMap", true);
        config.put("defaultCenter", new double[]{39.9042, 116.4074}); // 北京天安门
        config.put("defaultZoom", 12);
        config.put("baiduMapKey", baiduMapApiKey);
        config.put("apiKeyConfigured", baiduMapApiKey != null && !baiduMapApiKey.equals("YOUR_BAIDU_MAP_KEY"));
        return config;
    }

    // --- 存档相关接口 ---

    // --- 作战目标管理API ---
    @PostMapping("/objective/add")
    public CombatObjective addObjective(@RequestBody CombatObjective objective) {
        NavalDomainValidator.normalizeAndValidateObjective(objective);
        if (objective.getId() == null) {
            objective.setId(java.util.UUID.randomUUID().toString());
        }
        objective.setCompleted(false);
        if (objective.getX() == 0 && objective.getY() == 0 && objective.getLatitude() != null && objective.getLongitude() != null) {
            objective.setX(0.0);
            objective.setY(0.0);
        }
        String sid = objective.getScenarioId();
        if (sid != null && !sid.isEmpty()) {
            scenarioService.addObjectiveToScenario(sid, objective);
        }
        objective.setScenarioId(null);
        return objective;
    }
    
    @GetMapping("/objective/list")
    public List<CombatObjective> listObjectives(@RequestParam(required = false) String scenarioId) {
        String sid = scenarioId;
        if (sid == null || sid.isEmpty()) {
            sid = scenarioService.getActiveScenarioId();
        }
        if (sid != null && !sid.isEmpty()) {
            return scenarioService.getObjectivesForScenario(sid);
        }
        return new java.util.ArrayList<>();
    }
    
    @GetMapping("/objective/side/{side}")
    public List<CombatObjective> getObjectivesBySide(@PathVariable String side, @RequestParam(required = false) String scenarioId) {
        return listObjectives(scenarioId).stream()
                .filter(o -> side.equals(o.getSide()))
                .collect(java.util.stream.Collectors.toList());
    }
    
    @PostMapping("/objective/delete")
    public String deleteObjective(@RequestParam String id, @RequestParam(required = false) String scenarioId) {
        if (scenarioId != null && !scenarioId.isEmpty()) {
            scenarioService.removeObjectiveFromScenario(scenarioId, id);
        }
        return "目标删除成功";
    }

    /** 设置当前工作想定（活动/规则/推演与想定内目标联动）；若文档含战役则同步到推演会话 */
    @PostMapping("/scenario-data/set-active")
    public Map<String, Object> setActiveScenario(@RequestParam(required = false) String id) {
        scenarioActivationService.activateScenario(id);
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("activeScenarioId", id);
        m.put("mainLine", battlelineService.getMainLineForActiveScenario());
        return m;
    }

    @GetMapping("/scenario-data/active")
    public Map<String, Object> getActiveScenario() {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("activeScenarioId", scenarioService.getActiveScenarioId());
        return m;
    }
    
    // --- 作战想定数据管理API ---
    @PostMapping("/scenario-data/save")
    public ScenarioData saveScenarioData(@RequestBody ScenarioData data) {
        try {
            NavalDomainValidator.normalizeAndValidateScenario(data);
            return scenarioService.saveScenarioData(data);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("保存想定数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 指挥官新建想定（完整）：名称/海域中心/目标描述 + 用户布置的单位与目标清单，一次性保存并激活。
     * 说明：
     * - 单位与目标若未填 id，服务端会自动补齐；
     * - 保存后会自动激活，并把想定内单位投影到战场单位集合（供地图/推演读取）。
     */
    @PostMapping("/scenario-data/create-full")
    public Map<String, Object> createFullScenario(@RequestBody(required = false) FullScenarioCreateRequest body) {
        FullScenarioCreateRequest req = body == null ? new FullScenarioCreateRequest() : body;

        ScenarioData sd = new ScenarioData();
        sd.setName((req.getName() == null || req.getName().trim().isEmpty()) ? "新建海上想定" : req.getName().trim());
        sd.setDescription(req.getDescription());
        sd.setScenarioDomain("SEA");
        sd.setEvaluationProfile("NAVAL_BALANCED");
        sd.setSaveType("TEMPLATE");
        sd.setMaxRounds(req.getMaxRounds() == null ? 50 : Math.max(10, Math.min(500, req.getMaxRounds())));
        sd.setVictoryCondition("NAVAL_OBJECTIVES");
        sd.setRedSideName("红方");
        sd.setBlueSideName("蓝方");

        // 用户布置：单位/目标清单（可为空）
        if (req.getUnits() != null) {
            sd.setUnits(req.getUnits());
        }
        if (req.getObjectives() != null) {
            sd.setObjectives(req.getObjectives());
        }

        // 若没有任何目标，补一个默认海上目标，避免空想定无法评估
        if (sd.getObjectives() == null || sd.getObjectives().isEmpty()) {
            CombatObjective obj = new CombatObjective();
            obj.setId(java.util.UUID.randomUUID().toString());
            obj.setName("控制关键海域");
            obj.setType("CAPTURE");
            obj.setSide("RED");
            obj.setPriority(8);
            obj.setCompleted(false);
            obj.setObjectiveType("AREA_CONTROL");
            obj.setTargetAreaCode("SEA_AREA_DEFAULT");
            obj.setControlThreshold(0.6);
            obj.setDescription(req.getGoal() == null ? "夺取并保持关键海域控制权" : req.getGoal());
            if (req.getCenterLatitude() != null && req.getCenterLongitude() != null) {
                obj.setLatitude(req.getCenterLatitude());
                obj.setLongitude(req.getCenterLongitude());
            }
            sd.setObjectives(java.util.List.of(obj));
        }

        ScenarioData saved = scenarioService.saveScenarioData(sd);
        scenarioActivationService.activateScenario(saved.getId());

        Map<String, Object> out = new java.util.HashMap<>();
        out.put("scenario", saved);
        out.put("activeScenarioId", saved.getId());
        out.put("mainLine", battlelineService.getMainLineForActiveScenario());
        return out;
    }
    
    @GetMapping("/scenario-data/list")
    public List<ScenarioData> listScenarioData() {
        return scenarioService.getAllScenarioData();
    }
    
    @GetMapping("/scenario-data/{id}")
    public ScenarioData getScenarioData(@PathVariable String id) {
        return scenarioService.getScenarioDataById(id);
    }
    
    @PostMapping("/scenario-data/delete")
    public String deleteScenarioData(@RequestParam String id) {
        // 若删除当前激活想定，则先取消激活并清空战场单位集合
        String active = scenarioService.getActiveScenarioId();
        if (active != null && active.equals(id)) {
            scenarioActivationService.activateScenario(null);
        }
        scenarioService.deleteScenarioData(id);
        return "想定数据删除成功";
    }

    /** 想定管理：仅更新基础字段（名称/描述/最大回合），避免覆盖 units/objectives 等结构。 */
    @PostMapping("/scenario-data/update-basic")
    public ScenarioData updateScenarioBasic(@RequestBody ScenarioBasicUpdateRequest req) {
        if (req == null || req.getId() == null || req.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("想定ID不能为空");
        }
        ScenarioData sd = scenarioService.getScenarioDataById(req.getId().trim());
        if (sd == null) {
            throw new IllegalArgumentException("想定不存在: " + req.getId());
        }
        if (req.getName() != null) {
            sd.setName(req.getName().trim());
        }
        if (req.getDescription() != null) {
            sd.setDescription(req.getDescription());
        }
        if (req.getMaxRounds() != null) {
            sd.setMaxRounds(Math.max(10, Math.min(500, req.getMaxRounds())));
        }
        ScenarioData saved = scenarioService.saveScenarioData(sd);
        // 若当前正在激活该想定，则重新激活以刷新战场单位投影
        String active = scenarioService.getActiveScenarioId();
        if (active != null && active.equals(saved.getId())) {
            scenarioActivationService.activateScenario(active);
        }
        return saved;
    }
    
    @PostMapping("/scenario-data/restore-progress")
    public String restoreProgress(@RequestParam String id) {
        scenarioService.restoreProgress(id);
        return "进度恢复成功";
    }
    
    // --- 武器装备模板API ---
    @GetMapping("/weapon/templates")
    public List<Weapon> getWeaponTemplates() {
        return unitService.getWeaponTemplates();
    }

    /** 现代化兵力模板：航母打击群/舰载航空兵等复合作战平台。 */
    @GetMapping("/unit/templates/advanced")
    public List<CombatUnit> getAdvancedUnitTemplates() {
        return unitService.getAdvancedUnitTemplates();
    }

    @GetMapping("/unit/groups/advanced")
    public List<String> getAdvancedGroupKeys() {
        return unitService.getAdvancedGroupKeys();
    }

    @PostMapping("/unit/groups/deploy")
    public List<CombatUnit> deployAdvancedGroup(@RequestParam String groupKey) {
        return unitService.deployAdvancedGroup(groupKey);
    }

    @GetMapping("/sortie/list")
    public List<SortieMission> listSorties() {
        return sortieMissionService.listForActiveScenario();
    }

    @PostMapping("/sortie/create")
    public SortieMission createSortie(@RequestBody SortieMission mission) {
        return sortieMissionService.createMission(mission);
    }

    @PostMapping("/sortie/run")
    public List<SortieMission> runSortieForRound() {
        return sortieMissionService.runForCurrentRound();
    }

    @GetMapping("/command-chain/list")
    public List<GroupCommandOrder> listCommandOrders() {
        return commandChainService.listOrders();
    }

    @PostMapping("/command-chain/create")
    public GroupCommandOrder createCommandOrder(@RequestBody GroupCommandOrder order) {
        return commandChainService.createOrder(order);
    }

    @PostMapping("/command-chain/run")
    public List<GroupCommandOrder> runCommandOrders() {
        return commandChainService.runOrdersForCurrentRound();
    }

    @GetMapping("/opposing/list")
    public List<OpposingAction> listOpposingActions() {
        return opposingActionService.listForActiveScenario();
    }

    @GetMapping("/opposing/templates")
    public List<OpposingActionService.OpposingStrategyTemplate> listOpposingTemplates() {
        return opposingActionService.strategyTemplates();
    }

    @PostMapping("/opposing/create")
    public OpposingAction createOpposingAction(@RequestBody OpposingAction action) {
        return opposingActionService.create(action);
    }

    @PostMapping("/opposing/run")
    public List<OpposingAction> runOpposingActions() {
        return opposingActionService.runForCurrentRound();
    }
    

    
    // --- 交互规则管理API ---
    @PostMapping("/interaction-rule/add")
    public InteractionRule addInteractionRule(@RequestBody InteractionRule rule) {
        return ruleService.addInteractionRule(rule);
    }
    
    @GetMapping("/interaction-rule/list")
    public List<InteractionRule> listInteractionRules(@RequestParam(required = false) String scenarioId) {
        return ruleService.getInteractionRulesForEditor(scenarioId);
    }
    
    @GetMapping("/interaction-rule/enabled")
    public List<InteractionRule> getEnabledInteractionRules() {
        return ruleService.getEnabledInteractionRulesForEngine();
    }
    
    @GetMapping("/interaction-rule/type/{type}")
    public List<InteractionRule> getInteractionRulesByType(@PathVariable String type) {
        return ruleService.getInteractionRulesByType(type);
    }
    
    @PostMapping("/interaction-rule/delete")
    public String deleteInteractionRule(@RequestParam String id) {
        ruleService.deleteInteractionRule(id);
        return "交互规则删除成功";
    }
    
    @GetMapping("/interaction-rule/templates")
    public List<InteractionRule> getInteractionRuleTemplates() {
        return ruleService.getInteractionRuleTemplates();
    }

    /** 一键迁移：将当前想定下交互过程/协同作战转换为交互规则。 */
    @PostMapping("/interaction-rule/migrate-legacy")
    public Map<String, Object> migrateLegacyToRules(@RequestBody(required = false) Map<String, Object> body) {
        boolean archive = body != null && Boolean.TRUE.equals(body.get("archiveLegacy"));
        return interactionLegacyMigrationService.migrateActiveScenarioToRules(archive);
    }
    
    // --- 交互过程管理API ---
    @PostMapping("/interaction-process/add")
    public InteractionProcess addInteractionProcess(@RequestBody InteractionProcess process) {
        return processService.createProcess(process);
    }
    
    @GetMapping("/interaction-process/list")
    public List<InteractionProcess> listInteractionProcesses(@RequestParam(required = false) String scenarioId) {
        return processService.getProcessesForEditor(scenarioId);
    }
    
    @GetMapping("/interaction-process/status/{status}")
    public List<InteractionProcess> getProcessesByStatus(@PathVariable String status) {
        return processService.getProcessesByStatus(status);
    }
    
    @GetMapping("/interaction-process/round/{round}")
    public List<InteractionProcess> getProcessesByRound(@PathVariable int round) {
        return processService.getProcessesByRound(round);
    }
    
    @PostMapping("/interaction-process/execute/{id}")
    public InteractionProcess executeProcess(@PathVariable String id) {
        InteractionProcess process = processService.getProcessById(id);
        if (process != null) {
            return processService.executeProcess(process);
        }
        return null;
    }
    
    @PostMapping("/interaction-process/cancel/{id}")
    public InteractionProcess cancelProcess(@PathVariable String id) {
        return processService.cancelProcess(id);
    }
    
    @PostMapping("/interaction-process/delete")
    public String deleteProcess(@RequestParam String id) {
        processService.deleteProcess(id);
        return "交互过程删除成功";
    }
    
    @GetMapping("/interaction-process/templates")
    public List<InteractionProcess> getProcessTemplates() {
        return processService.getProcessTemplates();
    }
    
    // --- 活动管理API ---
    @PostMapping("/activity/add")
    public CombatActivity addActivity(@RequestBody CombatActivity activity) {
        return activityService.createActivity(activity);
    }
    
    @GetMapping("/activity/list")
    public List<CombatActivity> listActivities(@RequestParam(required = false) String scenarioId) {
        if (scenarioId != null && !scenarioId.isEmpty()) {
            return activityService.getActivitiesForEditor(scenarioId);
        }
        return activityService.getAllActivities();
    }
    
    @GetMapping("/activity/side/{side}")
    public List<CombatActivity> getActivitiesBySide(@PathVariable String side) {
        return activityService.getActivitiesBySide(side);
    }
    
    @GetMapping("/activity/status/{status}")
    public List<CombatActivity> getActivitiesByStatus(@PathVariable String status) {
        return activityService.getActivitiesByStatus(status);
    }
    
    @GetMapping("/activity/campaign/{campaignId}")
    public List<CombatActivity> getActivitiesByCampaign(@PathVariable String campaignId) {
        return activityService.getActivitiesByCampaign(campaignId);
    }
    
    @GetMapping("/activity/objective/{objectiveId}")
    public List<CombatActivity> getActivitiesByObjective(@PathVariable String objectiveId) {
        return activityService.getActivitiesByObjective(objectiveId);
    }
    
    @PostMapping("/activity/prepare/{id}")
    public CombatActivity prepareActivity(@PathVariable String id) {
        return activityService.prepareActivity(id);
    }
    
    @PostMapping("/activity/start/{id}")
    public CombatActivity startActivity(@PathVariable String id) {
        return activityService.startActivity(id);
    }
    
    @PostMapping("/activity/pause/{id}")
    public CombatActivity pauseActivity(@PathVariable String id) {
        return activityService.pauseActivity(id);
    }
    
    @PostMapping("/activity/resume/{id}")
    public CombatActivity resumeActivity(@PathVariable String id) {
        return activityService.resumeActivity(id);
    }
    
    @PostMapping("/activity/cancel/{id}")
    public CombatActivity cancelActivity(@PathVariable String id) {
        return activityService.cancelActivity(id);
    }
    
    @PostMapping("/activity/complete/{id}")
    public CombatActivity completeActivity(@PathVariable String id) {
        return activityService.completeActivity(id);
    }
    
    @PostMapping("/activity/fail/{id}")
    public CombatActivity failActivity(@PathVariable String id, @RequestParam String reason) {
        return activityService.failActivity(id, reason);
    }
    
    @PostMapping("/activity/execute/{id}")
    public CombatActivity executeActivity(@PathVariable String id) {
        return activityService.executeActivity(id);
    }
    
    @PostMapping("/activity/delete")
    public String deleteActivity(@RequestParam String id) {
        activityService.deleteActivity(id);
        return "活动删除成功";
    }
    
    @GetMapping("/activity/templates")
    public List<CombatActivity> getActivityTemplates() {
        return activityService.getActivityTemplates();
    }
    
    @GetMapping("/activity/{id}")
    public CombatActivity getActivityById(@PathVariable String id) {
        return activityService.getActivityById(id);
    }
    
    // --- 协同作战管理API ---
    @PostMapping("/coordination/add")
    public CoordinationAction addCoordinationAction(@RequestBody CoordinationAction action) {
        return coordinationService.addCoordinationAction(action);
    }
    
    @GetMapping("/coordination/list")
    public List<CoordinationAction> listCoordinationActions(@RequestParam(required = false) String scenarioId) {
        return coordinationService.getCoordinationActionsForEditor(scenarioId);
    }
    
    @GetMapping("/coordination/side/{side}")
    public List<CoordinationAction> getCoordinationActionsBySide(@PathVariable String side) {
        return coordinationService.getCoordinationActionsBySide(side);
    }
    
    @GetMapping("/coordination/status/{status}")
    public List<CoordinationAction> getCoordinationActionsByStatus(@PathVariable String status) {
        return coordinationService.getCoordinationActionsByStatus(status);
    }
    
    @PostMapping("/coordination/delete")
    public String deleteCoordinationAction(@RequestParam String id) {
        coordinationService.deleteCoordinationAction(id);
        return "协同动作删除成功";
    }
    
    @PostMapping("/coordination/execute/{id}")
    public String executeCoordinationAction(@PathVariable String id) {
        CoordinationAction action = coordinationService.getAllCoordinationActions().stream()
            .filter(a -> a.getId().equals(id))
            .findFirst()
            .orElse(null);
        if (action != null) {
            coordinationService.executeCoordinationAction(action, scenarioService.getCurrentRound());
            return "协同动作执行成功";
        }
        return "协同动作不存在";
    }

    // --- AI 辅助想定建模 ---

    /**
     * 根据用户给出的总体作战目标/背景，生成一份想定建模建议文本
     */
    @PostMapping("/ai/generate-scenario-suggestion")
    public String generateScenarioSuggestion(@RequestBody Map<String, Object> body) {
        Object goal = body.get("goal");
        String userGoal = goal == null ? null : goal.toString();
        return aiScenarioService.generateScenarioSuggestion(userGoal);
    }

    /**
     * 根据当前想定数据（单位/目标/活动/规则等的概要），生成活动与交互规则的建议
     */
    @PostMapping("/ai/generate-activity-interaction-suggestion")
    public String generateActivityInteractionSuggestion(@RequestBody Map<String, Object> body) {
        return aiScenarioService.generateActivityAndInteractionSuggestion(body);
    }

    /**
     * AI 自动化建模与模拟：自动部署编组、下达命令、创建架次/对抗并推进回合。
     * 请求体 {@code dryRun=true} 时仅解析计划并返回概要（不写库、不推演）；
     * {@code replaceAiArtifacts=true} 时在正式写入前删除本想定下名称以 {@code [AI]} 开头的活动与规则。
     */
    @PostMapping("/ai/auto-model-and-simulate")
    public com.military.combat.simulation.AiAutoModelResult autoModelAndSimulate(@RequestBody(required = false) com.military.combat.simulation.AiAutoModelRequest req) {
        if (req == null) {
            req = new com.military.combat.simulation.AiAutoModelRequest();
            req.setRounds(2);
            req.setGoal("构建一套可执行的联合打击作战想定");
        }
        return aiAutoModelingService.autoModelAndSimulate(req);
    }

    /**
     * AI 实时再规划：基于当前回合态势给出下一回合动作并落库。
     */
    @PostMapping("/ai/replan-round")
    public com.military.combat.simulation.AiReplanResult replanRound(@RequestBody(required = false) Map<String, Object> body) {
        String goal = body == null ? null : (body.get("goal") == null ? null : body.get("goal").toString());
        return aiAutoModelingService.realtimeReplan(goal);
    }

    /**
     * AI 混合决策第一阶段：生成 Top-N 候选方案。
     */
    @PostMapping("/ai/strategy/recommend")
    public com.military.combat.simulation.hybrid.HybridStrategyRecommendResponse recommendStrategies(
            @RequestBody(required = false) com.military.combat.simulation.hybrid.HybridStrategyRecommendRequest req) {
        if (req == null) {
            req = new com.military.combat.simulation.hybrid.HybridStrategyRecommendRequest();
        }
        return aiAutoModelingService.recommendTopStrategies(req);
    }

    /**
     * AI 混合决策第二阶段：批量仿真优化并输出可解释对比。
     */
    @PostMapping("/ai/strategy/optimize")
    public com.military.combat.simulation.hybrid.HybridStrategyOptimizeResponse optimizeStrategies(
            @RequestBody(required = false) com.military.combat.simulation.hybrid.HybridStrategyOptimizeRequest req) {
        if (req == null) {
            req = new com.military.combat.simulation.hybrid.HybridStrategyOptimizeRequest();
        }
        return aiAutoModelingService.optimizeTopStrategies(req);
    }

    // --- 交互事件查询API ---
    @GetMapping("/interaction-event/list")
    public List<InteractionEvent> listInteractionEvents(@RequestParam(required = false) Integer round) {
        if (round != null) {
            return interactionEventRepository.findByRound(round);
        }
        return interactionEventRepository.findAll();
    }

    @GetMapping("/interaction-event/round/{round}")
    public List<InteractionEvent> getInteractionEventsByRound(@PathVariable int round) {
        return interactionEventRepository.findByRound(round);
    }

    // --- 战役管理API ---
    @PostMapping("/campaign/load/huaihai")
    public Campaign loadHuaiHaiCampaign() {
        return campaignService.loadHuaiHaiCampaign();
    }

    @PostMapping("/campaign/load/korean")
    public Campaign loadKoreanWar() {
        return campaignService.loadKoreanWar();
    }

    /** 仅关联淮海战役配置到当前会话，不重建单位（用于已保存战役想定的「管理」） */
    @PostMapping("/campaign/attach/huaihai")
    public Campaign attachHuaiHaiCampaign() {
        return campaignService.attachHuaiHaiCampaign();
    }

    /** 仅关联抗美援朝战役配置到当前会话，不重建单位 */
    @PostMapping("/campaign/attach/korean")
    public Campaign attachKoreanWar() {
        return campaignService.attachKoreanWar();
    }

    @GetMapping("/campaign/current")
    public Campaign getCurrentCampaign() {
        return campaignService.getCurrentCampaign();
    }

    @GetMapping("/campaign/phase")
    public Map<String, Object> getCurrentPhase() {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("currentPhase", campaignService.getCurrentPhase());
        Campaign c = campaignService.getCurrentCampaign();
        if (c != null && c.getPhases() != null) {
            int idx = campaignService.getCurrentPhase();
            if (idx >= 0 && idx < c.getPhases().size()) {
                result.put("phaseName", c.getPhases().get(idx));
            }
        }
        return result;
    }

    /** 自定义战役写入当前会话（阶段/目标/事件可自由编辑，服务端补全 ID） */
    @PostMapping("/campaign/custom")
    public Campaign setCustomCampaign(@RequestBody Campaign campaign) {
        return campaignService.applyCampaignToSession(campaign);
    }

    /** 仅清除会话中的当前战役（不影响已保存想定） */
    @PostMapping("/campaign/session/clear")
    public void clearSessionCampaign() {
        campaignService.clearSessionCampaign();
    }

    @PostMapping("/campaign/phase/advance")
    public Map<String, Object> advancePhase() {
        campaignService.advancePhase();
        return getCurrentPhase();
    }

    @PostMapping("/campaign/check-events")
    public void checkEvents() {
        List<CombatUnit> units = unitService.getUnitsForBattleEngine();
        campaignService.checkEvents(units);
        campaignService.checkObjectives(units);
    }

    @GetMapping("/campaign/is-complete")
    public Map<String, Boolean> isCampaignComplete() {
        Map<String, Boolean> result = new java.util.HashMap<>();
        result.put("complete", campaignService.isCampaignComplete());
        return result;
    }
} 