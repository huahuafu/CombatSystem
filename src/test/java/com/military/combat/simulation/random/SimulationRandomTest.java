package com.military.combat.simulation.random;

import com.military.combat.service.ScenarioService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class SimulationRandomTest {

    @Test
    void sameSeedSameKey_isDeterministic() throws Exception {
        ScenarioService scenarioService = new ScenarioService();
        scenarioService.setSimulationSeed(123456789L);

        SimulationRandom sr = new SimulationRandom();
        inject(sr, "scenarioService", scenarioService);

        RandomGenerator a = sr.rng("FIND_DETECT", 7, "unit-001");
        RandomGenerator b = sr.rng("FIND_DETECT", 7, "unit-001");

        assertEquals(a.nextInt(1_000_000), b.nextInt(1_000_000));
        assertEquals(a.nextDouble(), b.nextDouble());
    }

    @Test
    void differentModuleOrRound_changesStream() throws Exception {
        ScenarioService scenarioService = new ScenarioService();
        scenarioService.setSimulationSeed(42L);

        SimulationRandom sr = new SimulationRandom();
        inject(sr, "scenarioService", scenarioService);

        double v1 = sr.rng("FIND_DETECT", 0, "u1").nextDouble();
        double v2 = sr.rng("FIND_CONTACT", 0, "u1").nextDouble();
        double v3 = sr.rng("FIND_DETECT", 1, "u1").nextDouble();

        assertNotEquals(v1, v2);
        assertNotEquals(v1, v3);
    }

    /**
     * 回归基线：如果 seed 派生算法被改动，会在这里立即失败。
     * 注意：这是“可复现性 contract”，不是业务算法正确性的证明。
     */
    @Test
    void regression_baselineNumbers() throws Exception {
        ScenarioService scenarioService = new ScenarioService();
        scenarioService.setSimulationSeed(20260427L);

        SimulationRandom sr = new SimulationRandom();
        inject(sr, "scenarioService", scenarioService);

        // 这些期望值一旦变化，意味着 seed 派生/混淆逻辑改变，可能导致历史回放不可复现
        assertEquals(0.070_772_346_903_110_46, sr.rng("FIND_DETECT", 0, "u1").nextDouble(), 1e-15);
        assertEquals(8, sr.rng("ENGINE_ATTACK", 3, "a->b").nextInt(10));
        assertEquals(0.773_776_999_943_795_6, sr.rng("INTERACTION_ATTACK", 5, "p1").nextDouble(), 1e-15);
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}

