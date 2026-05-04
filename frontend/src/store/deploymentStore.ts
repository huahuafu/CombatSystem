import { create } from "zustand";
import type { DeployedUnit, ForceCamp } from "../rebuild/force/forceTypes";
import {
  buildDeployedUnit,
  deployedCountForTemplate,
  getTemplateByTypeAndCamp,
  remainForTemplate
} from "../rebuild/force/forceCatalog";

function cloneUnits(u: DeployedUnit[]): DeployedUnit[] {
  return u.map((x) => ({
    ...x,
    coreParams: x.coreParams ? { ...x.coreParams } : undefined
  }));
}

export interface DeploymentState {
  deployedUnits: DeployedUnit[];
  /** 快捷部署 / 双击地图使用的兵种 type */
  lastSelectedUnitType: string | null;
  past: DeployedUnit[][];
  future: DeployedUnit[][];

  /** 将当前兵力表压栈后替换为 next（路线绑定任务等批量编辑可复用） */
  commitWithHistory: (next: DeployedUnit[]) => void;
  deployAt: (type: string, camp: ForceCamp, lon: number, lat: number, mission?: string) => boolean;
  removeUnitIds: (ids: string[], actingCamp: ForceCamp) => void;
  moveUnit: (id: string, lon: number, lat: number, actingCamp: ForceCamp) => void;
  clearAll: () => void;
  undo: () => void;
  redo: () => void;
  canUndo: () => boolean;
  canRedo: () => boolean;
  setLastSelectedType: (t: string | null) => void;
  /** 外部同步（读想定等）不进入撤销栈 */
  replaceAllNoHistory: (units: DeployedUnit[]) => void;
  resetHistory: () => void;
}

export const useDeploymentStore = create<DeploymentState>((set, get) => ({
  deployedUnits: [],
  lastSelectedUnitType: null,
  past: [],
  future: [],

  commitWithHistory: (next) => {
    const { deployedUnits, past } = get();
    set({
      past: [...past, cloneUnits(deployedUnits)],
      future: [],
      deployedUnits: cloneUnits(next)
    });
  },

  deployAt: (type, camp, lon, lat, mission) => {
    const tpl = getTemplateByTypeAndCamp(type, camp);
    if (!tpl) return false;
    const { deployedUnits } = get();
    if (remainForTemplate(deployedUnits, tpl) <= 0) return false;
    const nextIndex = deployedCountForTemplate(deployedUnits, tpl) + 1;
    const unit = buildDeployedUnit(tpl, lon, lat, nextIndex);
    if (mission) unit.mission = mission;
    get().commitWithHistory([...deployedUnits, unit]);
    set({ lastSelectedUnitType: type });
    return true;
  },

  removeUnitIds: (ids, actingCamp) => {
    const { deployedUnits } = get();
    const idSet = new Set(ids);
    const removable = deployedUnits.filter((u) => idSet.has(u.id) && u.side === actingCamp);
    if (!removable.length) return;
    get().commitWithHistory(deployedUnits.filter((u) => !removable.some((r) => r.id === u.id)));
  },

  moveUnit: (id, lon, lat, actingCamp) => {
    const { deployedUnits } = get();
    const u = deployedUnits.find((x) => x.id === id);
    if (!u || u.side !== actingCamp) return;
    get().commitWithHistory(
      deployedUnits.map((x) => (x.id === id ? { ...x, longitude: lon, latitude: lat } : x))
    );
  },

  clearAll: () => {
    const { deployedUnits } = get();
    if (!deployedUnits.length) return;
    get().commitWithHistory([]);
  },

  undo: () => {
    const { past, future, deployedUnits } = get();
    if (!past.length) return;
    const prev = past[past.length - 1];
    set({
      deployedUnits: cloneUnits(prev),
      past: past.slice(0, -1),
      future: [cloneUnits(deployedUnits), ...future]
    });
  },

  redo: () => {
    const { past, future, deployedUnits } = get();
    if (!future.length) return;
    const nxt = future[0];
    set({
      deployedUnits: cloneUnits(nxt),
      future: future.slice(1),
      past: [...past, cloneUnits(deployedUnits)]
    });
  },

  canUndo: () => get().past.length > 0,
  canRedo: () => get().future.length > 0,

  setLastSelectedType: (t) => set({ lastSelectedUnitType: t }),

  replaceAllNoHistory: (units) => {
    set({ deployedUnits: cloneUnits(units), past: [], future: [] });
  },

  resetHistory: () => set({ past: [], future: [] })
}));
