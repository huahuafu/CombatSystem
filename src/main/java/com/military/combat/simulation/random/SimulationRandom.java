package com.military.combat.simulation.random;

import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.SplittableRandom;

/**
 * 推演随机源（可复现）：由“当前会话 seed”派生到 module/round/entity 维度。
 *
 * 设计目标：
 * - 同一 seed + 同一输入状态下，输出稳定可复现
 * - 不依赖调用次数（避免“多一次 nextDouble() 就全漂移”），尽量按 key 派生
 */
@Service
public class SimulationRandom {

    private static final long DEFAULT_SEED = 20260427L;

    @Autowired
    private ScenarioService scenarioService;

    public long getSeed() {
        Long s = scenarioService.getSimulationSeed();
        return s == null ? DEFAULT_SEED : s;
    }

    public void setSeed(long seed) {
        scenarioService.setSimulationSeed(seed);
    }

    public SplittableRandom rng(String module, int round) {
        return rng(module, round, null);
    }

    public SplittableRandom rng(String module, int round, String entityKey) {
        long seed = deriveSeed(getSeed(), module, round, entityKey);
        return new SplittableRandom(seed);
    }

    private static long deriveSeed(long baseSeed, String module, int round, String entityKey) {
        long s = mix64(baseSeed);
        s ^= mix64(hash64(module));
        s ^= mix64(((long) round) * 0x9E3779B97F4A7C15L);
        if (entityKey != null && !entityKey.isEmpty()) {
            s ^= mix64(hash64(entityKey));
        }
        return mix64(s);
    }

    private static long hash64(String s) {
        if (s == null) {
            return 0L;
        }
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        long h = 1469598103934665603L; // FNV-1a 64 offset
        for (byte x : b) {
            h ^= (x & 0xff);
            h *= 1099511628211L;
        }
        return h;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}

