package com.military.combat.controller.v2;

import com.military.combat.battleline.BattlelineOverview;
import com.military.combat.battleline.BattlelineService;
import com.military.combat.entity.BattleEvent;
import com.military.combat.entity.CombatUnit;
import com.military.combat.service.CombatUnitService;
import com.military.combat.simulation.SimulationFacadeService;
import com.military.combat.simulation.ModelingReadiness;
import com.military.combat.simulation.ModelingReadinessService;
import com.military.combat.simulation.KillChainAssessment;
import com.military.combat.simulation.KillChainAssessmentService;
import com.military.combat.simulation.KillChainRunResult;
import com.military.combat.simulation.KillChainSimulationService;
import com.military.combat.simulation.FindSnapshot;
import com.military.combat.simulation.FindService;
import com.military.combat.simulation.FixSnapshot;
import com.military.combat.simulation.FixService;
import com.military.combat.simulation.TrackSnapshot;
import com.military.combat.simulation.TrackService;
import com.military.combat.simulation.TargetSnapshot;
import com.military.combat.simulation.TargetService;
import com.military.combat.simulation.EngageSnapshot;
import com.military.combat.simulation.EngageService;
import com.military.combat.simulation.AssessSnapshot;
import com.military.combat.simulation.AssessService;
import com.military.combat.simulation.SortieEffectService;
import com.military.combat.simulation.CommandEffectService;
import com.military.combat.simulation.OpposingEffectService;
import com.military.combat.simulation.AdversarialReview;
import com.military.combat.simulation.AdversarialReviewService;
import com.military.combat.simulation.SimulationState;
import com.military.combat.simulation.OperationalPhaseState;
import com.military.combat.simulation.OperationalPhaseService;
import com.military.combat.simulation.NavalOperationalMetricsService;
import com.military.combat.simulation.NavalOperationalMetricsSnapshot;
import com.military.combat.simulation.TacticalAssessment;
import com.military.combat.simulation.TacticalAssessmentService;
import com.military.combat.service.BatchSimulationService;
import com.military.combat.service.AiAutoModelingService;
import com.military.combat.simulation.batch.BatchSimulationRequest;
import com.military.combat.simulation.batch.BatchSimulationResponse;
import com.military.combat.simulation.hybrid.HybridStrategyOptimizeRequest;
import com.military.combat.simulation.hybrid.HybridStrategyOptimizeResponse;
import com.military.combat.simulation.hybrid.HybridStrategyRecommendRequest;
import com.military.combat.simulation.hybrid.HybridStrategyRecommendResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/combat/v2/simulation")
public class SimulationController {

    @Autowired
    private KillChainSimulationService killChainSimulationService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private SimulationFacadeService simulationFacadeService;

    @Autowired
    private BattlelineService battlelineService;

    @Autowired
    private ModelingReadinessService readinessService;

    @Autowired
    private TacticalAssessmentService tacticalAssessmentService;

    @Autowired
    private KillChainAssessmentService killChainAssessmentService;

    @Autowired
    private FindService findService;

    @Autowired
    private FixService fixService;

    @Autowired
    private TrackService trackService;

    @Autowired
    private TargetService targetService;

    @Autowired
    private EngageService engageService;

    @Autowired
    private AssessService assessService;

    @Autowired
    private SortieEffectService sortieEffectService;

    @Autowired
    private CommandEffectService commandEffectService;

    @Autowired
    private OpposingEffectService opposingEffectService;

    @Autowired
    private AdversarialReviewService adversarialReviewService;

    @Autowired
    private BatchSimulationService batchSimulationService;

    @Autowired
    private OperationalPhaseService operationalPhaseService;
    @Autowired
    private NavalOperationalMetricsService navalOperationalMetricsService;
    @Autowired
    private AiAutoModelingService aiAutoModelingService;

    @PostMapping("/start")
    public List<BattleEvent> startRound() {
        return killChainSimulationService.runOneRoundByKillChain().getBattleEvents();
    }

    @PostMapping("/start-kill-chain")
    public KillChainRunResult startRoundByKillChain() {
        return killChainSimulationService.runOneRoundByKillChain();
    }

