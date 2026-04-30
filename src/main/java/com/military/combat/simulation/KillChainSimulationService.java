package com.military.combat.simulation;

import com.military.combat.entity.BattleEvent;
import com.military.combat.service.CombatEngineService;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按 Find/Fix/Track/Target/Engage/Assess 六阶段串行推进一轮仿真。
 */
@Service
public class KillChainSimulationService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatEngineService combatEngineService;

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
    private KillChainAssessmentService killChainAssessmentService;

    public KillChainRunResult runOneRoundByKillChain() {
        KillChainRunResult out = new KillChainRunResult();
        out.setScenarioId(scenarioService.getActiveScenarioId());
        out.setRound(scenarioService.getCurrentRound());

        out.getExecutedPhases().add(KillChainPhase.FIND.name());
        out.setFindContacts(findService.runFindScanForCurrentRound().size());

        out.getExecutedPhases().add(KillChainPhase.FIX.name());
        FixSnapshot fix = fixService.runFusionForCurrentRound();
        out.setFixTargets(Math.max(0, fix.getFixedTargetCount()));

        out.getExecutedPhases().add(KillChainPhase.TRACK.name());
        TrackSnapshot track = trackService.runTrackForCurrentRound();
        out.setTrackTargets(Math.max(0, track.getTrackedTargetCount()));

        out.getExecutedPhases().add(KillChainPhase.TARGET.name());
        TargetSnapshot target = targetService.runTargetingForCurrentRound();
        out.setTargetSolutions(Math.max(0, target.getFireSolutionCount()));

        out.getExecutedPhases().add(KillChainPhase.ENGAGE.name());
        List<BattleEvent> events = combatEngineService.simulateRound();
        out.setBattleEvents(events);
        out.setEngageEvents(events == null ? 0 : events.size());
        engageService.runEngageForCurrentRound();

        out.getExecutedPhases().add(KillChainPhase.ASSESS.name());
        AssessSnapshot assess = assessService.runAssessNow();
        out.setAssessReadiness(assess.getReadinessLevel());
        out.setLoopClosureRate(assess.getLoopClosureRate());

        out.setAssessment(killChainAssessmentService.getForActiveScenario());
        return out;
    }
}
