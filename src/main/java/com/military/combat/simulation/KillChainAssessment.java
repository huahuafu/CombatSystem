package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 六环节杀伤链整体评估快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KillChainAssessment {
    private String scenarioId;
    private String scenarioName;
    private int currentRound;
    private double overallMaturity;       // 0~1
    private String doctrineTip;           // 面向教学展示的建议
    private List<KillChainStageStatus> stages = new ArrayList<>();
}
