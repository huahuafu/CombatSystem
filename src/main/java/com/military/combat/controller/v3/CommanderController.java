package com.military.combat.controller.v3;

import com.military.combat.controller.dto.DeploymentObjectiveDraft;
import com.military.combat.controller.dto.DeploymentUnitDraft;
import com.military.combat.controller.dto.FullScenarioCreateRequest;
import com.military.combat.service.CommanderService;
import com.military.combat.simulation.commander.CommanderExecuteRequest;
import com.military.combat.simulation.commander.CommanderExecuteResponse;
import com.military.combat.simulation.commander.CommanderGenerateRequest;
import com.military.combat.simulation.commander.CommanderGenerateResponse;
import com.military.combat.simulation.commander.CommanderReplayRequest;
import com.military.combat.simulation.commander.CommanderRunRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/combat/v3/commander")
public class CommanderController {

    @Autowired
    private CommanderService commanderService;

    @PostMapping("/strategies/generate")
    public CommanderGenerateResponse generate(@RequestBody(required = false) CommanderGenerateRequest body) {
        return commanderService.generate(body);
    }

    @PostMapping("/strategies/execute")
    public CommanderExecuteResponse execute(@RequestBody CommanderExecuteRequest body) {
        return commanderService.execute(body);
    }

    @GetMapping("/runs/{requestId}")
    public CommanderRunRecord getRun(@PathVariable String requestId) {
        return commanderService.getRun(requestId);
    }

    /**
     * 回放：按历史 run 的 seed/策略/输入重新执行（默认不落库，确保“纯复现”）。
     */
    @PostMapping("/runs/{requestId}/replay")
    public CommanderExecuteResponse replay(@PathVariable String requestId,
                                           @RequestBody(required = false) CommanderReplayRequest body) {
        CommanderRunRecord rec = commanderService.getRun(requestId);
        if (rec == null) {
            throw new IllegalArgumentException("run 记录不存在: " + requestId);
        }
        CommanderReplayRequest b = body == null ? new CommanderReplayRequest() : body;
        return commanderService.replayFromRecord(rec, b.getRounds(), b.getSeed(), b.isPersist());
    }

    /**
     * 删除全部指挥官运行记录（Mongo {@code commander_runs}），用于清理「运行回放」列表中的旧数据。
     */
    @DeleteMapping("/runs")
    public Map<String, Object> purgeAllRuns() {
        long n = commanderService.purgeAllCommanderRuns();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("purgedCount", n);
        out.put("message", "已清空运行列表");
        return out;
    }

    /**
     * 轻量运行列表：用于审计/回放入口（仅返回最近若干条）。
     * <p>当前实现依赖 Mongo 默认排序（按 _id），用于 MVP；上线建议增加 createdAt 索引与分页。</p>
     */
    @GetMapping("/runs")
    public java.util.List<CommanderRunRecord> listRuns(@RequestParam(required = false) String scenarioId,
                                                       @RequestParam(required = false, defaultValue = "20") int limit) {
        var all = commanderService.listRecentRuns(scenarioId, limit);
        return all == null ? java.util.List.of() : all;
    }

    @PostMapping("/deployment/validate")
    public Map<String, Object> validateDeployment(@RequestBody(required = false) FullScenarioCreateRequest body) {
        FullScenarioCreateRequest req = body == null ? new FullScenarioCreateRequest() : body;
        List<String> issues = new ArrayList<>();
        List<DeploymentUnitDraft> units = req.getUnits() == null ? List.of() : req.getUnits();
        List<DeploymentObjectiveDraft> objectives = req.getObjectives() == null ? List.of() : req.getObjectives();
        if (units.isEmpty()) {
            issues.add("至少部署一个作战单位");
        }
        if (objectives.isEmpty()) {
            issues.add("至少设置一个作战目标");
        }
        for (int i = 0; i < units.size(); i++) {
            DeploymentUnitDraft u = units.get(i);
            if (u == null || u.getLatitude() == null || u.getLongitude() == null) {
                issues.add("单位#" + (i + 1) + " 缺少坐标");
                continue;
            }
            if (Math.abs(u.getLatitude()) > 90 || Math.abs(u.getLongitude()) > 180) {
                issues.add("单位#" + (i + 1) + " 坐标越界");
            }
            if (u.getSide() == null || u.getSide().isBlank()) {
                issues.add("单位#" + (i + 1) + " 缺少阵营");
            }
        }
        for (int i = 0; i < objectives.size(); i++) {
            DeploymentObjectiveDraft o = objectives.get(i);
            if (o == null || o.getLatitude() == null || o.getLongitude() == null) {
                issues.add("目标#" + (i + 1) + " 缺少坐标");
                continue;
            }
            if (Math.abs(o.getLatitude()) > 90 || Math.abs(o.getLongitude()) > 180) {
                issues.add("目标#" + (i + 1) + " 坐标越界");
            }
            if (o.getType() == null || o.getType().isBlank()) {
                issues.add("目标#" + (i + 1) + " 缺少类型");
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("valid", issues.isEmpty());
        out.put("issues", issues);
        out.put("unitCount", units.size());
        out.put("objectiveCount", objectives.size());
        return out;
    }
}

