package com.military.combat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MigrationModeService {
    private final AtomicReference<String> modeRef = new AtomicReference<>("legacy");

    public MigrationModeService(@Value("${combat.cutover.default-route:legacy}") String defaultMode) {
        setMode(defaultMode);
    }

    public String getMode() {
        return modeRef.get();
    }

    public void setMode(String mode) {
        if (mode == null) {
            return;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (!"next".equals(normalized)) {
            normalized = "legacy";
        }
        modeRef.set(normalized);
    }

    /**
     * 旧版 /app/commander 已下线，默认入口恒为 commander-next；本标志对 HTTP 重定向不再分支，仅保留运维 API 兼容。
     */
    public boolean useNextRoute() {
        return true;
    }
}
