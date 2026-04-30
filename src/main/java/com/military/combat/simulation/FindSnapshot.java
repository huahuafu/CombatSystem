package com.military.combat.simulation;

import com.military.combat.entity.FindContactReport;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Find 环节态势快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FindSnapshot {
    private String scenarioId;
    private String scenarioName;
    private int round;
    private long generatedAt;

    // 核心任务覆盖
    private boolean domainLandCovered;
    private boolean domainSeaCovered;
    private boolean domainAirCovered;
    private boolean domainSpaceCovered;
    private boolean domainElectromagneticCovered;

    // 关键指标
    private double detectionProbability;      // Pd
    private double falseAlarmRate;            // Pf
    private double identificationAccuracy;    // IdAcc
    private double reportingLatencySeconds;   // 秒
    private double updateIntervalSeconds;     // 秒

    private int detectionsThisRound;
    private int identifiedThisRound;
    private int highThreatContacts;

    private String readinessLevel; // HIGH/MEDIUM/LOW
    private List<String> strengths = new ArrayList<>();
    private List<String> weaknesses = new ArrayList<>();

    private List<FindContactReport> latestContacts = new ArrayList<>();
}
