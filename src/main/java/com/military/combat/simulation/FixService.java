package com.military.combat.simulation;

import com.military.combat.entity.FindContactReport;
import com.military.combat.entity.ScenarioData;
import com.military.combat.repository.FindContactReportRepository;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fix（定位）环节：对 Find 接触报告进行多源融合与定位质量评估。
 */
@Service
public class FixService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private FindContactReportRepository reportRepository;

    @Autowired
    private SortieEffectService sortieEffectService;

    @Autowired
    private CommandEffectService commandEffectService;

    public FixSnapshot runFusionForCurrentRound() {
        return getFixSnapshot();
    }

    public FixSnapshot getFixSnapshot() {
        FixSnapshot out = new FixSnapshot();
        String sid = scenarioService.getActiveScenarioId();
        out.setScenarioId(sid);
        out.setRound(scenarioService.getCurrentRound());
        out.setGeneratedAt(System.currentTimeMillis());

        if (sid == null || sid.isEmpty()) {
            out.setScenarioName("未激活想定");
            out.setReadinessLevel("LOW");
            out.getWeaknesses().add("未激活想定，定位链路未启动。");
            return out;
        }

        ScenarioData sd = scenarioService.getScenarioDataById(sid);
        out.setScenarioName(sd != null ? sd.getName() : null);

        List<FindContactReport> reports = reportRepository.findTop200ByScenarioIdOrderByTimestampDesc(sid);
        Map<String, List<FindContactReport>> grouped = groupByTarget(reports);

        int candidate = grouped.size();
        int fused = 0;
        int fixed = 0;
        double consistencySum = 0;
        double errorSum = 0;
        int near = 0;
        int mid = 0;
        int far = 0;

        for (Map.Entry<String, List<FindContactReport>> en : grouped.entrySet()) {
            List<FindContactReport> rs = en.getValue();
            if (rs.isEmpty()) {
                continue;
            }
            int sourceCount = maxSensorCount(rs);
            double avgConfidence = avgConfidence(rs);
            double consistency = geoConsistency(rs);
            double estError = estimatePositionError(sourceCount, avgConfidence, consistency);

            boolean fusedOk = sourceCount >= 2;
            boolean fixedOk = fusedOk && avgConfidence >= 0.75 && estError <= 500;
            if (fusedOk) fused++;
            if (fixedOk) fixed++;

            consistencySum += consistency;
            errorSum += estError;

            if (estError <= 200) near++;
            else if (estError <= 1000) mid++;
            else far++;
        }

        double candidateDen = Math.max(1, candidate);
        out.setCandidateCount(candidate);
        out.setFusedTargetCount(fused);
        out.setFixedTargetCount(fixed);
        SortieEffectService.SortieEffectSummary sortie = sortieEffectService.currentSummary();
        CommandEffectService.CommandEffectSummary cmd = commandEffectService.currentSummary();
        out.setMultiSourceFusionRate(round3(clamp01(fused / candidateDen + sortie.isrBoost() * 0.8 + cmd.isrBoost() * 0.9)));
        out.setGeoConsistencyRate(round3(clamp01(consistencySum / candidateDen + sortie.isrBoost() * 0.6 + cmd.isrBoost() * 0.7)));
        out.setAvgPositionErrorMeters(round3(Math.max(50, errorSum / candidateDen - sortie.isrBoost() * 600 - cmd.isrBoost() * 700)));
        out.setNearLayerCoverage(round3(near / candidateDen));
        out.setMidLayerCoverage(round3(mid / candidateDen));
        out.setFarLayerCoverage(round3(far / candidateDen));

        evaluate(out);
        out.setReadinessLevel(deriveReadiness(out));
        return out;
    }

    private Map<String, List<FindContactReport>> groupByTarget(List<FindContactReport> reports) {
        Map<String, List<FindContactReport>> grouped = new HashMap<>();
        for (FindContactReport r : reports) {
            if (r == null || r.isFalseAlarm() || r.getTargetUnitId() == null || r.getTargetUnitId().isEmpty()) {
                continue;
            }
            grouped.computeIfAbsent(r.getTargetUnitId(), k -> new ArrayList<>()).add(r);
        }
        return grouped;
    }

    private int maxSensorCount(List<FindContactReport> rs) {
        int m = 0;
        for (FindContactReport r : rs) {
            int n = (r.getSensorSources() == null) ? 0 : r.getSensorSources().size();
            if (n > m) m = n;
        }
        return m;
    }

    private double avgConfidence(List<FindContactReport> rs) {
        if (rs.isEmpty()) return 0;
        double sum = 0;
        for (FindContactReport r : rs) {
            sum += r.getConfidence();
        }
        return sum / rs.size();
    }

    private double geoConsistency(List<FindContactReport> rs) {
        if (rs.size() <= 1) {
            return 0.6;
        }
        // 简化一致性：传感器越多、置信度越高，一致性越高
        int source = maxSensorCount(rs);
        double conf = avgConfidence(rs);
        return Math.max(0, Math.min(1, 0.35 + source * 0.12 + conf * 0.35));
    }

    private double estimatePositionError(int sourceCount, double confidence, double consistency) {
        // 误差估算：多源 + 高置信 + 高一致性 => 误差小
        double base = 1600;
        base -= sourceCount * 220;
        base -= confidence * 500;
        base -= consistency * 420;
        return Math.max(80, base);
    }

    private void evaluate(FixSnapshot out) {
        if (out.getMultiSourceFusionRate() >= 0.8) {
            out.getStrengths().add("多源融合率达标（>=80%）。");
        } else {
            out.getWeaknesses().add("多源融合率不足（当前 " + pct(out.getMultiSourceFusionRate()) + "）。");
        }
        if (out.getGeoConsistencyRate() >= 0.85) {
            out.getStrengths().add("定位一致性达标（>=85%）。");
        } else {
            out.getWeaknesses().add("坐标一致性不足（当前 " + pct(out.getGeoConsistencyRate()) + "）。");
        }
        if (out.getAvgPositionErrorMeters() <= 500) {
            out.getStrengths().add("定位误差控制良好（<=500m）。");
        } else {
            out.getWeaknesses().add("平均定位误差偏大（当前 " + round3(out.getAvgPositionErrorMeters()) + "m）。");
        }
        if (out.getFixedTargetCount() > 0) {
            out.getStrengths().add("已形成可移交 Track/Target 的定位目标 " + out.getFixedTargetCount() + " 个。");
        } else {
            out.getWeaknesses().add("未形成可移交目标，后续环节将被阻塞。");
        }
    }

    private String deriveReadiness(FixSnapshot out) {
        int weakness = out.getWeaknesses().size();
        if (weakness <= 1) return "HIGH";
        if (weakness <= 3) return "MEDIUM";
        return "LOW";
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private String pct(double v) {
        return (int) Math.round(v * 100) + "%";
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
