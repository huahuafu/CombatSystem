package com.military.combat.simulation.hybrid;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HybridStrategyRecommendResponse {
    private String scenarioId;
    private String goal;
    private int topN;
    private String aiRaw;
    private List<HybridStrategyCandidate> strategies = new ArrayList<>();
}
