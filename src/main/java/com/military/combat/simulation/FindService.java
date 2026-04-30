package com.military.combat.simulation;

import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.FindContactReport;
import com.military.combat.entity.ScenarioData;
import com.military.combat.entity.Terrain;
import com.military.combat.repository.FindContactReportRepository;
import com.military.combat.service.CombatUnitService;
import com.military.combat.service.ScenarioService;
import com.military.combat.service.TerrainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Find 环节：多源探测、识别、标准化上报。
 */
@Service
public class FindService {

    private static final double TARGET_PD_STANDARD = 0.90;
    private static final double FALSE_ALARM_STANDARD = 0.01;
    private static final double ID_ACC_STANDARD = 0.95;
    private static final double REPORTING_LATENCY_STANDARD = 60.0;
    private static final double UPDATE_INTERVAL_STANDARD = 10.0;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private TerrainService terrainService;

    @Autowired
    private FindContactReportRepository reportRepository;

    @Autowired
    private SortieEffectService sortieEffectService;

    @Autowired
    private CommandEffectService commandEffectService;

    @Autowired
    private OpposingEffectService opposingEffectService;

    @Autowired
    private com.military.combat.simulation.random.SimulationRandom simulationRandom;

    public List<FindContactReport> runFindScanForCurrentRound() {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) {
            return new ArrayList<>();
        }
        int round = scenarioService.getCurrentRound();
        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine().stream()
                .filter(u -> u != null && u.getCombatPower() > 0)
                .collect(Collectors.toList());
        if (units.isEmpty()) {
            return new ArrayList<>();
        }

        // 稳定排序：避免底层查询/集合顺序波动导致同 seed 下输出漂移
        units.sort(Comparator.comparing(u -> u.getId() == null ? "" : u.getId()));

