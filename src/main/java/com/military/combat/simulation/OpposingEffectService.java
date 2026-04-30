package com.military.combat.simulation;

import com.military.combat.entity.OpposingAction;
import com.military.combat.service.OpposingActionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对抗效果聚合：将敌方动作转为环节抑制系数。
 */
@Service
public class OpposingEffectService {

    @Autowired
    private OpposingActionService opposingActionService;

    public OpposingEffectSummary currentSummary() {
        List<OpposingAction> all = opposingActionService.listForActiveScenario();
        double counterRecon = 0;
        double ewSuppression = 0;
        double decoy = 0;
        double samIntercept = 0;
        double saturationStrike = 0;
        double routeBreach = 0;
        for (OpposingAction a : all) {
            if (a == null || !"ACTIVE".equals(a.getStatus())) continue;
            double x = Math.max(0, Math.min(1, a.getIntensity()));
            if ("COUNTER_RECON".equalsIgnoreCase(a.getActionType())) counterRecon += x;
            if ("EW_SUPPRESSION".equalsIgnoreCase(a.getActionType())) ewSuppression += x;
            if ("DECOY".equalsIgnoreCase(a.getActionType())) decoy += x;
            if ("SAM_INTERCEPT".equalsIgnoreCase(a.getActionType())) samIntercept += x;
            if ("SATURATION_STRIKE".equalsIgnoreCase(a.getActionType())) saturationStrike += x;
            if ("ROUTE_BREACH".equalsIgnoreCase(a.getActionType())) routeBreach += x;
        }
        return new OpposingEffectSummary(
                clamp(counterRecon + routeBreach * 0.4, 0.6),
                clamp(ewSuppression + saturationStrike * 0.25, 0.7),
                clamp(decoy + routeBreach * 0.2, 0.6),
                clamp(samIntercept + saturationStrike * 0.35, 0.75)
        );
    }

    public static class OpposingEffectSummary {
        private final double counterRecon;
        private final double ewSuppression;
        private final double decoy;
        private final double samIntercept;

        public OpposingEffectSummary(double counterRecon, double ewSuppression, double decoy, double samIntercept) {
            this.counterRecon = counterRecon;
            this.ewSuppression = ewSuppression;
            this.decoy = decoy;
            this.samIntercept = samIntercept;
        }

        public double counterRecon() {
            return counterRecon;
        }

        public double ewSuppression() {
            return ewSuppression;
        }

        public double decoy() {
            return decoy;
        }

        public double samIntercept() {
            return samIntercept;
        }
    }

    private double clamp(double v, double max) {
        return Math.max(0, Math.min(max, v));
    }
}
