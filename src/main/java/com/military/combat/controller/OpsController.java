package com.military.combat.controller;

import com.military.combat.service.FastApiStrategyClient;
import com.military.combat.service.MigrationModeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/combat/ops")
public class OpsController {
    private final MigrationModeService migrationModeService;
    private final FastApiStrategyClient fastApiStrategyClient;

    public OpsController(MigrationModeService migrationModeService, FastApiStrategyClient fastApiStrategyClient) {
        this.migrationModeService = migrationModeService;
        this.fastApiStrategyClient = fastApiStrategyClient;
    }

    @GetMapping("/migration/status")
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", migrationModeService.getMode());
        out.put("nextRouteEnabled", migrationModeService.useNextRoute());
        out.put("fastApiHealthy", fastApiStrategyClient.health());
        return out;
    }

    @PostMapping("/migration/mode")
    public Map<String, Object> switchMode(@RequestParam String value) {
        migrationModeService.setMode(value);
        return status();
    }
}
