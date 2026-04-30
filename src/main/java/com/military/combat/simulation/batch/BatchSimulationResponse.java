package com.military.combat.simulation.batch;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量模拟总结果：按 score 降序排列的各次运行；bestRunIndex 为原 run 编号（非排序后下标）。
 */
@Data
public class BatchSimulationResponse {
    private String scenarioId;
    private int runsRequested;
    private int roundsPerRun;
    /** 实际使用的 base seed（用于复现）。 */
    private long seed;
    private int bestRunIndex = -1;
    private List<BatchRunOutcome> outcomes = new ArrayList<>();
    private String scoreDescription;
    /**
     * 当请求 {@code replaceAiArtifacts} 为 true 时，说明运行前清理的 [AI] 活动/规则条数。
     */
    private String purgeSummary;
}
