package com.military.combat.simulation.commander;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v3 统一阶段指标快照：用稳定的 key/value 输出给前端。
 */
@Data
public class KillChainStageSnapshotV3 {
    private KillChainStageKey stage;
    private String status; // READY/RUNNING/BLOCKED/UNKNOWN
    private Map<String, Object> metrics = new LinkedHashMap<>();
}