    /**
     * 同想定批量重放若干次并打分排序（详见 docs/VISION_ROADMAP.md 阶段 A）。
     */
    @PostMapping("/batch-run")
    public BatchSimulationResponse batchRun(@RequestBody(required = false) BatchSimulationRequest body) {
        return batchSimulationService.run(body);
    }

    @PostMapping("/strategy/recommend")
    public HybridStrategyRecommendResponse recommend(@RequestBody(required = false) HybridStrategyRecommendRequest body) {
        if (body == null) {
            body = new HybridStrategyRecommendRequest();
        }
        return aiAutoModelingService.recommendTopStrategies(body);
    }

    @PostMapping("/strategy/optimize")
    public HybridStrategyOptimizeResponse optimize(@RequestBody(required = false) HybridStrategyOptimizeRequest body) {
        if (body == null) {
            body = new HybridStrategyOptimizeRequest();
        }
        return aiAutoModelingService.optimizeTopStrategies(body);
    }

    @GetMapping("/state")
    public SimulationState state() {
        return simulationFacadeService.getState();
    }

    @GetMapping("/operational-phase")
    public OperationalPhaseState operationalPhase() {
        return operationalPhaseService.current();
    }

    @GetMapping("/naval-metrics")
    public NavalOperationalMetricsSnapshot navalMetrics() {
        return navalOperationalMetricsService.current();
    }

    @GetMapping("/units")
    public List<CombatUnit> units() {
        return combatUnitService.getVisibleUnitsForList();
    }

    @GetMapping("/battleline")
    public BattlelineOverview battleline() {
        return battlelineService.getForActiveScenario();
    }

    @GetMapping("/readiness")
    public ModelingReadiness readiness() {
        return readinessService.checkForActiveScenario();
    }

    @GetMapping("/assessment")
    public TacticalAssessment assessment() {
        return tacticalAssessmentService.getForActiveScenario();
    }

    @GetMapping("/kill-chain")
    public KillChainAssessment killChain() {
        return killChainAssessmentService.getForActiveScenario();
    }

    @GetMapping("/find")
    public FindSnapshot findSnapshot() {
        return findService.getFindSnapshot();
    }

    @PostMapping("/find/scan")
    public FindSnapshot findScanNow() {
        findService.runFindScanForCurrentRound();
        return findService.getFindSnapshot();
    }

    @GetMapping("/fix")
    public FixSnapshot fixSnapshot() {
        return fixService.getFixSnapshot();
    }

    @PostMapping("/fix/fuse")
    public FixSnapshot fixFuseNow() {
        return fixService.runFusionForCurrentRound();
    }

    @GetMapping("/track")
    public TrackSnapshot trackSnapshot() {
        return trackService.getTrackSnapshot();
    }

    @PostMapping("/track/run")
    public TrackSnapshot trackRunNow() {
        return trackService.runTrackForCurrentRound();
    }

    @GetMapping("/target")
    public TargetSnapshot targetSnapshot() {
        return targetService.getTargetSnapshot();
    }

    @PostMapping("/target/plan")
    public TargetSnapshot targetPlanNow() {
        return targetService.runTargetingForCurrentRound();
    }

    @GetMapping("/engage")
    public EngageSnapshot engageSnapshot() {
        return engageService.getEngageSnapshot();
    }

    @PostMapping("/engage/run")
    public EngageSnapshot engageRunNow() {
        return engageService.runEngageForCurrentRound();
    }

    @GetMapping("/assess")
    public AssessSnapshot assessSnapshot() {
        return assessService.getAssessSnapshot();
    }

    @PostMapping("/assess/run")
    public AssessSnapshot assessRunNow() {
        return assessService.runAssessNow();
    }

    @GetMapping("/sortie/effect")
    public SortieEffectService.SortieEffectSummary sortieEffectSummary() {
        return sortieEffectService.currentSummary();
    }

    @GetMapping("/command/effect")
    public CommandEffectService.CommandEffectSummary commandEffectSummary() {
        return commandEffectService.currentSummary();
    }

    @GetMapping("/opposing/effect")
    public OpposingEffectService.OpposingEffectSummary opposingEffectSummary() {
        return opposingEffectService.currentSummary();
    }

    @GetMapping("/adversarial-review")
    public AdversarialReview adversarialReview() {
        return adversarialReviewService.buildReview();
    }
}

