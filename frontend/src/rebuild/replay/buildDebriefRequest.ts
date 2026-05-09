import type { CombatSide } from "../../store/simulationStore";
import type { RoundInfoDigest } from "../info/infoTypes";

/** POST /combat/v2/simulation/debrief 请求体（与 Java DebriefRequest 字段对齐） */
export function buildDebriefRequestPayload(
  archive: RoundInfoDigest[],
  commanderSide: CombatSide,
  winner?: string | null,
  winReason?: string | null
) {
  const rounds = archive.map((r) => {
    const b = r.battle;
    const rs = r.roundStatSnapshot;
    const sum = (b?.redCombatPower ?? 0) + (b?.blueCombatPower ?? 0);
    let ownCombatShare = 0.5;
    if (b && sum > 0) {
      ownCombatShare =
        commanderSide === "RED" ? b.redCombatPower / sum : b.blueCombatPower / sum;
    }
    return {
      round: r.round,
      infoAdvantage: r.infoAdvantage,
      datalinkIntegrity: r.datalinkIntegrity,
      ownCombatShare,
      interactionCount: rs?.interactionEventCount ?? 0,
      redHp: rs?.redTotalHp ?? 0,
      blueHp: rs?.blueTotalHp ?? 0,
      statRound: rs?.statRound ?? -1
    };
  });

  return {
    commanderSide,
    winner: winner && String(winner).trim() ? String(winner).trim() : undefined,
    winReason: winReason && String(winReason).trim() ? String(winReason).trim() : undefined,
    rounds
  };
}
