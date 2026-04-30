package com.military.combat.service;

import com.military.combat.entity.*;
import com.military.combat.repository.InteractionEventRepository;
import com.military.combat.simulation.random.SimulationRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatEngineServiceTest {

    @Mock
    private CombatUnitService unitService;

    @Mock
    private CombatActivityService activityService;

    @Mock
    private CoordinationService coordinationService;

    @Mock
    private InteractionRuleService ruleService;

    @Mock
    private TerrainService terrainService;

    @Mock
    private ScenarioService scenarioService;

    @Mock
    private InteractionEventRepository interactionEventRepository;

    @Mock
    private CampaignService campaignService;

    @Mock
    private InteractionProcessService interactionProcessService;

    @Mock
    private CombatObjectiveDirectiveService objectiveDirectiveService;

    @Mock
    private SimulationRandom simulationRandom;

    @InjectMocks
    private CombatEngineService engineService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSimulateRound_NoActiveUnits() {
        when(unitService.getUnitsForBattleEngine()).thenReturn(new ArrayList<>());
        when(scenarioService.getCurrentRound()).thenReturn(0);

        List<BattleEvent> events = engineService.simulateRound();

        assertNotNull(events);
        assertTrue(events.isEmpty());
        verify(unitService, times(1)).getUnitsForBattleEngine();
    }

    @Test
    void testSimulateRound_OneActiveUnit() {
        List<CombatUnit> units = new ArrayList<>();
        CombatUnit unit = new CombatUnit();
        unit.setCombatPower(100);
        units.add(unit);

        when(unitService.getUnitsForBattleEngine()).thenReturn(units);
        when(scenarioService.getCurrentRound()).thenReturn(0);

        List<BattleEvent> events = engineService.simulateRound();

        assertNotNull(events);
        assertTrue(events.isEmpty());
        verify(unitService, times(1)).getUnitsForBattleEngine();
    }

    @Test
    void testResetScenario() {
        when(unitService.getAllUnits()).thenReturn(new ArrayList<>());
        doNothing().when(scenarioService).resetStats();

        assertDoesNotThrow(() -> engineService.resetScenario());
        verify(scenarioService, times(1)).resetStats();
        verify(unitService, times(1)).getAllUnits();
    }
}
