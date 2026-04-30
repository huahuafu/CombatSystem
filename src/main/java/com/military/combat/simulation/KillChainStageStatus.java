package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 杀伤链单阶段状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KillChainStageStatus {
    private String stageKey;         // FIND/FIX/TRACK/TARGET/ENGAGE/ASSESS
    private String stageName;        // 发现/定位/跟踪/瞄准/交战/评估
    private double progress;         // 0~1
    private boolean ready;           // 是否达到可执行阈值
    private boolean blockedByPrevious; // 是否被前序环节阻塞
    private String predecessorKey;     // 前置环节
    private String transitionHint;     // 进入下一环节建议
    private List<String> evidences = new ArrayList<>();
    private List<String> gaps = new ArrayList<>();
}
