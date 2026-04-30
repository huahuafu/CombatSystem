package com.military.combat.simulation.batch;

import lombok.Data;

/**
 * 单次批量运行结果。
 */
@Data
public class BatchRunOutcome {
    private int runIndex;
    private int roundsSimulated;
    /** 本次 run 的 seed（用于精确复现单次）。 */
    private long seed;
    private int redPowerAtStart;
    private int bluePowerAtStart;
    private int redPowerAtEnd;
    private int bluePowerAtEnd;
    private int totalBattleEvents;
    private int objectivesCompleted;
    /**
     * 综合分，越大越优（在 {@link com.military.combat.service.BatchSimulationService} 中按视角计算）。
     */
    private double score;
}
