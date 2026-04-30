package com.military.combat.simulation;

import com.military.combat.entity.SortieMission;
import com.military.combat.service.SortieMissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 架次任务对六环节的加成/抑制效果聚合。
 */
@Service
public class SortieEffectService {

    @Autowired
    private SortieMissionService sortieMissionService;

    public SortieEffectSummary currentSummary() {
        List<SortieMission> all = sortieMissionService.listForActiveScenario();
        int isr = 0;
        int ew = 0;
        int strike = 0;
        for (SortieMission m : all) {
            if (m == null || !"AIRBORNE".equals(m.getStatus())) continue;
            if ("ISR".equalsIgnoreCase(m.getMissionType())) isr++;
            if ("EW".equalsIgnoreCase(m.getMissionType())) ew++;
            if ("STRIKE".equalsIgnoreCase(m.getMissionType())) strike++;
        }
        return new SortieEffectSummary(isr, ew, strike);
    }

    public static class SortieEffectSummary {
        private final int isrAirborne;
        private final int ewAirborne;
        private final int strikeAirborne;

        public SortieEffectSummary(int isrAirborne, int ewAirborne, int strikeAirborne) {
            this.isrAirborne = isrAirborne;
            this.ewAirborne = ewAirborne;
            this.strikeAirborne = strikeAirborne;
        }

        public int getIsrAirborne() {
            return isrAirborne;
        }

        public int getEwAirborne() {
            return ewAirborne;
        }

        public int getStrikeAirborne() {
            return strikeAirborne;
        }

        public double isrBoost() { return Math.min(0.15, isrAirborne * 0.04); }
        public double ewBoost() { return Math.min(0.18, ewAirborne * 0.05); }
        public double strikeBoost() { return Math.min(0.2, strikeAirborne * 0.06); }
        public double ewSuppression() { return Math.min(0.12, ewAirborne * 0.035); }
    }
}
