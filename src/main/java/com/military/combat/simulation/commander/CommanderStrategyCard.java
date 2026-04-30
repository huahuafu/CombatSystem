package com.military.combat.simulation.commander;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CommanderStrategyCard {
    private String schemaVersion = "v1";
    private CommanderStrategyLabel label;
    private String scenarioId;
    private String goal;

    private String strategyId;
    private String strategyName;
    private String hypothesis;
    private String riskSummary;

    private double predictedWinRate;
    private double predictedExpectedLoss;
    private double predictedMissionSuccessRate;
    private double confidence;

    /** AI/规则系统产生的阶段动作包（可审计、可解释） */
    private List<KillChainStageAction> stageActions = new ArrayList<>();

    private List<DeploymentItem> deploymentPlan = new ArrayList<>();
    private List<RouteItem> routePlan = new ArrayList<>();
    private List<SequenceStep> sequencePlan = new ArrayList<>();
    private List<TempoNode> tempoPlan = new ArrayList<>();
    private IntentProsCons intentProsCons = new IntentProsCons();

    /** 简要解释 */
    private List<String> explanation = new ArrayList<>();
}

