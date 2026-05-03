import { create } from "zustand";

export type CombatSide = "RED" | "BLUE";
export type CommanderRole = "ATTACK" | "DEFEND";

interface SimulationGlobalState {
  activeScenarioId: string;
  commanderSide: CombatSide;
  commanderRole: CommanderRole;
  setActiveScenarioId: (id: string) => void;
  setCommanderSide: (side: CombatSide) => void;
  setCommanderRole: (role: CommanderRole) => void;
}

export const useSimulationStore = create<SimulationGlobalState>((set) => ({
  activeScenarioId: "",
  commanderSide: "RED",
  commanderRole: "ATTACK",
  setActiveScenarioId: (id) => set({ activeScenarioId: id }),
  setCommanderSide: (side) => set({ commanderSide: side }),
  setCommanderRole: (role) => set({ commanderRole: role })
}));
