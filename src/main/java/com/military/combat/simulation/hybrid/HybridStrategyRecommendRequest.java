package com.military.combat.simulation.hybrid;

import lombok.Data;

@Data
public class HybridStrategyRecommendRequest {
    private String goal;
    private int topN = 3;
}
