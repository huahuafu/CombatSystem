package com.military.combat.simulation;

import com.military.combat.entity.CombatActivity;
import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.InteractionEvent;
import com.military.combat.repository.InteractionEventRepository;
import com.military.combat.service.CombatActivityService;
import com.military.combat.service.CombatUnitService;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TacticalAssessmentService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private CombatActivityService activityService;

    @Autowired
    private InteractionEventRepository interactionEventRepository;

    public TacticalAssessment getForActiveScenario() {
        TacticalAssessment out = new TacticalAssessment();
        String sid = scenarioService.getActiveScenarioId();
        out.setScenarioId(sid);
        out.setCurrentRound(scenarioService.getCurrentRound());

        if (sid == null || sid.isEmpty()) {
            out.setScenarioName("未激活想定");
            return out;
        }

        var sd = scenarioService.getScenarioDataById(sid);
        out.setScenarioName(sd != null ? sd.getName() : null);

        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine();
        computeAttrition(out, units);
        computeObjectiveCompletion(out, sd == null ? null : sd.getObjectives());
        computeActivityCompletion(out, activityService.getActivitiesForEditor(sid));
        computeRuleContribution(out, units);
        return out;
    }

    private void computeAttrition(TacticalAssessment out, List<CombatUnit> units) {
        double redMax = 0, redNow = 0, blueMax = 0, blueNow = 0;
        for (CombatUnit u : units) {
            if (u == null) continue;
            double max = Math.max(u.getMaxPower(), u.getCombatPower());
            if ("RED".equals(u.getSide())) {
                redMax += max;
                redNow += Math.max(0, u.getCombatPower());
            } else if ("BLUE".equals(u.getSide())) {
                blueMax += max;
                blueNow += Math.max(0, u.getCombatPower());
            }
        }
        out.setRedAttritionRate(redMax <= 0 ? 0 : ratio((redMax - redNow) / redMax));
        out.setBlueAttritionRate(blueMax <= 0 ? 0 : ratio((blueMax - blueNow) / blueMax));
    }

    private void computeObjectiveCompletion(TacticalAssessment out, List<CombatObjective> objectives) {
        if (objectives == null || objectives.isEmpty()) {
            out.setObjectiveCompletionRate(0);
            return;
        }
        long done = objectives.stream().filter(CombatObjective::isCompleted).count();
        out.setObjectiveCompletionRate(ratio((double) done / objectives.size()));
    }

    private void computeActivityCompletion(TacticalAssessment out, List<CombatActivity> activities) {
        if (activities == null || activities.isEmpty()) {
            out.setActivityCompletionRate(0);
            return;
        }
        long done = activities.stream().filter(a -> "COMPLETED".equals(a.getStatus())).count();
        out.setActivityCompletionRate(ratio((double) done / activities.size()));
    }

    private void computeRuleContribution(TacticalAssessment out, List<CombatUnit> activeUnits) {
        List<InteractionEvent> all = interactionEventRepository.findAll();
        Set<String> unitIds = new HashSet<>();
        for (CombatUnit u : activeUnits) {
            if (u != null && u.getId() != null) unitIds.add(u.getId());
        }

        Map<String, Long> cntByRule = new HashMap<>();
        Map<String, String> nameByRule = new HashMap<>();
        int related = 0;
        for (InteractionEvent e : all) {
            if (e == null || e.getRuleId() == null) continue;
            boolean relatedToScenario =
                    (e.getSourceUnitId() != null && unitIds.contains(e.getSourceUnitId()))
                            || (e.getTargetUnitId() != null && unitIds.contains(e.getTargetUnitId()));
            if (!relatedToScenario) continue;
            related++;
            cntByRule.put(e.getRuleId(), cntByRule.getOrDefault(e.getRuleId(), 0L) + 1L);
            if (e.getRuleName() != null && !e.getRuleName().isEmpty()) {
                nameByRule.put(e.getRuleId(), e.getRuleName());
            }
        }
        out.setInteractionEventCount(related);

        List<TacticalAssessment.RuleContribution> top = new ArrayList<>();
        for (Map.Entry<String, Long> en : cntByRule.entrySet()) {
            top.add(new TacticalAssessment.RuleContribution(
                    en.getKey(),
                    nameByRule.getOrDefault(en.getKey(), en.getKey()),
                    en.getValue()
            ));
        }
        top.sort(Comparator.comparingLong(TacticalAssessment.RuleContribution::getTriggerCount).reversed());
        if (top.size() > 5) top = top.subList(0, 5);
        out.setTopRuleContributions(top);
    }

    private double ratio(double v) {
        double r = Math.max(0, Math.min(1, v));
        return Math.round(r * 1000.0) / 1000.0;
    }
}