        List<FindContactReport> out = new ArrayList<>();
        for (CombatUnit unit : units) {
            FindContactReport report = maybeDetectTarget(sid, round, unit);
            if (report != null) {
                out.add(reportRepository.save(report));
            }
        }
        // 注入少量虚警，体现杂波干扰场景
        SplittableRandom faRng = simulationRandom.rng("FIND_FALSE_ALARM", round, sid);
        int falseAlarmCount = faRng.nextDouble() < 0.25 ? 1 : 0;
        for (int i = 0; i < falseAlarmCount; i++) {
            out.add(reportRepository.save(generateFalseAlarm(sid, round)));
        }
        return out;
    }

    public FindSnapshot getFindSnapshot() {
        FindSnapshot snapshot = new FindSnapshot();
        String sid = scenarioService.getActiveScenarioId();
        snapshot.setScenarioId(sid);
        snapshot.setRound(scenarioService.getCurrentRound());
        snapshot.setGeneratedAt(System.currentTimeMillis());

        if (sid == null || sid.isEmpty()) {
            snapshot.setScenarioName("未激活想定");
            snapshot.setReadinessLevel("LOW");
            snapshot.getWeaknesses().add("未激活想定，Find 链路未启动。");
            return snapshot;
        }

        ScenarioData sd = scenarioService.getScenarioDataById(sid);
        snapshot.setScenarioName(sd != null ? sd.getName() : null);

        int round = scenarioService.getCurrentRound();
        List<FindContactReport> thisRound = reportRepository.findByScenarioIdAndRound(sid, round);
        List<FindContactReport> recent = reportRepository.findTop200ByScenarioIdOrderByTimestampDesc(sid);
        List<CombatUnit> aliveUnits = combatUnitService.getUnitsForBattleEngine().stream()
                .filter(u -> u != null && u.getCombatPower() > 0)
                .collect(Collectors.toList());

        int targetCount = Math.max(1, aliveUnits.size());
        int detected = (int) thisRound.stream().filter(r -> !r.isFalseAlarm()).count();
        int identified = (int) thisRound.stream().filter(FindContactReport::isIdentified).count();
        int falseAlarms = (int) thisRound.stream().filter(FindContactReport::isFalseAlarm).count();
        int highThreat = (int) thisRound.stream().filter(r -> "HIGH".equals(r.getThreatLevel())).count();

        SortieEffectService.SortieEffectSummary sortie = sortieEffectService.currentSummary();
        CommandEffectService.CommandEffectSummary cmd = commandEffectService.currentSummary();
        OpposingEffectService.OpposingEffectSummary opp = opposingEffectService.currentSummary();

        double pd = clamp01((double) detected / targetCount + sortie.isrBoost() + cmd.isrBoost() * 0.8 - opp.counterRecon() * 0.8 - opp.ewSuppression() * 0.4);
        double pfaDen = Math.max(1, detected + falseAlarms);
        double pfa = clamp01((double) falseAlarms / pfaDen - sortie.ewSuppression() - cmd.suppression() + opp.decoy() * 0.6);
        double idAccDen = Math.max(1, detected);
        double idAcc = clamp01((double) identified / idAccDen + sortie.isrBoost() * 0.7 + cmd.isrBoost() * 0.6 - opp.decoy() * 0.5);
        double reportingLatency = Math.max(5, estimateReportingLatencySeconds(thisRound) - sortie.isrBoost() * 30 - cmd.isrBoost() * 25 + opp.ewSuppression() * 40);
        double updateInterval = Math.max(2, estimateUpdateIntervalSeconds(recent) - sortie.isrBoost() * 8 - cmd.isrBoost() * 6 + opp.ewSuppression() * 7);

        snapshot.setDetectionProbability(round3(pd));
        snapshot.setFalseAlarmRate(round3(pfa));
        snapshot.setIdentificationAccuracy(round3(idAcc));
        snapshot.setReportingLatencySeconds(round3(reportingLatency));
        snapshot.setUpdateIntervalSeconds(round3(updateInterval));

        snapshot.setDetectionsThisRound(detected);
        snapshot.setIdentifiedThisRound(identified);
        snapshot.setHighThreatContacts(highThreat);

        applyCoverage(snapshot, aliveUnits, thisRound);
        buildStrengthWeakness(snapshot);

        snapshot.setLatestContacts(recent.stream().limit(8).collect(Collectors.toList()));
        snapshot.setReadinessLevel(deriveReadiness(snapshot));
        return snapshot;
    }

    private FindContactReport maybeDetectTarget(String sid, int round, CombatUnit unit) {
        double pd = estimateDetectionProbability(unit);
        SplittableRandom rng = simulationRandom.rng("FIND_DETECT", round, unit == null ? null : unit.getId());
        if (rng.nextDouble() > pd) {
            return null;
        }
        return buildContact(sid, round, unit, false);
    }

    private FindContactReport buildContact(String sid, int round, CombatUnit unit, boolean falseAlarm) {
        SplittableRandom rng = simulationRandom.rng(
                "FIND_CONTACT",
                round,
                falseAlarm ? ("FA@" + sid + "#" + round) : (unit == null ? null : unit.getId())
        );
        FindContactReport report = new FindContactReport();
        report.setId(UUID.randomUUID().toString());
        report.setContactId("FIND-" + round + "-" + Math.abs(rng.nextInt(99999)));
        report.setScenarioId(sid);
        report.setRound(round);
        report.setTimestamp(System.currentTimeMillis());
        report.setFalseAlarm(falseAlarm);

        List<String> sensors = chooseSensors(unit, falseAlarm);
        report.setSensorSources(sensors);
        report.setConfidence(round3(estimateConfidence(unit, sensors, falseAlarm)));

        if (falseAlarm) {
            report.setTargetUnitId(null);
            report.setTargetName("疑似杂波目标");
            report.setTargetType("UNKNOWN");
            report.setLatitude(39.0 + rng.nextDouble() * 3);
            report.setLongitude(116.0 + rng.nextDouble() * 4);
            report.setX(10 + rng.nextDouble() * 80);
            report.setY(10 + rng.nextDouble() * 80);
            report.setSpeed(round3(2 + rng.nextDouble() * 12));
            report.setHeading(rng.nextInt(360));
            report.setFeatureSignature("低信噪比回波/多径反射");
            report.setFriendFoe("UNKNOWN");
            report.setMilitaryOrCivilian("UNKNOWN");
            report.setMobility("UNKNOWN");
            report.setCamouflageState("DECOY");
            report.setThreatLevel("LOW");
            report.setCombatIntent("UNKNOWN");
            report.setValueScore(10 + rng.nextInt(15));
            report.setIdentified(false);
            return report;
        }

        report.setTargetUnitId(unit.getId());
        report.setTargetName(unit.getName());
        report.setTargetType(normalizedType(unit));
        report.setLatitude(unit.getLatitude());
        report.setLongitude(unit.getLongitude());
        report.setX(unit.getX());
        report.setY(unit.getY());
        report.setSpeed(round3(Math.max(0, unit.getSpeed())));
        report.setHeading(rng.nextInt(360));
        report.setFeatureSignature(buildFeatureSignature(unit));
        report.setFriendFoe("BLUE".equals(unit.getSide()) ? "FOE" : "FRIEND");
        report.setMilitaryOrCivilian("MILITARY");
        report.setMobility(unit.getSpeed() > 0 ? "MOBILE" : "STATIC");
        report.setCamouflageState(rng.nextDouble() < 0.2 ? "CAMOUFLAGED" : "REAL");
        report.setThreatLevel(assessThreatLevel(unit));
        report.setCombatIntent(estimateIntent(unit));
        report.setValueScore(estimateValue(unit));
        report.setIdentified(report.getConfidence() >= 0.72);
        return report;
    }

    private FindContactReport generateFalseAlarm(String sid, int round) {
        return buildContact(sid, round, null, true);
    }

    private double estimateDetectionProbability(CombatUnit unit) {
        if (unit == null) {
            return 0;
        }
        double base = 0.88;
        String type = normalizedType(unit);
        if ("FIGHTER".equals(type) || "BOMBER".equals(type)) {
            base += 0.05;
        }
        if ("INFANTRY".equals(type)) {
            base -= 0.08;
        }
        Terrain terrain = terrainService.getUnitTerrain(unit);
        if (terrain != null && "FOREST".equals(terrain.getType())) {
            base -= 0.12;
        }
        if (terrain != null && "SWAMP".equals(terrain.getType())) {
            base -= 0.06;
        }
        SplittableRandom rng = simulationRandom.rng("FIND_PD_JAM", scenarioService.getCurrentRound(),
                unit == null ? null : unit.getId());
        if (rng.nextDouble() < 0.18) { // 电磁压制
            base -= 0.10;
        }
        return clamp01(base);
    }

    private List<String> chooseSensors(CombatUnit unit, boolean falseAlarm) {
        List<String> sensors = new ArrayList<>();
        sensors.add("SPACE_SAR");
        sensors.add("AIR_AEW_RADAR");
        if (unit != null && ("FIGHTER".equals(normalizedType(unit)) || "BOMBER".equals(normalizedType(unit)))) {
            sensors.add("GROUND_LONG_RANGE_RADAR");
            sensors.add("ELINT");
        } else {
            sensors.add("UAV_EOIR");
            sensors.add("GROUND_RADAR");
        }
        if (falseAlarm) {
            sensors = sensors.stream().limit(2).collect(Collectors.toList());
        }
        return sensors;
    }

    private double estimateConfidence(CombatUnit unit, List<String> sensors, boolean falseAlarm) {
        double c = 0.45 + sensors.size() * 0.1;
        if (unit != null && unit.getLatitude() != null && unit.getLongitude() != null) {
            c += 0.1;
        }
        if (falseAlarm) {
            c -= 0.35;
        }
        return clamp01(c);
    }

    private void applyCoverage(FindSnapshot snapshot, List<CombatUnit> aliveUnits, List<FindContactReport> thisRound) {
        boolean hasAir = aliveUnits.stream().anyMatch(u -> {
            String t = normalizedType(u);
            return "FIGHTER".equals(t) || "BOMBER".equals(t) || "AIR".equals(t);
        });
        boolean hasGround = aliveUnits.stream().anyMatch(u -> {
            String t = normalizedType(u);
            return "INFANTRY".equals(t) || "TANK".equals(t) || "ARTILLERY".equals(t) || "MECH_INFANTRY".equals(t);
        });
        boolean hasSea = aliveUnits.stream().anyMatch(u -> {
            String t = normalizedType(u);
            return t.contains("SHIP") || t.contains("NAVAL") || t.contains("SUBMARINE");
        });

        snapshot.setDomainLandCovered(hasGround);
        snapshot.setDomainSeaCovered(hasSea || thisRound.stream().anyMatch(r -> "SONAR".equals(anySensor(r))));
        snapshot.setDomainAirCovered(hasAir);
        snapshot.setDomainSpaceCovered(thisRound.stream().anyMatch(r -> hasSensor(r, "SPACE_SAR")));
        snapshot.setDomainElectromagneticCovered(thisRound.stream().anyMatch(r -> hasSensor(r, "ELINT")));
    }

    private void buildStrengthWeakness(FindSnapshot snapshot) {
        if (snapshot.getDetectionProbability() >= TARGET_PD_STANDARD) {
            snapshot.getStrengths().add("探测概率达标（>=90%）。");
        } else {
            snapshot.getWeaknesses().add("探测概率不足（当前 " + pct(snapshot.getDetectionProbability()) + "）。");
        }
        if (snapshot.getFalseAlarmRate() <= FALSE_ALARM_STANDARD) {
            snapshot.getStrengths().add("虚警控制达标（<=1%）。");
        } else {
            snapshot.getWeaknesses().add("虚警偏高（当前 " + pct(snapshot.getFalseAlarmRate()) + "）。");
        }
        if (snapshot.getIdentificationAccuracy() >= ID_ACC_STANDARD) {
            snapshot.getStrengths().add("识别准确率达标（>=95%）。");
        } else {
            snapshot.getWeaknesses().add("识别准确率不足（当前 " + pct(snapshot.getIdentificationAccuracy()) + "）。");
        }
        if (snapshot.getReportingLatencySeconds() <= REPORTING_LATENCY_STANDARD) {
            snapshot.getStrengths().add("发现-识别-上报时延达标（<=60秒）。");
        } else {
            snapshot.getWeaknesses().add("上报时延过长（当前 " + round3(snapshot.getReportingLatencySeconds()) + " 秒）。");
        }
        if (snapshot.getUpdateIntervalSeconds() <= UPDATE_INTERVAL_STANDARD) {
            snapshot.getStrengths().add("数据更新速度达标（<=10秒）。");
        } else {
            snapshot.getWeaknesses().add("数据刷新频率不足（当前 " + round3(snapshot.getUpdateIntervalSeconds()) + " 秒）。");
        }
        if (!(snapshot.isDomainLandCovered() && snapshot.isDomainAirCovered() && snapshot.isDomainSpaceCovered()
                && snapshot.isDomainElectromagneticCovered())) {
            snapshot.getWeaknesses().add("全域覆盖不完整（陆/空/天/电至少存在短板）。");
        } else {
            snapshot.getStrengths().add("陆空天电多域覆盖已形成。");
        }
    }

    private String deriveReadiness(FindSnapshot snapshot) {
        int weakness = snapshot.getWeaknesses().size();
        if (weakness <= 1) {
            return "HIGH";
        }
        if (weakness <= 3) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double estimateReportingLatencySeconds(List<FindContactReport> thisRound) {
        if (thisRound == null || thisRound.isEmpty()) {
            return 120;
        }
        SplittableRandom rng = simulationRandom.rng("FIND_REPORT_LATENCY", scenarioService.getCurrentRound(),
                scenarioService.getActiveScenarioId());
        return 12 + rng.nextDouble() * 30;
    }

    private double estimateUpdateIntervalSeconds(List<FindContactReport> recent) {
        if (recent == null || recent.size() < 2) {
            return 30;
        }
        long newest = recent.get(0).getTimestamp();
        long oldest = recent.get(Math.min(9, recent.size() - 1)).getTimestamp();
        long diff = Math.max(1, newest - oldest);
        int span = Math.max(1, Math.min(9, recent.size() - 1));
        return diff / 1000.0 / span;
    }

    private String buildFeatureSignature(CombatUnit unit) {
        StringBuilder sb = new StringBuilder();
        sb.append("RCS=").append(estimateRcs(unit)).append("m2");
        sb.append(", IR=").append(estimateIr(unit));
        sb.append(", EM=").append(estimateEm(unit));
        return sb.toString();
    }

    private String assessThreatLevel(CombatUnit unit) {
        if (unit == null) return "LOW";
        int score = estimateValue(unit);
        if (score >= 75) return "HIGH";
        if (score >= 45) return "MEDIUM";
        return "LOW";
    }

    private String estimateIntent(CombatUnit unit) {
        if (unit == null || unit.getMission() == null) return "UNKNOWN";
        String m = unit.getMission().toUpperCase(Locale.ROOT);
        if (m.contains("ATTACK") || m.contains("进攻") || m.contains("突击")) return "ATTACK";
        if (m.contains("DEFEND") || m.contains("防御")) return "DEFEND";
        if (m.contains("RECON") || m.contains("侦察")) return "RECON";
        if (m.contains("SUPPORT") || m.contains("支援")) return "SUPPORT";
        return "UNKNOWN";
    }

    private int estimateValue(CombatUnit unit) {
        if (unit == null) return 0;
        int score = 30;
        String type = normalizedType(unit);
        if ("TANK".equals(type)) score += 25;
        if ("ARTILLERY".equals(type) || "ROCKET_ARTILLERY".equals(type)) score += 28;
        if ("FIGHTER".equals(type) || "BOMBER".equals(type)) score += 35;
        if ("AA_GUN".equals(type)) score += 20;
        score += Math.min(20, Math.max(0, unit.getCombatPower() / 6));
        return Math.max(0, Math.min(100, score));
    }

    private String normalizedType(CombatUnit unit) {
        return unit == null || unit.getType() == null ? "UNKNOWN" : unit.getType().toUpperCase(Locale.ROOT);
    }

    private boolean hasSensor(FindContactReport report, String sensor) {
        return report != null && report.getSensorSources() != null && report.getSensorSources().contains(sensor);
    }

    private String anySensor(FindContactReport report) {
        if (report == null || report.getSensorSources() == null || report.getSensorSources().isEmpty()) {
            return "";
        }
        return report.getSensorSources().get(0);
    }

    private double estimateRcs(CombatUnit unit) {
        String type = normalizedType(unit);
        if ("TANK".equals(type)) return 15;
        if ("FIGHTER".equals(type)) return 6;
        if ("INFANTRY".equals(type)) return 0.8;
        return 4;
    }

    private String estimateIr(CombatUnit unit) {
        String type = normalizedType(unit);
        if ("TANK".equals(type) || "ARTILLERY".equals(type)) return "HIGH";
        if ("FIGHTER".equals(type) || "BOMBER".equals(type)) return "HIGH";
        if ("INFANTRY".equals(type)) return "LOW";
        return "MEDIUM";
    }

    private String estimateEm(CombatUnit unit) {
        String mission = unit == null || unit.getMission() == null ? "" : unit.getMission().toUpperCase(Locale.ROOT);
        if (mission.contains("雷达") || mission.contains("通信")) return "HIGH";
        if (mission.contains("隐蔽") || mission.contains("潜伏")) return "LOW";
        return "MEDIUM";
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private String pct(double v) {
        return (int) Math.round(v * 100) + "%";
    }
}
