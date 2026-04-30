package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 红蓝对抗复盘报告。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdversarialReview {
    private String scenarioId;
    private String scenarioName;
    private int round;
    private long generatedAt;

    private String dominantSide; // RED/BLUE/BALANCED
    private double redCompositeScore;
    private double blueCompositeScore;

    private List<String> redAdvantages = new ArrayList<>();
    private List<String> blueAdvantages = new ArrayList<>();
    private List<String> keyTurningPoints = new ArrayList<>();
    private List<String> recommendationsForRed = new ArrayList<>();
    private List<String> recommendationsForBlue = new ArrayList<>();
}
