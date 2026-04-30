package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Track（跟踪）环节态势快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackSnapshot {
    private String scenarioId;
    private String scenarioName;
    private int round;
    private long generatedAt;

    private int handoffTargetCount;         // 来自 Fix 的可移交目标
    private int trackedTargetCount;         // 当前稳定跟踪目标
    private int highSpeedTrackedCount;      // 高速目标跟踪数
    private double trackContinuityRate;     // 轨迹连续性
    private double trackLossRate;           // 丢轨率
    private double trackUpdateSeconds;      // 轨迹更新时间（秒）
    private double predictionStabilityRate; // 轨迹预测稳定性

    private String readinessLevel; // HIGH/MEDIUM/LOW
    private List<String> strengths = new ArrayList<>();
    private List<String> weaknesses = new ArrayList<>();
}
