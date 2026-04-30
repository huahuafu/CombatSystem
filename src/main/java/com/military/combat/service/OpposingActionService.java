package com.military.combat.service;

import com.military.combat.entity.OpposingAction;
import com.military.combat.repository.OpposingActionRepository;
import com.military.combat.util.NavalDomainValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 对抗动作管理服务。
 */
@Service
public class OpposingActionService {

    @Autowired
    private OpposingActionRepository opposingActionRepository;

    @Autowired
    private ScenarioService scenarioService;
    @Autowired
    private CombatUnitService combatUnitService;

    public List<OpposingAction> listForActiveScenario() {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) return new ArrayList<>();
        return opposingActionRepository.findByScenarioId(sid);
    }

    public OpposingAction create(OpposingAction action) {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) throw new IllegalStateException("未激活想定，无法创建对抗动作");
        if (action.getActionType() == null || action.getActionType().isEmpty()) {
            throw new IllegalArgumentException("actionType 不能为空");
        }
        NavalDomainValidator.normalizeAndValidateOpposingAction(action);
        String actionType = action.getActionType().toUpperCase();
        action.setActionType(actionType);
        if (action.getId() == null || action.getId().isEmpty()) action.setId(UUID.randomUUID().toString());
        action.setScenarioId(sid);
        if (action.getSide() == null) action.setSide("BLUE");
        if (action.getStatus() == null) action.setStatus("PLANNED");
        if (action.getStartRound() <= 0) action.setStartRound(scenarioService.getCurrentRound());
        if (action.getEndRound() <= action.getStartRound()) action.setEndRound(action.getStartRound() + 3);
        if (action.getIntensity() <= 0) action.setIntensity("SATURATION_STRIKE".equals(actionType) ? 0.65 : 0.45);
        if (action.getPriority() <= 0) action.setPriority(defaultPriority(actionType));
        if (action.getExecutionWeight() <= 0) action.setExecutionWeight(defaultWeight(actionType));
        if (action.getStrategyTemplate() == null || action.getStrategyTemplate().trim().isEmpty()) {
            action.setStrategyTemplate(defaultTemplate(actionType));
        }
        action.setCreatedAt(System.currentTimeMillis());
        return opposingActionRepository.save(action);
    }

    public List<OpposingAction> runForCurrentRound() {
        int round = scenarioService.getCurrentRound();
        List<OpposingAction> all = listForActiveScenario();
        List<OpposingAction> changed = new ArrayList<>();
        for (OpposingAction a : all) {
            if ("PLANNED".equals(a.getStatus()) && round >= a.getStartRound()) {
                a.setStatus("ACTIVE");
                changed.add(opposingActionRepository.save(a));
                continue;
            }
            if ("ACTIVE".equals(a.getStatus()) && round > a.getEndRound()) {
                a.setStatus("EXPIRED");
                changed.add(opposingActionRepository.save(a));
            }
        }
        applyActiveActions(round);
        return changed;
    }

    public List<OpposingStrategyTemplate> strategyTemplates() {
        List<OpposingStrategyTemplate> out = new ArrayList<>();
        out.add(new OpposingStrategyTemplate("NAVAL_DECOY_TRAP", "DECOY", 8, 0.9, "诱饵欺骗与虚假航迹，压制敌方识别可信度。"));
        out.add(new OpposingStrategyTemplate("NAVAL_SATURATION_STRIKE", "SATURATION_STRIKE", 9, 1.0, "饱和火力压制，优先打击护航薄弱节点。"));
        out.add(new OpposingStrategyTemplate("NAVAL_EW_DENIAL", "EW_SUPPRESSION", 7, 0.95, "电子压制破坏探测链路，降低交战效率。"));
        out.add(new OpposingStrategyTemplate("NAVAL_ROUTE_BREACH", "ROUTE_BREACH", 6, 0.85, "航线突防扰乱护航秩序与补给节奏。"));
        return out;
    }

    private void applyActiveActions(int round) {
        List<OpposingAction> active = listForActiveScenario().stream()
                .filter(a -> a != null && "ACTIVE".equals(a.getStatus()))
                .sorted(Comparator.comparingInt(OpposingAction::getPriority).reversed())
                .collect(Collectors.toList());
        if (active.isEmpty()) {
            return;
        }
        List<com.military.combat.entity.CombatUnit> units = combatUnitService.getUnitsForBattleEngine();
        for (OpposingAction a : active) {
            if (a.getLastAppliedRound() == round) {
                continue;
            }
            double x = Math.max(0, Math.min(1.2, a.getIntensity() * Math.max(0.1, a.getExecutionWeight())));
            applySingleAction(a, units, x);
            a.setLastAppliedRound(round);
            opposingActionRepository.save(a);
        }
    }

    private void applySingleAction(OpposingAction action, List<com.military.combat.entity.CombatUnit> units, double x) {
        String side = action.getSide() == null ? "BLUE" : action.getSide();
        String type = action.getActionType() == null ? "" : action.getActionType().toUpperCase();
        List<com.military.combat.entity.CombatUnit> targets = units.stream()
                .filter(u -> u != null && u.getCombatPower() > 0 && !side.equalsIgnoreCase(u.getSide()))
                .collect(Collectors.toList());
        for (com.military.combat.entity.CombatUnit t : targets) {
            switch (type) {
                case "DECOY":
                    t.setDetectionChainStatus("JAMMED");
                    t.setReadinessLevel(Math.max(0.3, t.getReadinessLevel() - 0.02 * x));
                    break;
                case "EW_SUPPRESSION":
                case "COUNTER_RECON":
                    t.setDetectionChainStatus("DEGRADED");
                    t.setEcmStrength(Math.max(0, t.getEcmStrength() - 0.03 * x));
                    break;
                case "SATURATION_STRIKE":
                    t.setAmmoLevel(Math.max(0, t.getAmmoLevel() - (int) Math.round(4 * x)));
                    t.setFuelLevel(Math.max(0, t.getFuelLevel() - 1.5 * x));
                    if ("SEA".equalsIgnoreCase(t.getDomain()) && "SCREEN".equalsIgnoreCase(t.getFormationRole())) {
                        t.setCombatPower(Math.max(0, t.getCombatPower() - (int) Math.round(2 * x)));
                    }
                    break;
                case "ROUTE_BREACH":
                    if ("SEA".equalsIgnoreCase(t.getDomain())) {
                        if (t.getMission() != null && t.getMission().toUpperCase().contains("ESCORT")) {
                            t.setReadinessLevel(Math.max(0.25, t.getReadinessLevel() - 0.04 * x));
                        }
                        t.setFuelLevel(Math.max(0, t.getFuelLevel() - 1.0 * x));
                    }
                    break;
                default:
                    // no-op
                    break;
            }
            combatUnitService.updateUnit(t);
        }
    }

    private static int defaultPriority(String actionType) {
        switch (actionType) {
            case "SATURATION_STRIKE":
                return 9;
            case "DECOY":
                return 8;
            case "EW_SUPPRESSION":
                return 7;
            case "ROUTE_BREACH":
                return 6;
            default:
                return 5;
        }
    }

    private static double defaultWeight(String actionType) {
        switch (actionType) {
            case "SATURATION_STRIKE":
                return 1.0;
            case "DECOY":
                return 0.9;
            case "EW_SUPPRESSION":
                return 0.95;
            case "ROUTE_BREACH":
                return 0.85;
            default:
                return 0.8;
        }
    }

    private static String defaultTemplate(String actionType) {
        switch (actionType) {
            case "SATURATION_STRIKE":
                return "NAVAL_SATURATION_STRIKE";
            case "DECOY":
                return "NAVAL_DECOY_TRAP";
            case "EW_SUPPRESSION":
                return "NAVAL_EW_DENIAL";
            case "ROUTE_BREACH":
                return "NAVAL_ROUTE_BREACH";
            default:
                return "GENERIC_OPPOSING";
        }
    }

    public static class OpposingStrategyTemplate {
        private final String templateId;
        private final String actionType;
        private final int priority;
        private final double executionWeight;
        private final String description;

        public OpposingStrategyTemplate(String templateId, String actionType, int priority, double executionWeight, String description) {
            this.templateId = templateId;
            this.actionType = actionType;
            this.priority = priority;
            this.executionWeight = executionWeight;
            this.description = description;
        }

        public String getTemplateId() {
            return templateId;
        }

        public String getActionType() {
            return actionType;
        }

        public int getPriority() {
            return priority;
        }

        public double getExecutionWeight() {
            return executionWeight;
        }

        public String getDescription() {
            return description;
        }
    }
}
