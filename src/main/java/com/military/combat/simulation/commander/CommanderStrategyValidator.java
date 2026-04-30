package com.military.combat.simulation.commander;

public final class CommanderStrategyValidator {
    private CommanderStrategyValidator() {}

    public static void validate(CommanderStrategyCard card) {
        if (card == null) {
            throw new IllegalArgumentException("STRATEGY_SCHEMA_INVALID: 策略为空");
        }
        if (card.getLabel() == null) {
            throw new IllegalArgumentException("PLAN_TYPE_MISSING: 未指定策略类型");
        }
        if (isEmpty(card.getDeploymentPlan())) {
            throw new IllegalArgumentException("STRATEGY_SCHEMA_INVALID: deploymentPlan 不能为空");
        }
        if (isEmpty(card.getRoutePlan())) {
            throw new IllegalArgumentException("STRATEGY_SCHEMA_INVALID: routePlan 不能为空");
        }
        if (isEmpty(card.getSequencePlan())) {
            throw new IllegalArgumentException("STRATEGY_SCHEMA_INVALID: sequencePlan 不能为空");
        }
        if (isEmpty(card.getTempoPlan())) {
            throw new IllegalArgumentException("STRATEGY_SCHEMA_INVALID: tempoPlan 不能为空");
        }
        IntentProsCons intent = card.getIntentProsCons();
        if (intent == null || blank(intent.getIntent()) || isEmpty(intent.getPros()) || isEmpty(intent.getCons())) {
            throw new IllegalArgumentException("STRATEGY_SCHEMA_INVALID: intentProsCons 不完整");
        }
    }

    private static boolean isEmpty(java.util.Collection<?> list) {
        return list == null || list.isEmpty();
    }

    private static boolean blank(String v) {
        return v == null || v.trim().isEmpty();
    }
}
