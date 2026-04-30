package com.military.combat.service;

import com.military.combat.entity.BattleEvent;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.InteractionProcess;
import com.military.combat.entity.InteractionRule;
import com.military.combat.repository.InteractionProcessRepository;
import com.military.combat.repository.CombatUnitRepository;
import com.military.combat.simulation.random.SimulationRandom;
import com.military.combat.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.SplittableRandom;

/**
 * 交互过程服务
 * 管理交互过程的生命周期
 */
@Service
public class InteractionProcessService {

    @Autowired
    private InteractionProcessRepository processRepository;

    @Autowired
    private CombatUnitRepository unitRepository;

    @Autowired
    private InteractionRuleService ruleService;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private SimulationRandom simulationRandom;

    /**
     * 推演主循环调用：每个作战回合对符合条件的交互过程推进<strong>一步</strong>（与活动、协同并列）。
     */
    public void runProcessesForSimulationRound(int simulationRound, List<BattleEvent> events) {
        List<String> statuses = java.util.List.of("PENDING", "EXECUTING");
        String active = scenarioService.getActiveScenarioId();
        List<InteractionProcess> candidates = (active == null || active.isEmpty())
                ? processRepository.findByStatusIn(statuses)
                : processRepository.findByScenarioIdAndStatusIn(active, statuses);

        for (InteractionProcess p : candidates) {
            try {
                if ("COMPLETED".equals(p.getStatus()) || "CANCELLED".equals(p.getStatus())) {
                    continue;
                }
                if ("PENDING".equals(p.getStatus())) {
                    if (p.getRound() > simulationRound) {
                        continue;
                    }
                    p.setStatus("EXECUTING");
                    processRepository.save(p);
                }
                if ("EXECUTING".equals(p.getStatus())) {
                    advanceOneProcessStep(p, events);
                }
            } catch (Exception e) {
                System.err.println("交互过程推进失败 id=" + p.getId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private boolean processMatchesActiveScenario(InteractionProcess p) {
        String active = scenarioService.getActiveScenarioId();
        if (active == null || active.isEmpty()) {
            return true;
        }
        String sid = p.getScenarioId();
        return active.equals(sid);
    }

    private void advanceOneProcessStep(InteractionProcess p, List<BattleEvent> events) {
        if (p.getSteps() == null || p.getSteps().isEmpty()) {
            p.setStatus("COMPLETED");
            p.setEndTime(sdf.format(new Date()));
            processRepository.save(p);
            return;
        }
        int idx = p.getCurrentStep();
        if (idx >= p.getSteps().size()) {
            p.setStatus("COMPLETED");
            p.setEndTime(sdf.format(new Date()));
            processRepository.save(p);
            return;
        }
        InteractionProcess.ProcessStep step = p.getSteps().get(idx);
        step.setStatus("EXECUTING");
        executeStep(step, p);
        step.setStatus("COMPLETED");
        step.setProgress(100);
        step.setResult("SUCCESS");
        p.setCurrentStep(idx + 1);
        if (p.getCurrentStep() >= p.getTotalSteps()) {
            p.setStatus("COMPLETED");
            p.setResult("SUCCESS");
            p.setEndTime(sdf.format(new Date()));
        } else {
            p.setStatus("EXECUTING");
        }
        processRepository.save(p);

        BattleEvent be = new BattleEvent();
        be.setSource("交互过程");
        be.setTarget(p.getProcessName());
        be.setAction("PROCESS_STEP");
        be.setSide("SYSTEM");
        be.setMessage("交互过程「" + (p.getProcessName() != null ? p.getProcessName() : p.getId()) + "」完成步骤 "
                + (idx + 1) + "/" + p.getTotalSteps() + "：" + (step.getName() != null ? step.getName() : step.getAction()));
        events.add(be);
    }

    /**
     * 创建交互过程
     */
    public InteractionProcess createProcess(InteractionProcess process) {
        if (process.getId() == null) {
            process.setId(java.util.UUID.randomUUID().toString());
        }
        if (process.getStatus() == null) {
            process.setStatus("PENDING");
        }
        if (process.getCurrentStep() == 0) {
            process.setCurrentStep(0);
        }
        if (process.getSteps() != null) {
            process.setTotalSteps(process.getSteps().size());
        }
        if ((process.getScenarioId() == null || process.getScenarioId().isEmpty())
                && scenarioService.getActiveScenarioId() != null
                && !scenarioService.getActiveScenarioId().isEmpty()) {
            process.setScenarioId(scenarioService.getActiveScenarioId());
        }
        process.setStartTime(sdf.format(new Date()));
        return processRepository.save(process);
    }

    /**
     * 根据规则创建交互过程
     */
    public InteractionProcess createProcessFromRule(InteractionRule rule, List<String> sourceUnitIds, List<String> targetUnitIds, int round) {
        InteractionProcess process = new InteractionProcess();
        process.setId(java.util.UUID.randomUUID().toString());
        process.setRuleId(rule.getId());
        process.setRuleName(rule.getName());
        process.setProcessName(rule.getName() + "-执行过程");
        process.setDescription(rule.getDescription());
        process.setRound(round);
        process.setSourceSide(rule.getSourceSide());
        process.setTargetSide(rule.getTargetSide());
        process.setSourceUnits(sourceUnitIds);
        process.setTargetUnits(targetUnitIds);
        process.setStatus("PENDING");
        process.setCurrentStep(0);
        process.setStartTime(sdf.format(new Date()));
        process.setDuration(1); // 默认持续1回合
        String rsid = rule.getScenarioId();
        if (rsid != null && !rsid.isEmpty()) {
            process.setScenarioId(rsid);
        } else if (scenarioService.getActiveScenarioId() != null) {
            process.setScenarioId(scenarioService.getActiveScenarioId());
        }

        // 创建默认步骤
        List<InteractionProcess.ProcessStep> steps = new ArrayList<>();
        InteractionProcess.ProcessStep step = new InteractionProcess.ProcessStep();
        step.setStepNumber(1);
        step.setName("执行规则");
        step.setDescription("执行" + rule.getName() + "规则");
        step.setAction("EXECUTE");
        step.setTarget(rule.getTargetSide());
        step.setRoundDuration(1);
        step.setStatus("PENDING");
        step.setProgress(0);
        steps.add(step);
        process.setSteps(steps);
        process.setTotalSteps(steps.size());

        return processRepository.save(process);
    }

    /**
     * 执行交互过程
     */
    public InteractionProcess executeProcess(InteractionProcess process) {
        if (!"PENDING".equals(process.getStatus()) && !"EXECUTING".equals(process.getStatus())) {
            throw new IllegalArgumentException("过程状态不允许执行: " + process.getStatus());
        }

        process.setStatus("EXECUTING");
        process.setExecuteTime(sdf.format(new Date()));

        // 执行每个步骤
        if (process.getSteps() != null) {
            for (int i = process.getCurrentStep(); i < process.getSteps().size(); i++) {
                InteractionProcess.ProcessStep step = process.getSteps().get(i);
                if (!"COMPLETED".equals(step.getStatus())) {
                    step.setStatus("EXECUTING");
                    step.setProgress(50);

                    // 执行步骤逻辑
                    executeStep(step, process);

                    step.setStatus("COMPLETED");
                    step.setProgress(100);
                    step.setResult("SUCCESS");
                    process.setCurrentStep(i + 1);
                }
            }
        }

        // 检查是否所有步骤都已完成
        if (process.getCurrentStep() >= process.getTotalSteps()) {
            process.setStatus("COMPLETED");
            process.setResult("SUCCESS");
            process.setEndTime(sdf.format(new Date()));
        }

        return processRepository.save(process);
    }

    /**
     * 执行步骤
     */
    private void executeStep(InteractionProcess.ProcessStep step, InteractionProcess process) {
        if (step.getAction() == null) {
            return;
        }
        switch (step.getAction()) {
            case "MOVE":
                runMoveStep(process);
                break;
            case "ATTACK":
                runAttackStep(process);
                break;
            case "DEFEND":
                runDefendStep(process);
                break;
            case "SUPPORT":
                runSupportStep(process);
                break;
            case "EXECUTE":
                executeRule(process);
                break;
            case "WAIT":
            default:
                break;
        }
    }

    private void runMoveStep(InteractionProcess process) {
        CombatUnit focus = resolveFocusTarget(process);
        if (focus == null) {
            return;
        }
        List<String> sources = process.getSourceUnits();
        if (sources == null) {
            return;
        }
        for (String sid : sources) {
            CombatUnit u = unitRepository.findById(sid).orElse(null);
            if (u == null || u.getCombatPower() <= 0) {
                continue;
            }
            double stepMeters = Math.max(u.getSpeed() * 1000.0, 8000.0);
            if (u.getLatitude() != null && u.getLongitude() != null
                    && focus.getLatitude() != null && focus.getLongitude() != null) {
                double[] np = GeoUtils.moveTowards(
                        u.getLatitude(), u.getLongitude(),
                        focus.getLatitude(), focus.getLongitude(),
                        stepMeters);
                u.setLatitude(np[0]);
                u.setLongitude(np[1]);
            } else {
                double dx = focus.getX() - u.getX();
                double dy = focus.getY() - u.getY();
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len < 1e-6) {
                    continue;
                }
                double step = 0.02;
                u.setX(u.getX() + dx / len * step);
                u.setY(u.getY() + dy / len * step);
            }
            combatUnitService.updateUnit(u);
        }
    }

    private CombatUnit resolveFocusTarget(InteractionProcess process) {
        if (process.getTargetUnits() != null) {
            for (String tid : process.getTargetUnits()) {
                CombatUnit t = unitRepository.findById(tid).orElse(null);
                if (t != null && t.getCombatPower() > 0) {
                    return t;
                }
            }
        }
        return null;
    }

    private void runAttackStep(InteractionProcess process) {
        CombatUnit attacker = firstAlive(process.getSourceUnits());
        CombatUnit target = firstEnemyTarget(process.getSourceUnits(), process.getTargetUnits());
        if (attacker == null || target == null) {
            return;
        }
        if (distance(attacker, target) > attacker.getAttackRange()) {
            runMoveStep(process);
            attacker = unitRepository.findById(attacker.getId()).orElse(attacker);
            if (distance(attacker, target) > attacker.getAttackRange()) {
                return;
            }
        }
        SplittableRandom rng = simulationRandom.rng("INTERACTION_ATTACK",
                scenarioService.getCurrentRound(),
                process == null ? null : process.getId());
        int dmg = 12 + rng.nextInt(14);
        target.setCombatPower(target.getCombatPower() - dmg);
        if (target.getCombatPower() <= 0) {
            target.setCombatPower(0);
            target.setStatus("DESTROYED");
        }
        combatUnitService.updateUnit(target);
    }

    private void runDefendStep(InteractionProcess process) {
        for (String sid : nullSafe(process.getSourceUnits())) {
            CombatUnit u = unitRepository.findById(sid).orElse(null);
            if (u == null || u.getCombatPower() <= 0) {
                continue;
            }
            int max = u.getMaxPower() > 0 ? u.getMaxPower() : u.getCombatPower();
            int heal = Math.max(1, (int) (max * 0.03));
            u.setCombatPower(Math.min(max, u.getCombatPower() + heal));
            combatUnitService.updateUnit(u);
        }
    }

    private void runSupportStep(InteractionProcess process) {
        CombatUnit target = resolveFocusTarget(process);
        if (target == null || !isEnemySide(process, target)) {
            return;
        }
        SplittableRandom rng = simulationRandom.rng("INTERACTION_SUPPORT_DMG",
                scenarioService.getCurrentRound(),
                process == null ? null : process.getId());
        int dmg = 8 + rng.nextInt(10);
        target.setCombatPower(Math.max(0, target.getCombatPower() - dmg));
        combatUnitService.updateUnit(target);
    }

    private boolean isEnemySide(InteractionProcess process, CombatUnit target) {
        List<String> src = process.getSourceUnits();
        if (src == null || src.isEmpty()) {
            return true;
        }
        CombatUnit s = unitRepository.findById(src.get(0)).orElse(null);
        return s != null && !s.getSide().equals(target.getSide());
    }

    private CombatUnit firstAlive(List<String> ids) {
        if (ids == null) {
            return null;
        }
        for (String id : ids) {
            CombatUnit u = unitRepository.findById(id).orElse(null);
            if (u != null && u.getCombatPower() > 0) {
                return u;
            }
        }
        return null;
    }

    private CombatUnit firstEnemyTarget(List<String> sourceIds, List<String> targetIds) {
        if (targetIds == null) {
            return null;
        }
        String mySide = null;
        if (sourceIds != null && !sourceIds.isEmpty()) {
            CombatUnit s = unitRepository.findById(sourceIds.get(0)).orElse(null);
            if (s != null) {
                mySide = s.getSide();
            }
        }
        for (String tid : targetIds) {
            CombatUnit t = unitRepository.findById(tid).orElse(null);
            if (t != null && t.getCombatPower() > 0 && (mySide == null || !mySide.equals(t.getSide()))) {
                return t;
            }
        }
        return null;
    }

    private List<String> nullSafe(List<String> ids) {
        return ids == null ? new ArrayList<>() : ids;
    }

    private double distance(CombatUnit a, CombatUnit b) {
        if (a.getLatitude() != null && a.getLongitude() != null
                && b.getLatitude() != null && b.getLongitude() != null) {
            return GeoUtils.calculateDistance(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        }
        return Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2));
    }

    /**
     * 执行规则（与交互规则实体联动，作用于源单位）
     */
    private void executeRule(InteractionProcess process) {
        InteractionRule rule = ruleService.getInteractionRuleById(process.getRuleId());
        if (rule == null || process.getSourceUnits() == null) {
            return;
        }
        for (String unitId : process.getSourceUnits()) {
            CombatUnit unit = unitRepository.findById(unitId).orElse(null);
            if (unit == null || unit.getCombatPower() <= 0) {
                continue;
            }
            String et = rule.getEffectType();
            double ev = rule.getEffectValue();
            if ("SPEED_BOOST".equals(et)) {
                double mult = ev >= 1.0 ? ev : (1.0 + Math.abs(ev));
                unit.setSpeed(unit.getSpeed() * mult);
            } else if ("DEFENSE_BOOST".equals(et)) {
                int max = unit.getMaxPower() > 0 ? unit.getMaxPower() : unit.getCombatPower();
                int heal = Math.max(1, (int) (max * 0.04));
                unit.setCombatPower(Math.min(max, unit.getCombatPower() + heal));
            } else if ("DAMAGE_BOOST".equals(et) && process.getTargetUnits() != null) {
                for (String tid : process.getTargetUnits()) {
                    CombatUnit t = unitRepository.findById(tid).orElse(null);
                    if (t == null || t.getCombatPower() <= 0 || unit.getSide().equals(t.getSide())) {
                        continue;
                    }
                    if (distance(unit, t) > unit.getAttackRange()) {
                        continue;
                    }
                    SplittableRandom rng = simulationRandom.rng("INTERACTION_RULE_DAMAGE",
                            scenarioService.getCurrentRound(),
                            (process == null ? "" : process.getId()) + ":" + (unit.getId() == null ? "" : unit.getId())
                                    + "->" + (t.getId() == null ? "" : t.getId()));
                    int base = 15 + rng.nextInt(10);
                    int dmg = (int) (base * (ev >= 1.0 ? ev : 1.2));
                    t.setCombatPower(t.getCombatPower() - dmg);
                    if (t.getCombatPower() <= 0) {
                        t.setCombatPower(0);
                        t.setStatus("DESTROYED");
                    }
                    combatUnitService.updateUnit(t);
                    break;
                }
            }
            combatUnitService.updateUnit(unit);
        }
    }

    /**
     * 获取所有交互过程
     */
    public List<InteractionProcess> getAllProcesses() {
        return processRepository.findAll();
    }

    public InteractionProcess getProcessById(String id) {
        return id == null ? null : processRepository.findById(id).orElse(null);
    }

    public List<InteractionProcess> getProcessesForEditor(String scenarioId) {
        List<InteractionProcess> all = processRepository.findAll();
        if (scenarioId == null || scenarioId.isEmpty()) {
            return all;
        }
        List<InteractionProcess> out = new ArrayList<>();
        for (InteractionProcess p : all) {
            String sid = p.getScenarioId();
            if (scenarioId.equals(sid)) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * 根据状态获取交互过程
     */
    public List<InteractionProcess> getProcessesByStatus(String status) {
        return processRepository.findByStatus(status);
    }

    /**
     * 根据回合获取交互过程
     */
    public List<InteractionProcess> getProcessesByRound(int round) {
        return processRepository.findByRound(round);
    }

    /**
     * 根据规则ID获取交互过程
     */
    public List<InteractionProcess> getProcessesByRuleId(String ruleId) {
        return processRepository.findByRuleId(ruleId);
    }

    /**
     * 取消交互过程
     */
    public InteractionProcess cancelProcess(String processId) {
        InteractionProcess process = processRepository.findById(processId).orElse(null);
        if (process != null) {
            process.setStatus("CANCELLED");
            process.setEndTime(sdf.format(new Date()));
            process.setResult("CANCELLED");
            return processRepository.save(process);
        }
        return null;
    }

    /**
     * 删除交互过程
     */
    public void deleteProcess(String processId) {
        processRepository.deleteById(processId);
    }

    /**
     * 获取交互过程模板
     */
    public List<InteractionProcess> getProcessTemplates() {
        List<InteractionProcess> templates = new ArrayList<>();

        // 协同攻击过程模板
        InteractionProcess coordAttack = new InteractionProcess();
        coordAttack.setProcessName("协同攻击过程");
        coordAttack.setDescription("多个单位协同攻击的完整过程");
        List<InteractionProcess.ProcessStep> coordSteps = new ArrayList<>();
        coordSteps.add(new InteractionProcess.ProcessStep(1, "集结", "单位向目标区域集结", "MOVE", "目标区域", 2, "PENDING", null, 0));
        coordSteps.add(new InteractionProcess.ProcessStep(2, "协同攻击", "多个单位同时攻击目标", "ATTACK", "目标单位", 3, "PENDING", null, 0));
        coordSteps.add(new InteractionProcess.ProcessStep(3, "评估效果", "评估攻击效果并调整策略", "WAIT", "评估", 1, "PENDING", null, 0));
        coordAttack.setSteps(coordSteps);
        coordAttack.setTotalSteps(coordSteps.size());
        templates.add(coordAttack);

        // 火力支援过程模板
        InteractionProcess fireSupport = new InteractionProcess();
        fireSupport.setProcessName("火力支援过程");
        fireSupport.setDescription("远程单位提供火力支援的过程");
        List<InteractionProcess.ProcessStep> fireSteps = new ArrayList<>();
        fireSteps.add(new InteractionProcess.ProcessStep(1, "部署", "炮兵单位进入射击位置", "MOVE", "射击位置", 1, "PENDING", null, 0));
        fireSteps.add(new InteractionProcess.ProcessStep(2, "火力准备", "进行火力准备和瞄准", "WAIT", "准备", 1, "PENDING", null, 0));
        fireSteps.add(new InteractionProcess.ProcessStep(3, "火力打击", "对目标进行火力打击", "SUPPORT", "目标区域", 2, "PENDING", null, 0));
        fireSteps.add(new InteractionProcess.ProcessStep(4, "火力转移", "根据需要转移火力", "MOVE", "新目标", 1, "PENDING", null, 0));
        fireSupport.setSteps(fireSteps);
        fireSupport.setTotalSteps(fireSteps.size());
        templates.add(fireSupport);

        return templates;
    }
}
