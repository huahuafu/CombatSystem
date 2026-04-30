package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Assess（评估）环节快照：战果评估 + 偏差归因 + 闭环建议。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessSnapshot {
    private String scenarioId;
    private String scenarioName;
    private int round;
    private long generatedAt;

    private double missionEffectivenessRate;   // 综合任务效能
    private double objectiveAchievementRate;   // 目标达成率
    private double bdaConfidenceRate;          // 战果判定置信度
    private double modelDeviationRate;         // 计划-执行偏差率
    private double loopClosureRate;            // 闭环完成度

    private String readinessLevel; // HIGH/MEDIUM/LOW
    private List<String> keyFindings = new ArrayList<>();
    private List<String> deviationCauses = new ArrayList<>();
    private List<String> nextActionRecommendations = new ArrayList<>();
}
