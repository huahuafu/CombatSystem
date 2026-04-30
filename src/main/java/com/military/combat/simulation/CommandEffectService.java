package com.military.combat.simulation;

import com.military.combat.entity.GroupCommandOrder;
import com.military.combat.service.CommandChainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 指挥命令效果聚合：统计当前执行中的编组命令并提供环节加成。
 */
@Service
public class CommandEffectService {

    @Autowired
    private CommandChainService commandChainService;

    public CommandEffectSummary currentSummary() {
        List<GroupCommandOrder> all = commandChainService.listOrders();
        int isr = 0;
        int ew = 0;
        int strike = 0;
        int airDefense = 0;
        for (GroupCommandOrder o : all) {
            if (o == null) continue;
            if (!"ISSUED".equals(o.getStatus()) && !"EXECUTING".equals(o.getStatus())) continue;
            if ("ISR".equalsIgnoreCase(o.getOrderType())) isr++;
            if ("EW".equalsIgnoreCase(o.getOrderType())) ew++;
            if ("STRIKE".equalsIgnoreCase(o.getOrderType())) strike++;
            if ("AIR_DEFENSE".equalsIgnoreCase(o.getOrderType())) airDefense++;
        }
        return new CommandEffectSummary(isr, ew, strike, airDefense);
    }

    public static class CommandEffectSummary {
        private final int isrExecuting;
        private final int ewExecuting;
        private final int strikeExecuting;
        private final int airDefenseExecuting;

        public CommandEffectSummary(int isrExecuting, int ewExecuting, int strikeExecuting, int airDefenseExecuting) {
            this.isrExecuting = isrExecuting;
            this.ewExecuting = ewExecuting;
            this.strikeExecuting = strikeExecuting;
            this.airDefenseExecuting = airDefenseExecuting;
        }

        public int getIsrExecuting() {
            return isrExecuting;
        }

        public int getEwExecuting() {
            return ewExecuting;
        }

        public int getStrikeExecuting() {
            return strikeExecuting;
        }

        public int getAirDefenseExecuting() {
            return airDefenseExecuting;
        }

        public double isrBoost() { return Math.min(0.20, isrExecuting * 0.05); }
        public double ewBoost() { return Math.min(0.22, ewExecuting * 0.055); }
        public double strikeBoost() { return Math.min(0.24, strikeExecuting * 0.06); }
        public double airDefenseBoost() { return Math.min(0.18, airDefenseExecuting * 0.05); }
        public double suppression() { return Math.min(0.14, ewExecuting * 0.04); }
    }
}
