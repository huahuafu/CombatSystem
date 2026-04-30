package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Target（瞄准）环节态势快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TargetSnapshot {
    private String scenarioId;
    private String scenarioName;
    private int round;
    private long generatedAt;

    private int trackedCandidateCount;     // 来自 Track 的候选
    private int targetAssignedCount;       // 已分配目标数
    private int fireSolutionCount;         // 形成火力解数
    private double assignmentCoverageRate; // 分配覆盖率
    private double weaponMatchRate;        // 武器匹配质量
    private double timeToFireSeconds;      // 预计交战前准备时长
    private double highValuePriorityRate;  // 高价值目标优先命中率

    private String readinessLevel; // HIGH/MEDIUM/LOW
    private List<String> strengths = new ArrayList<>();
    private List<String> weaknesses = new ArrayList<>();
}
