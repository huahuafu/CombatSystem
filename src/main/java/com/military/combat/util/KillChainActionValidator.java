package com.military.combat.util;

import com.military.combat.simulation.commander.CommanderStrategyLabel;
import com.military.combat.simulation.commander.KillChainStageAction;
import com.military.combat.simulation.commander.KillChainStageKey;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 指挥官 v3：校验 AI/模板输出的阶段动作，保障可执行与可解释性。
 * <p>当前为轻量校验：要求六阶段至少各 1 条动作；否则由调用方降级到默认模板。</p>
 */
public final class KillChainActionValidator {

    private KillChainActionValidator() {
    }

    public static ValidationResult validate(List<KillChainStageAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return ValidationResult.invalid("stageActions 为空");
        }
        Set<KillChainStageKey> covered = EnumSet.noneOf(KillChainStageKey.class);
        for (KillChainStageAction a : actions) {
            if (a == null || a.getStage() == null) {
                continue;
            }
            covered.add(a.getStage());
        }
        if (covered.size() < KillChainStageKey.values().length) {
            return ValidationResult.invalid("stageActions 未覆盖全部六阶段，当前覆盖: " + covered);
        }
        return ValidationResult.valid();
    }

    public static List<KillChainStageAction> safeActionsOrFallback(List<KillChainStageAction> actions,
                                                                   List<KillChainStageAction> fallback) {
        ValidationResult r = validate(actions);
        if (r.valid) {
            return actions;
        }
        return fallback == null ? new ArrayList<>() : fallback;
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, "");
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message == null ? "" : message);
        }
    }
}

