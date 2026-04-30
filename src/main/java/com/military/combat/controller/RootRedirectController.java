package com.military.combat.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URI;

/**
 * 默认入口：强制跳转到指挥官工作台（唯一 SPA：commander-next），避免多入口分叉。
 */
@Controller
public class RootRedirectController {

    @GetMapping("/")
    public ResponseEntity<Void> root() {
        return redirectToHomeRoute();
    }

    /** 常见默认页名；静态目录未提供 index.html，与根路径一致跳转到工作台。 */
    @GetMapping("/index.html")
    public ResponseEntity<Void> indexHtml() {
        return redirectToHomeRoute();
    }

    /** 兼容旧入口：旧版指挥官 SPA 已下线，统一进入 commander-next。 */
    @GetMapping("/commander.html")
    public ResponseEntity<Void> legacyCommander() {
        return redirectTo("/app/commander-next");
    }

    /** 兼容历史路由。 */
    @GetMapping("/commander")
    public ResponseEntity<Void> commanderRoute() {
        return redirectTo("/app/commander-next");
    }

    /** 兼容历史路由。 */
    @GetMapping("/commander-next")
    public ResponseEntity<Void> commanderNextRoute() {
        return redirectTo("/app/commander-next");
    }

    /** 兼容旧入口。 */
    @GetMapping("/simulation-dashboard.html")
    public ResponseEntity<Void> legacySimulationDashboard() {
        return redirectTo("/app/simulation-dashboard");
    }

    /** 已删除静态页，历史 URL 仍 302 到默认工作台。 */
    @GetMapping("/portal.html")
    public ResponseEntity<Void> legacyPortal() {
        return redirectToHomeRoute();
    }

    @GetMapping("/scenario-studio.html")
    public ResponseEntity<Void> legacyScenarioStudio() {
        return redirectToHomeRoute();
    }

    @GetMapping("/activity-rule-center.html")
    public ResponseEntity<Void> legacyActivityRuleCenter() {
        return redirectToHomeRoute();
    }

    @GetMapping("/batch-lab.html")
    public ResponseEntity<Void> legacyBatchLab() {
        return redirectToHomeRoute();
    }

    /** 运行回放 / 战役中心：历史 .html 仍 302。 */
    @GetMapping("/run-center.html")
    public ResponseEntity<Void> legacyRunCenter() {
        return redirectTo("/app/run-center");
    }

    @GetMapping("/campaign-center.html")
    public ResponseEntity<Void> legacyCampaignCenter() {
        return redirectTo("/app/campaign-center");
    }

    /**
     * SPA 路由入口。
     * 说明：/app 下的非静态资源路径统一 forward 到 /app/index.html，
     * 由 React Router 接管页面路由。
     */
    @RequestMapping({
            "/app",
            "/app/",
            "/app/{path:[^\\.]*}",
            "/app/{path1:[^\\.]*}/{path2:[^\\.]*}",
            "/app/{path1:[^\\.]*}/{path2:[^\\.]*}/{path3:[^\\.]*}"
    })
    public String appFallback() {
        return "forward:/app/index.html";
    }

    private static ResponseEntity<Void> redirectToHomeRoute() {
        return redirectTo("/app/commander-next");
    }

    private static ResponseEntity<Void> redirectTo(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(path));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
