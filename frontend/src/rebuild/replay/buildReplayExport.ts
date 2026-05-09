import { httpJson } from "../../lib/api";
import type { CombatSide } from "../../store/simulationStore";
import type { RoundInfoDigest } from "../info/infoTypes";

export const REPLAY_EXPORT_VERSION = 1;

export interface ReplayExportPayload {
  exportVersion: number;
  exportedAt: string;
  scenarioId: string;
  commanderSide: CombatSide;
  simulationState: unknown;
  roundStats: unknown;
  interactionEvents: unknown;
  roundArchive: RoundInfoDigest[];
}

/**
 * 聚合本会话复盘摘要与后端事实，便于离线对齐或归档。
 * 依赖当前浏览器已登录后端；接口失败时对应字段为 null 或 []。
 */
export async function buildReplayExportPayload(params: {
  scenarioId: string;
  commanderSide: CombatSide;
  roundArchive: RoundInfoDigest[];
}): Promise<ReplayExportPayload> {
  const [simulationState, roundStats, interactionEvents] = await Promise.all([
    httpJson("/combat/v2/simulation/state").catch(() => null),
    httpJson("/combat/stats").catch(() => []),
    httpJson("/combat/interaction-event/list").catch(() => [])
  ]);

  return {
    exportVersion: REPLAY_EXPORT_VERSION,
    exportedAt: new Date().toISOString(),
    scenarioId: params.scenarioId,
    commanderSide: params.commanderSide,
    simulationState,
    roundStats,
    interactionEvents,
    roundArchive: params.roundArchive
  };
}
