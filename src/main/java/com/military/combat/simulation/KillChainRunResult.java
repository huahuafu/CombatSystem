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
