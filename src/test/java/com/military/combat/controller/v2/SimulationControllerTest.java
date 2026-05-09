package com.military.combat.controller.v2;

import com.military.combat.battleline.BattlelineOverview;
import com.military.combat.battleline.BattlelineService;
import com.military.combat.entity.BattleEvent;
import com.military.combat.entity.CombatUnit;
import com.military.combat.service.CombatUnitService;
import com.military.combat.simulation.KillChainRunResult;
import com.military.combat.simulation.KillChainSimulationService;
import com.military.combat.simulation.SimulationFacadeService;
import com.military.combat.simulation.SimulationMode;
import com.military.combat.simulation.SimulationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SimulationControllerTest {

    private MockMvc mockMvc;

    private KillChainSimulationService killChainSimulationService;

    private CombatUnitService combatUnitService;

    private SimulationFacadeService simulationFacadeService;

    private BattlelineService battlelineService;

    @BeforeEach
    void setUp() {
        killChainSimulationService = mock(KillChainSimulationService.class);
        combatUnitService = mock(CombatUnitService.class);
        simulationFacadeService = mock(SimulationFacadeService.class);
        battlelineService = mock(BattlelineService.class);
        SimulationController controller = new SimulationController();
        inject(controller, "killChainSimulationService", killChainSimulationService);
        inject(controller, "combatUnitService", combatUnitService);
        inject(controller, "simulationFacadeService", simulationFacadeService);
        inject(controller, "battlelineService", battlelineService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnSimulationState() throws Exception {
        SimulationState state = new SimulationState(
                3,
                "sid-1",
                SimulationMode.TASK_DRIVEN,
                true,
                "desc",
                "cid-1",
                "campaign",
                "mainline",
                null,
                null,
                null,
                null,
                null,
                0);
        when(simulationFacadeService.getState()).thenReturn(state);

        mockMvc.perform(get("/combat/v2/simulation/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(3))
                .andExpect(jsonPath("$.activeScenarioId").value("sid-1"))
                .andExpect(jsonPath("$.mainLine").value("mainline"));
    }

    @Test
    void shouldStartRoundAndReturnEvents() throws Exception {
        BattleEvent event = new BattleEvent();
        event.setAction("ATTACK");
        List<BattleEvent> events = new ArrayList<>();
        events.add(event);
        KillChainRunResult runResult = new KillChainRunResult();
        runResult.setBattleEvents(events);
        when(killChainSimulationService.runOneRoundByKillChain()).thenReturn(runResult);

        mockMvc.perform(post("/combat/v2/simulation/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("ATTACK"));
    }

    @Test
    void shouldReturnUnitsAndBattleline() throws Exception {
        CombatUnit unit = new CombatUnit();
        unit.setId("u1");
        unit.setName("unit1");
        when(combatUnitService.getVisibleUnitsForList()).thenReturn(List.of(unit));

        BattlelineOverview overview = new BattlelineOverview();
        overview.setMainLine("line");
        when(battlelineService.getForActiveScenario()).thenReturn(overview);

        mockMvc.perform(get("/combat/v2/simulation/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("u1"));

        mockMvc.perform(get("/combat/v2/simulation/battleline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainLine").value("line"));
    }

    private void inject(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

