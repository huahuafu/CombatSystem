package com.military.combat.simulation;

import lombok.Data;

/**
 * AI 自动建模请求。
 */
@Data
public class AiAutoModelRequest {
    private String goal;
    private int rounds = 2;
    /**
     * 为 true 时仅解析 AI 计划并返回概要，不部署编组、不写活动/规则/命令/架次/对抗、不推进回合。
     */
    private boolean dryRun = false;
    /**
     * 为 true 且非 dryRun 时，在写入前先删除当前想定下名称以 {@code [AI]} 开头的作战活动与交互规则，避免重复堆积。
     */
    private boolean replaceAiArtifacts = false;
}
