import type { RoundInfoDigest, RoundStatSnapshot } from "./infoTypes";

/** 与 GET /combat/stats 返回体一致 */
export interface RoundStatRow {
  round: number;
  redTotalHp: number;
  blueTotalHp: number;
  redCount: number;
  blueCount: number;
}

/** 与 GET /combat/interaction-event/list 单条一致（仅回合用于聚合） */
export interface InteractionEventRow {
  round?: number;
}

export function countInteractionsByRound(events: InteractionEventRow[] | null | undefined): Map<number, number> {
  const m = new Map<number, number>();
  for (const e of events || []) {
    if (e == null || typeof e.round !== "number" || Number.isNaN(e.round)) continue;
    m.set(e.round, (m.get(e.round) ?? 0) + 1);
  }
  return m;
}

/**
 * RoundStat 在 recordStat 时使用「刚结束的推演回合」编号；
 * 仿真状态 round 在完成一整回合后会 +1，故优先匹配 digest.round - 1。
 * 交互事件在引擎里多为 currentRound + 1，与刷新时的 session round（digest.round）一致。
 */
export function pickRoundStatForDigest(stats: RoundStatRow[], digestRound: number): RoundStatRow | null {
  if (!stats?.length) return null;
  const by = new Map(stats.map((s) => [s.round, s]));
  if (digestRound > 0 && by.has(digestRound - 1)) {
    return by.get(digestRound - 1)!;
  }
  if (by.has(digestRound)) {
    return by.get(digestRound)!;
  }
  const sorted = [...stats].sort((a, b) => b.round - a.round);
  const le = sorted.filter((s) => s.round <= digestRound);
  return le[0] ?? sorted[0] ?? null;
}

export function buildRoundStatSnapshot(
  digestRound: number,
  stats: RoundStatRow[],
  interactionByRound: Map<number, number>
): RoundStatSnapshot {
  const stat = pickRoundStatForDigest(stats, digestRound);
  const intr = interactionByRound.get(digestRound) ?? 0;
  if (stat) {
    return {
      statRound: stat.round,
      redTotalHp: stat.redTotalHp,
      blueTotalHp: stat.blueTotalHp,
      redCount: stat.redCount,
      blueCount: stat.blueCount,
      interactionEventCount: intr
    };
  }
  return {
    statRound: -1,
    redTotalHp: 0,
    blueTotalHp: 0,
    redCount: 0,
    blueCount: 0,
    interactionEventCount: intr
  };
}

export function attachServerSnapshotToDigest(
  digest: RoundInfoDigest,
  stats: RoundStatRow[],
  interactionByRound: Map<number, number>
): RoundInfoDigest {
  return {
    ...digest,
    roundStatSnapshot: buildRoundStatSnapshot(digest.round, stats, interactionByRound)
  };
}
