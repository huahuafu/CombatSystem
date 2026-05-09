package com.military.combat.simulation;

import com.military.combat.entity.BattleEvent;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 六阶段串行执行结果。
 */
@Data
public class KillChainRunResult {
    private String scenarioId;
    private int round;
    private List<String> executedPhases = new ArrayList<>();

    /** 单次「逐步推进」时实际执行的片段（与 executedPhases 单元素对应）。整回合一键推进时可留空。 */
    private String executedPhase;

    /** 下一次逐步推进应从哪一片段开始（0..5）。仅逐步 API 使用。 */
    private int nextSequentialStep;

    private int findContacts;
    private int fixTargets;
    private int trackTargets;
    private int targetSolutions;
    private int engageEvents;

    private String assessReadiness;
    private double loopClosureRate;

    private List<BattleEvent> battleEvents = new ArrayList<>();
    private KillChainAssessment assessment;
}
