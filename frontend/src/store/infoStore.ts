import { create } from "zustand";
import type { ForceCamp } from "../rebuild/force/forceTypes";
import type {
  BattlePerspective,
  EnvironmentState,
  InfoReplaySlice,
  RoundInfoDigest,
  UnitInfoCombatState
} from "../rebuild/info/infoTypes";

const STORAGE_PREFIX = "combat-info-replay";

/** 用于战争迷雾「离开探测后延迟隐去」：上一帧被探测到的敌方 id */
let prevDetectedEnemyIds = new Set<string>();

function defaultUnitState(): UnitInfoCombatState {
  return { radarEmitting: true, radioSilent: false, ewIntensity: 0 };
}

export interface InfoStoreState {
  perspective: BattlePerspective;
  /** 指挥视角下是否启用战争迷雾（上帝视角忽略） */
  fogOfWarEnabled: boolean;
  environment: EnvironmentState;
  /** 按单位 id 存储信息作战开关 */
  unitInfoCombat: Record<string, UnitInfoCombatState>;
  /** 敌方单位幽灵显示截止时间戳 ms（离开探测后短暂保留） */
  ghostEnemyUntil: Record<string, number>;
  /** 最近一次整域计算摘要（部署/仿真共用） */
  lastDigest: RoundInfoDigest | null;
  /** 仿真逐回合归档（供复盘页） */
  roundArchive: RoundInfoDigest[];
  /** 当前绑定的想定 id（用于 sessionStorage 分桶） */
  boundScenarioId: string;

  setPerspective: (p: BattlePerspective) => void;
  setFogOfWarEnabled: (v: boolean) => void;
  setEnvironment: (partial: Partial<EnvironmentState>) => void;
  setUnitInfoCombat: (unitId: string, partial: Partial<UnitInfoCombatState>) => void;
  ensureUnitsHaveDefaults: (unitIds: string[]) => void;
  /** 根据当前探测到的敌方 id 更新「延迟隐去」可见截止时间 */
  syncGhostContacts: (detectedEnemyIds: readonly string[], fadeMs?: number) => void;
  setLastDigest: (d: RoundInfoDigest | null) => void;
  appendRoundArchive: (d: RoundInfoDigest) => void;
  clearRoundArchive: () => void;
  setBoundScenarioId: (id: string) => void;
  persistArchiveToSession: () => void;
  loadArchiveFromSession: (scenarioId: string) => void;
  getReplaySlices: () => InfoReplaySlice[];
  resetInfoModule: () => void;
}

const defaultEnv: EnvironmentState = { seaState: 3, weatherAttenuation: 0.92 };

export const useInfoStore = create<InfoStoreState>((set, get) => ({
  perspective: "COMMAND",
  fogOfWarEnabled: true,
  environment: { ...defaultEnv },
  unitInfoCombat: {},
  ghostEnemyUntil: {},
  lastDigest: null,
  roundArchive: [],
  boundScenarioId: "",

  setPerspective: (p) => set({ perspective: p }),
  setFogOfWarEnabled: (v) => set({ fogOfWarEnabled: v }),
  setEnvironment: (partial) =>
    set((s) => ({
      environment: {
        seaState: partial.seaState ?? s.environment.seaState,
        weatherAttenuation: partial.weatherAttenuation ?? s.environment.weatherAttenuation
      }
    })),

  setUnitInfoCombat: (unitId, partial) =>
    set((s) => {
      const cur = s.unitInfoCombat[unitId] ?? defaultUnitState();
      return {
        unitInfoCombat: {
          ...s.unitInfoCombat,
          [unitId]: {
            radarEmitting: partial.radarEmitting ?? cur.radarEmitting,
            radioSilent: partial.radioSilent ?? cur.radioSilent,
            ewIntensity: partial.ewIntensity !== undefined ? partial.ewIntensity : cur.ewIntensity
          }
        }
      };
    }),

  ensureUnitsHaveDefaults: (unitIds) =>
    set((s) => {
      const next = { ...s.unitInfoCombat };
      let changed = false;
      for (const id of unitIds) {
        if (!next[id]) {
          next[id] = defaultUnitState();
          changed = true;
        }
      }
      return changed ? { unitInfoCombat: next } : {};
    }),

  syncGhostContacts: (detectedEnemyIds, fadeMs = 14000) => {
    const now = Date.now();
    const det = new Set(detectedEnemyIds);
    set((s) => {
      const nextGhost: Record<string, number> = { ...s.ghostEnemyUntil };
      for (const id of det) {
        nextGhost[id] = now + 1e12;
      }
      for (const id of prevDetectedEnemyIds) {
        if (!det.has(id)) {
          nextGhost[id] = now + fadeMs;
        }
      }
      prevDetectedEnemyIds = det;
      for (const id of Object.keys(nextGhost)) {
        if (nextGhost[id] <= now) {
          delete nextGhost[id];
        }
      }
      return { ghostEnemyUntil: nextGhost };
    });
  },

  setLastDigest: (d) => set({ lastDigest: d }),

  appendRoundArchive: (d) =>
    set((s) => {
      const prev = s.roundArchive;
      const last = prev[prev.length - 1];
      let next: RoundInfoDigest[];
      if (last && last.round === d.round) {
        next = [...prev.slice(0, -1), d];
      } else {
        next = [...prev, d].slice(-80);
      }
      return { roundArchive: next, lastDigest: d };
    }),

  clearRoundArchive: () => set({ roundArchive: [] }),

  setBoundScenarioId: (id) => set({ boundScenarioId: id }),

  persistArchiveToSession: () => {
    const { boundScenarioId, roundArchive } = get();
    if (!boundScenarioId || typeof sessionStorage === "undefined") return;
    try {
      sessionStorage.setItem(`${STORAGE_PREFIX}:${boundScenarioId}`, JSON.stringify(roundArchive));
    } catch {
      /* ignore quota */
    }
  },

  loadArchiveFromSession: (scenarioId) => {
    if (typeof sessionStorage === "undefined") return;
    try {
      const raw = sessionStorage.getItem(`${STORAGE_PREFIX}:${scenarioId}`);
      if (!raw) {
        set({ roundArchive: [], boundScenarioId: scenarioId });
        return;
      }
      const parsed = JSON.parse(raw) as RoundInfoDigest[];
      set({ roundArchive: Array.isArray(parsed) ? parsed : [], boundScenarioId: scenarioId });
    } catch {
      set({ roundArchive: [], boundScenarioId: scenarioId });
    }
  },

  getReplaySlices: () => {
    return get().roundArchive.map((r) => ({
      round: r.round,
      detectionCount: r.detections.length,
      jamEventsNote: `探测效率×${r.ewSummary.avgDetectionEfficiency.toFixed(2)} · 干扰压制圈内单位 ${r.ewSummary.unitsInEnemyJam}`,
      datalinkUpRatio: r.datalinkIntegrity,
      infoAdvantage: r.infoAdvantage
    }));
  },

  resetInfoModule: () => {
    prevDetectedEnemyIds = new Set();
    set({
      perspective: "COMMAND",
      fogOfWarEnabled: true,
      environment: { ...defaultEnv },
      unitInfoCombat: {},
      ghostEnemyUntil: {},
      lastDigest: null,
      roundArchive: [],
      boundScenarioId: ""
    });
  }
}));
