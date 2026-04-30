package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 战术评估快照：用于前端展示当前推演质量与态势。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacticalAssessment {
    private String scenarioId;
    private String scenarioName;
    private int currentRound;

    private double redAttritionRate;      // 红方战损率（基于 maxPower）
    private double blueAttritionRate;     // 蓝方战损率（基于 maxPower）
    private double objectiveCompletionRate; // 想定目标完成率
    private double activityCompletionRate;  // 活动完成率

    private int interactionEventCount;      // 当前想定相关交互事件总数
    private List<RuleContribution> topRuleContributions = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleContribution {
        private String ruleId;
        private String ruleName;
        private long triggerCount;
    }
}
