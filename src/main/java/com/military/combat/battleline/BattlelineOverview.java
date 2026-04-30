package com.military.combat.battleline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条战役主线的只读聚合：自定义战役 → 想定兵力部署 → 作战活动命令 → 交互规则/过程。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BattlelineOverview {

    /** 主线一句话说明（固定模板 + 当前实例） */
    private String mainLine;

    private String campaignId;
    private String campaignName;

    private String scenarioId;
    private String scenarioName;

    /** 第一层：战役（会话或想定内嵌） */
    private String layerCampaign;
    /** 第二层：想定 — 兵力部署与目标 */
    private String layerDeployment;
    /** 第三层：活动 — 对部队的命令 */
    private String layerCommands;
    /** 第四层：交互 — 规则与过程 */
    private String layerInteraction;

    private int redUnitCount;
    private int blueUnitCount;
    private int objectiveCount;

    private int activityCount;
    private int interactionRuleCount;
    private int interactionProcessCount;

    private List<String> hints = new ArrayList<>();
}
