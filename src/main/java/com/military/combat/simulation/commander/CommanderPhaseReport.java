package com.military.combat.simulation.commander;

import com.military.combat.entity.BattleEvent;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CommanderPhaseReport {
    private int round;
    private KillChainStageKey stage;
    private KillChainStageSnapshotV3 snapshot;
    private List<BattleEvent> events = new ArrayList<>();
}

