package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 三层建模就绪度：想定（兵力/目标）→ 活动（命令）→ 交互（规则）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelingReadiness {
    private boolean ready;
    private String scenarioId;
    private String scenarioName;

    private int redUnitCount;
    private int blueUnitCount;
    private int objectiveCount;
    private int activityCount;
    private int enabledRuleCount;

    private List<String> blockers = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
