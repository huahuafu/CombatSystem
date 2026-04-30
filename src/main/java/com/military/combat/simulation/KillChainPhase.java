package com.military.combat.simulation;

/**
 * 六阶段杀伤链作战阶段。
 */
public enum KillChainPhase {
    FIND("发现"),
    FIX("定位"),
    TRACK("跟踪"),
    TARGET("瞄准"),
    ENGAGE("交战"),
    ASSESS("评估");

    private final String displayName;

    KillChainPhase(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
