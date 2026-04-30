package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Fix（定位）环节态势快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FixSnapshot {
    private String scenarioId;
    private String scenarioName;
    private int round;
    private long generatedAt;

    // 定位精度与融合能力
    private int candidateCount;
    private int fusedTargetCount;
    private int fixedTargetCount;            // 达到可交给 Track/Target 的目标数量
    private double multiSourceFusionRate;    // 多源融合占比
    private double geoConsistencyRate;       // 坐标一致性
    private double avgPositionErrorMeters;   // 平均定位误差估计
    private double nearLayerCoverage;        // 近程覆盖
    private double midLayerCoverage;         // 中程覆盖
    private double farLayerCoverage;         // 远程覆盖

    private String readinessLevel; // HIGH/MEDIUM/LOW
    private List<String> strengths = new ArrayList<>();
    private List<String> weaknesses = new ArrayList<>();
}
