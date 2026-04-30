package com.military.combat.simulation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 自动建模执行结果。
 */
@Data
public class AiAutoModelResult {
    private String scenarioId;
    private String goal;
    private int simulatedRounds;
    private String aiRawPlan;
    private String commanderSummary;
    private List<String> actions = new ArrayList<>();
    private AdversarialReview adversarialReview;
    private AssessSnapshot assessSnapshot;
    /** 本次是否为草稿预览（dryRun） */
    private boolean dryRun;
    /** 草稿模式下拟创建实体数量的可读摘要 */
    private String dryRunSummary;
}
