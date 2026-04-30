package com.military.combat.service;

import com.military.combat.entity.CoordinationAction;
import com.military.combat.entity.InteractionProcess;
import com.military.combat.entity.InteractionRule;
import com.military.combat.repository.CoordinationActionRepository;
import com.military.combat.repository.InteractionProcessRepository;
import com.military.combat.repository.InteractionRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 兼容迁移：将交互过程/协同作战收敛为交互规则。
 */
@Service
public class InteractionLegacyMigrationService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private InteractionRuleRepository interactionRuleRepository;

    @Autowired
    private InteractionProcessRepository interactionProcessRepository;

    @Autowired
    private CoordinationActionRepository coordinationActionRepository;

    public Map<String, Object> migrateActiveScenarioToRules(boolean archiveLegacy) {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) {
            throw new IllegalStateException("未激活想定，无法迁移。");
        }

        List<InteractionProcess> processes = listProcessesForScenario(sid);
        List<CoordinationAction> coordinations = listCoordinationsForScenario(sid);

        int createdFromProcess = 0;
        int createdFromCoordination = 0;

        for (InteractionProcess p : processes) {
            InteractionRule rule = fromProcess(p, sid);
            interactionRuleRepository.save(rule);
            createdFromProcess++;
        }
        for (CoordinationAction c : coordinations) {
            InteractionRule rule = fromCoordination(c, sid);
            interactionRuleRepository.save(rule);
            createdFromCoordination++;
        }

        int archivedProcess = 0;
        int archivedCoordination = 0;
        if (archiveLegacy) {
            for (InteractionProcess p : processes) {
                interactionProcessRepository.deleteById(p.getId());
                archivedProcess++;
            }
            for (CoordinationAction c : coordinations) {
                coordinationActionRepository.deleteById(c.getId());
                archivedCoordination++;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenarioId", sid);
        out.put("scenarioName", scenarioService.getScenarioDataById(sid) != null ? scenarioService.getScenarioDataById(sid).getName() : null);
        out.put("createdFromProcess", createdFromProcess);
        out.put("createdFromCoordination", createdFromCoordination);
        out.put("createdTotal", createdFromProcess + createdFromCoordination);
        out.put("archivedProcess", archivedProcess);
        out.put("archivedCoordination", archivedCoordination);
        out.put("archiveLegacy", archiveLegacy);
        return out;
    }

    private List<InteractionProcess> listProcessesForScenario(String sid) {
        List<InteractionProcess> all = interactionProcessRepository.findAll();
        List<InteractionProcess> out = new ArrayList<>();
        for (InteractionProcess p : all) {
            if (p != null && sid.equals(p.getScenarioId())) {
                out.add(p);
            }
        }
        return out;
    }

    private List<CoordinationAction> listCoordinationsForScenario(String sid) {
        List<CoordinationAction> all = coordinationActionRepository.findAll();
        List<CoordinationAction> out = new ArrayList<>();
        for (CoordinationAction c : all) {
            if (c != null && sid.equals(c.getScenarioId())) {
                out.add(c);
            }
        }
        return out;
    }

    private InteractionRule fromProcess(InteractionProcess p, String sid) {
        InteractionRule r = new InteractionRule();
        r.setId(UUID.randomUUID().toString());
        r.setScenarioId(sid);
        r.setName("迁移-过程-" + safeName(p.getProcessName(), p.getId()));
        r.setType("COORDINATE");
        r.setDescription("由交互过程迁移：" + safeName(p.getProcessName(), p.getId()));
        r.setTriggerType("TIME");
        r.setTriggerCondition("legacy:process:" + p.getId());
        r.setSourceUnitIds(p.getSourceUnits());
        r.setTargetUnitIds(p.getTargetUnits());
        r.setSourceSide(p.getSourceSide());
        r.setTargetSide(p.getTargetSide());
        r.setEffectType(inferEffectTypeFromProcess(p));
        r.setEffectValue(inferEffectValueFromProcess(p));
        r.setMinDistance(0);
        r.setMaxDistance(50000);
        r.setStartRound(Math.max(0, p.getRound()));
        r.setEndRound(p.getDuration() > 0 ? p.getRound() + p.getDuration() - 1 : -1);
        r.setEnabled(true);
        r.setPriority(1);
        return r;
    }

    private InteractionRule fromCoordination(CoordinationAction c, String sid) {
        InteractionRule r = new InteractionRule();
        r.setId(UUID.randomUUID().toString());
        r.setScenarioId(sid);
        r.setName("迁移-协同-" + safeName(c.getName(), c.getId()));
        r.setType("COORDINATE");
        r.setDescription("由协同作战迁移：" + safeName(c.getName(), c.getId()));
        r.setTriggerType("CONDITION");
        r.setTriggerCondition("legacy:coordination:" + c.getId());
        r.setSourceUnitIds(c.getUnitIds());
        r.setSourceSide(c.getSide());
        r.setTargetSide("RED".equals(c.getSide()) ? "BLUE" : "RED");
        r.setEffectType("DAMAGE_BOOST");
        r.setEffectValue(c.getDamageMultiplier() > 0 ? c.getDamageMultiplier() : 1.1);
        r.setMinDistance(0);
        r.setMaxDistance(50000);
        r.setStartRound(0);
        r.setEndRound(c.getDuration() > 0 ? c.getDuration() : -1);
        r.setEnabled(true);
        r.setPriority(2);
        return r;
    }

    private String inferEffectTypeFromProcess(InteractionProcess p) {
        if (p == null || p.getSteps() == null) {
            return "DAMAGE_BOOST";
        }
        for (InteractionProcess.ProcessStep step : p.getSteps()) {
            if (step == null || step.getAction() == null) continue;
            if ("DEFEND".equals(step.getAction())) return "DEFENSE_BOOST";
            if ("MOVE".equals(step.getAction())) return "SPEED_BOOST";
            if ("ATTACK".equals(step.getAction()) || "SUPPORT".equals(step.getAction()) || "EXECUTE".equals(step.getAction())) {
                return "DAMAGE_BOOST";
            }
        }
        return "DAMAGE_BOOST";
    }

    private double inferEffectValueFromProcess(InteractionProcess p) {
        if (p != null && p.getEffectValue() > 0) {
            return p.getEffectValue();
        }
        return 1.15;
    }

    private String safeName(String v, String fallback) {
        return (v != null && !v.trim().isEmpty()) ? v.trim() : fallback;
    }
}
