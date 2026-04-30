package com.military.combat.util;

import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.GroupCommandOrder;
import com.military.combat.entity.NavalDomainEnums;
import com.military.combat.entity.OpposingAction;
import com.military.combat.entity.ScenarioData;
import com.military.combat.entity.SortieMission;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 海上域相关字段标准化与值域校验。
 */
public final class NavalDomainValidator {

    private static final Set<String> DOMAIN_SET = Set.of("LAND", "SEA", "AIR", "SPACE", "ELECTROMAGNETIC", "EM");
    private static final Set<String> FORMATION_ROLE_SET = Set.of("SCREEN", "CORE", "PICKET", "LOGISTICS");
    private static final Set<String> COMMAND_TYPE_SET = enumSet(NavalDomainEnums.CommandType.values());
    private static final Set<String> TASK_TYPE_SET = enumSet(NavalDomainEnums.TaskType.values());
    private static final Set<String> VESSEL_TYPE_SET = enumSet(NavalDomainEnums.VesselType.values());
    private static final Set<String> SUPPLY_STATUS_SET = enumSet(NavalDomainEnums.SupplyStatus.values());
    private static final Set<String> DETECTION_SET = enumSet(NavalDomainEnums.DetectionChainStatus.values());
    private static final Set<String> OBJECTIVE_TYPE_SET = enumSet(NavalDomainEnums.ObjectiveType.values());
    private static final Set<String> MISSION_TYPE_SET = merge(TASK_TYPE_SET, Set.of("ISR", "EW", "STRIKE", "CAP", "ESCORT", "ANTI_SUBMARINE", "SEA_DENIAL"));
    private static final Set<String> OPPOSING_ACTION_SET = Set.of(
            "COUNTER_RECON", "EW_SUPPRESSION", "DECOY", "SAM_INTERCEPT", "SATURATION_STRIKE", "ROUTE_BREACH"
    );

    private NavalDomainValidator() {
    }

    public static void normalizeAndValidateUnit(CombatUnit unit) {
        if (unit == null) {
            return;
        }
        unit.setDomain(normalize(unit.getDomain()));
        if (unit.getDomain() != null) {
            validate("domain", unit.getDomain(), DOMAIN_SET);
            if ("EM".equals(unit.getDomain())) {
                unit.setDomain("ELECTROMAGNETIC");
            }
        }
        unit.setVesselType(normalize(unit.getVesselType()));
        if (unit.getVesselType() != null) {
            validate("vesselType", unit.getVesselType(), VESSEL_TYPE_SET);
        }
        unit.setNavalTaskType(normalize(unit.getNavalTaskType()));
        if (unit.getNavalTaskType() != null) {
            validate("navalTaskType", unit.getNavalTaskType(), TASK_TYPE_SET);
        }
        unit.setSupplyStatus(normalize(unit.getSupplyStatus()));
        if (unit.getSupplyStatus() != null) {
            validate("supplyStatus", unit.getSupplyStatus(), SUPPLY_STATUS_SET);
        }
        unit.setDetectionChainStatus(normalize(unit.getDetectionChainStatus()));
        if (unit.getDetectionChainStatus() != null) {
            validate("detectionChainStatus", unit.getDetectionChainStatus(), DETECTION_SET);
        }
        unit.setFormationRole(normalize(unit.getFormationRole()));
        if (unit.getFormationRole() != null) {
            validate("formationRole", unit.getFormationRole(), FORMATION_ROLE_SET);
        }
    }

    public static void normalizeAndValidateObjective(CombatObjective objective) {
        if (objective == null) {
            return;
        }
        objective.setObjectiveType(normalize(objective.getObjectiveType()));
        if (objective.getObjectiveType() != null) {
            validate("objectiveType", objective.getObjectiveType(), OBJECTIVE_TYPE_SET);
        }
    }

    public static void normalizeAndValidateScenario(ScenarioData scenarioData) {
        if (scenarioData == null) {
            return;
        }
        scenarioData.setScenarioDomain(normalize(scenarioData.getScenarioDomain()));
        if (scenarioData.getScenarioDomain() != null) {
            validate("scenarioDomain", scenarioData.getScenarioDomain(), DOMAIN_SET);
            if ("EM".equals(scenarioData.getScenarioDomain())) {
                scenarioData.setScenarioDomain("ELECTROMAGNETIC");
            }
        }
    }

    public static void normalizeAndValidateCommandOrder(GroupCommandOrder order) {
        if (order == null) {
            return;
        }
        order.setOrderType(normalize(order.getOrderType()));
        if (order.getOrderType() != null) {
            validate("orderType", order.getOrderType(), merge(COMMAND_TYPE_SET, Set.of("ISR", "EW", "STRIKE", "AIR_DEFENSE", "RESUPPLY")));
        }
    }

    public static void normalizeAndValidateSortie(SortieMission mission) {
        if (mission == null) {
            return;
        }
        mission.setMissionType(normalize(mission.getMissionType()));
        if (mission.getMissionType() != null) {
            validate("missionType", mission.getMissionType(), MISSION_TYPE_SET);
        }
    }

    public static void normalizeAndValidateOpposingAction(OpposingAction action) {
        if (action == null) {
            return;
        }
        action.setActionType(normalize(action.getActionType()));
        if (action.getActionType() != null) {
            validate("actionType", action.getActionType(), OPPOSING_ACTION_SET);
        }
        action.setTargetDomain(normalize(action.getTargetDomain()));
        if (action.getTargetDomain() != null) {
            validate("targetDomain", action.getTargetDomain(), DOMAIN_SET);
            if ("EM".equals(action.getTargetDomain())) {
                action.setTargetDomain("ELECTROMAGNETIC");
            }
        }
    }

    private static void validate(String field, String value, Set<String> allowed) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(field + " 非法值: " + value + "，允许值: " + allowed);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String out = value.trim();
        if (out.isEmpty()) {
            return null;
        }
        return out.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static Set<String> enumSet(Enum<?>[] values) {
        Set<String> out = new HashSet<>();
        for (Enum<?> e : values) {
            out.add(e.name());
        }
        return out;
    }

    private static Set<String> merge(Set<String> base, Set<String> extra) {
        Set<String> out = new HashSet<>(base);
        out.addAll(extra);
        return out;
    }
}
