package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前回合作战阶段快照（六阶段驱动）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationalPhaseState {
    private String scenarioId;
    private int round;
    private String doctrine;
    private KillChainPhase currentPhase;
    private String currentPhaseName;
    private String reason;
}
