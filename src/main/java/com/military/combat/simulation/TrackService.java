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
 * Track（跟踪）环节：承接 Fix 目标池，评估持续跟踪质量。
 */
@Service
public class TrackService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private FindContactReportRepository reportRepository;

    @Autowired
    private FixService fixService;

    @Autowired
    private SortieEffectService sortieEffectService;

    @Autowired
    private CommandEffectService commandEffectService;

    @Autowired
    private OpposingEffectService opposingEffectService;

    public TrackSnapshot runTrackForCurrentRound() {
        return getTrackSnapshot();
    }

    public TrackSnapshot getTrackSnapshot() {
        TrackSnapshot out = new TrackSnapshot();
        String sid = scenarioService.getActiveScenarioId();
        out.setScenarioId(sid);
        out.setRound(scenarioService.getCurrentRound());
        out.setGeneratedAt(System.currentTimeMillis());

        if (sid == null || sid.isEmpty()) {
            out.setScenarioName("未激活想定");
            out.setReadinessLevel("LOW");
            out.getWeaknesses().add("未激活想定，跟踪链路未启动。");
            return out;
        }

        ScenarioData sd = scenarioService.getScenarioDataById(sid);
        out.setScenarioName(sd != null ? sd.getName() : null);

        FixSnapshot fix = fixService.getFixSnapshot();
        out.setHandoffTargetCount(fix.getFixedTargetCount());

        List<FindContactReport> reports = reportRepository.findTop200ByScenarioIdOrderByTimestampDesc(sid);
        Map<String, List<FindContactReport>> grouped = groupByTarget(reports);

        int tracked = 0;
        int highSpeedTracked = 0;
        int lost = 0;
        double updateSecSum = 0;
        double stabilitySum = 0;
        int effective = 0;

        for (Map.Entry<String, List<FindContactReport>> en : grouped.entrySet()) {
            List<FindContactReport> rs = en.getValue();
            if (rs.size() < 2) {
                lost++;
                continue;
            }
            double avgConf = avgConfidence(rs);
            double update = estimateUpdateSeconds(rs);
            double stability = estimateStability(rs, avgConf, update);
            boolean stableTrack = avgConf >= 0.72 && update <= 10 && stability >= 0.7;
            if (stableTrack) {
                tracked++;
                if (avgSpeed(rs) >= 20) {
                    highSpeedTracked++;
                }
            } else {
                lost++;
            }
            updateSecSum += update;
            stabilitySum += stability;
            effective++;
        }

        double handoffDen = Math.max(1, out.getHandoffTargetCount());
        out.setTrackedTargetCount(tracked);
        out.setHighSpeedTrackedCount(highSpeedTracked);
        SortieEffectService.SortieEffectSummary sortie = sortieEffectService.currentSummary();
        CommandEffectService.CommandEffectSummary cmd = commandEffectService.currentSummary();
        OpposingEffectService.OpposingEffectSummary opp = opposingEffectService.currentSummary();
        out.setTrackContinuityRate(round3(clamp01(tracked / handoffDen + sortie.isrBoost() * 0.9 + sortie.ewBoost() * 0.3 + cmd.isrBoost() * 0.8 - opp.counterRecon() * 0.6 - opp.ewSuppression() * 0.5)));
        out.setTrackLossRate(round3(clamp01(lost / handoffDen - sortie.ewBoost() * 0.5 - cmd.suppression() * 0.6 + opp.ewSuppression() * 0.7 + opp.decoy() * 0.5)));
        out.setTrackUpdateSeconds(round3(Math.max(1.5, (effective == 0 ? 30 : updateSecSum / effective) - sortie.isrBoost() * 10 - cmd.isrBoost() * 8 + opp.ewSuppression() * 9)));
        out.setPredictionStabilityRate(round3(clamp01((effective == 0 ? 0 : stabilitySum / effective) + sortie.isrBoost() * 0.4 + sortie.ewBoost() * 0.4 + cmd.ewBoost() * 0.5 - opp.decoy() * 0.6)));

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

    private double avgConfidence(List<FindContactReport> rs) {
        if (rs.isEmpty()) return 0;
        double sum = 0;
        for (FindContactReport r : rs) sum += r.getConfidence();
        return sum / rs.size();
    }

    private double avgSpeed(List<FindContactReport> rs) {
        if (rs.isEmpty()) return 0;
        double sum = 0;
        for (FindContactReport r : rs) sum += r.getSpeed();
        return sum / rs.size();
    }

    private double estimateUpdateSeconds(List<FindContactReport> rs) {
        if (rs.size() < 2) return 30;
        long newest = rs.get(0).getTimestamp();
        long oldest = rs.get(rs.size() - 1).getTimestamp();
        long diff = Math.max(1, newest - oldest);
        return diff / 1000.0 / Math.max(1, rs.size() - 1);
    }

    private double estimateStability(List<FindContactReport> rs, double conf, double updateSeconds) {
        double s = 0.3 + conf * 0.5 + Math.max(0, Math.min(1, (12 - updateSeconds) / 12.0)) * 0.2;
        return Math.max(0, Math.min(1, s));
    }

    private void evaluate(TrackSnapshot out) {
        if (out.getTrackContinuityRate() >= 0.85) {
            out.getStrengths().add("轨迹连续性达标（>=85%）。");
        } else {
            out.getWeaknesses().add("轨迹连续性不足（当前 " + pct(out.getTrackContinuityRate()) + "）。");
        }
        if (out.getTrackLossRate() <= 0.1) {
            out.getStrengths().add("丢轨率控制良好（<=10%）。");
        } else {
            out.getWeaknesses().add("丢轨率偏高（当前 " + pct(out.getTrackLossRate()) + "）。");
        }
        if (out.getTrackUpdateSeconds() <= 10) {
            out.getStrengths().add("轨迹更新频率达标（<=10秒）。");
        } else {
            out.getWeaknesses().add("轨迹更新过慢（当前 " + round3(out.getTrackUpdateSeconds()) + " 秒）。");
        }
        if (out.getPredictionStabilityRate() >= 0.8) {
            out.getStrengths().add("轨迹预测稳定性良好。");
        } else {
            out.getWeaknesses().add("轨迹预测稳定性不足（当前 " + pct(out.getPredictionStabilityRate()) + "）。");
        }
        if (out.getTrackedTargetCount() == 0) {
            out.getWeaknesses().add("当前无稳定跟踪目标，Target环节将受阻。");
        }
    }

    private String deriveReadiness(TrackSnapshot out) {
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
