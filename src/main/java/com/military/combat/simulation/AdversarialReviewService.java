package com.military.combat.simulation;

import com.military.combat.entity.CombatUnit;
import com.military.combat.service.CombatUnitService;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对抗复盘服务：按红蓝双视角输出压制关系与改进建议。
 */
@Service
public class AdversarialReviewService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private FindService findService;
    @Autowired
    private TrackService trackService;
    @Autowired
    private EngageService engageService;
    @Autowired
    private OpposingEffectService opposingEffectService;
    @Autowired
    private CommandEffectService commandEffectService;
    @Autowired
    private SortieEffectService sortieEffectService;

    public AdversarialReview buildReview() {
        AdversarialReview out = new AdversarialReview();
        String sid = scenarioService.getActiveScenarioId();
        out.setScenarioId(sid);
        out.setRound(scenarioService.getCurrentRound());
        out.setGeneratedAt(System.currentTimeMillis());
        if (sid != null && !sid.isBlank()) {
            var sd = scenarioService.getScenarioDataById(sid);
            out.setScenarioName(sd != null ? sd.getName() : null);
        }

        FindSnapshot find = findService.getFindSnapshot();
        TrackSnapshot track = trackService.getTrackSnapshot();
        EngageSnapshot engage = engageService.getEngageSnapshot();
        var opp = opposingEffectService.currentSummary();
        var cmd = commandEffectService.currentSummary();
        var sortie = sortieEffectService.currentSummary();

        // 兜底：系统初始态（无想定/无单位）也应返回可展示复盘，而不是 500。
        if (find == null) find = new FindSnapshot();
        if (track == null) track = new TrackSnapshot();
        if (engage == null) engage = new EngageSnapshot();

        double red = 0.5;
        red += cmd.isrBoost() * 0.5 + cmd.strikeBoost() * 0.5 + cmd.ewBoost() * 0.3;
        red += sortie.isrBoost() * 0.4 + sortie.strikeBoost() * 0.4 + sortie.ewBoost() * 0.2;
        red += find.getDetectionProbability() * 0.15 + track.getTrackContinuityRate() * 0.15 + engage.getHitEffectivenessRate() * 0.2;

        double blue = 0.5;
        blue += opp.counterRecon() * 0.5 + opp.ewSuppression() * 0.5 + opp.samIntercept() * 0.5 + opp.decoy() * 0.3;
        blue += (1 - engage.getFireExecutionRate()) * 0.2 + track.getTrackLossRate() * 0.2;

        red = clamp01(red / 2.2);
        blue = clamp01(blue / 2.0);
        out.setRedCompositeScore(round3(red));
        out.setBlueCompositeScore(round3(blue));
        if (Math.abs(red - blue) < 0.05) out.setDominantSide("BALANCED");
        else out.setDominantSide(red > blue ? "RED" : "BLUE");

        if (find.getDetectionProbability() >= 0.9) out.getRedAdvantages().add("Find 探测质量高，先手情报优势明显。");
        if (track.getTrackContinuityRate() >= 0.85) out.getRedAdvantages().add("Track 连续性高，目标链稳定。");
        if (engage.getHitEffectivenessRate() >= 0.75) out.getRedAdvantages().add("Engage 命中效率高，火力兑现较好。");

        if (opp.counterRecon() > 0.25) out.getBlueAdvantages().add("蓝方反侦察强，对红方发现链形成压制。");
        if (opp.ewSuppression() > 0.25) out.getBlueAdvantages().add("蓝方电子压制显著，降低红方跟踪稳定性。");
        if (opp.samIntercept() > 0.25) out.getBlueAdvantages().add("蓝方防空拦截有效，削弱红方打击兑现率。");

        out.getKeyTurningPoints().add("命令链 ISR/EW/STRIKE 联动决定了红方链路上限。");
        out.getKeyTurningPoints().add("蓝方对抗动作强度是 Find->Track 质量的主要扰动源。");
        out.getKeyTurningPoints().add("交战兑现率与丢轨率共同决定 Assess 闭环评分。");

        out.getRecommendationsForRed().add("优先维持 ISR 与 EW 架次在空，削弱蓝方反侦察与干扰。");
        out.getRecommendationsForRed().add("提高 STRIKE 命令密度并压缩 Target->Engage 准备时间。");
        out.getRecommendationsForBlue().add("持续叠加 DECOY+EW_SUPPRESSION，破坏红方 Fix/Track链。");
        out.getRecommendationsForBlue().add("在红方打击窗口前提高 SAM_INTERCEPT 强度。");

        enrichByUnitBalance(out);
        return out;
    }

    private void enrichByUnitBalance(AdversarialReview out) {
        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine();
        long redAlive = units.stream().filter(u -> u != null && "RED".equals(u.getSide()) && u.getCombatPower() > 0).count();
        long blueAlive = units.stream().filter(u -> u != null && "BLUE".equals(u.getSide()) && u.getCombatPower() > 0).count();
        out.getKeyTurningPoints().add("兵力存活：RED " + redAlive + " / BLUE " + blueAlive + "。");
        if (redAlive < blueAlive) {
            out.getRecommendationsForRed().add("优先提升防空与机动掩护，降低战损速率。");
        } else if (blueAlive < redAlive) {
            out.getRecommendationsForBlue().add("分散部署并提升诱饵密度，延缓消耗。");
        }
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
