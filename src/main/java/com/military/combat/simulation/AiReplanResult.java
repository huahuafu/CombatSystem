package com.military.combat.simulation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 实时再规划结果。
 */
@Data
public class AiReplanResult {
    private String scenarioId;
    private int currentRound;
    private String aiRawPlan;
    private String brief;
    private List<String> appliedActions = new ArrayList<>();
    private AssessSnapshot assessSnapshot;
    private AdversarialReview adversarialReview;
}
