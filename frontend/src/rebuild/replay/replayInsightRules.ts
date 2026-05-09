import type { CombatSide } from "../../store/simulationStore";
import type { RoundInfoDigest } from "../info/infoTypes";

export type InsightLevel = "info" | "warning" | "success";

export interface ReplayInsight {
  level: InsightLevel;
  text: string;
}

function avgInfoAdv(rows: RoundInfoDigest[], takeLast: number): number {
  const slice = rows.slice(-takeLast);
  if (!slice.length) return 0;
  return slice.reduce((s, r) => s + r.infoAdvantage, 0) / slice.length;
}

function lastOwnShare(r: RoundInfoDigest, side: CombatSide): number {
  const b = r.battle;
  if (!b) return 0.5;
  const sum = b.redCombatPower + b.blueCombatPower;
  if (sum <= 0) return 0.5;
  return side === "RED" ? b.redCombatPower / sum : b.blueCombatPower / sum;
}

/**
 * 非 LLM、可预测的复盘结论，供阶段 A 使用；后续可被 AI 简报引用或覆盖。
 */
export function buildReplayInsights(archive: RoundInfoDigest[], commanderSide: CombatSide): ReplayInsight[] {
  const out: ReplayInsight[] = [];
  if (!archive.length) {
    out.push({
      level: "info",
      text: "暂无回合归档：请在「仿真运行」页刷新或推进杀伤链以写入会话摘要。"
    });
    return out;
  }

  const last = archive[archive.length - 1];
  const rs = last.roundStatSnapshot;
  if (!rs || rs.statRound < 0) {
    out.push({
      level: "info",
      text: "后端回合统计尚未与本摘要对齐：通常需在完成含 ASSESS 的整回合后刷新，或检查会话是否与后端连通。"
    });
  }

  const ownLast = lastOwnShare(last, commanderSide);
  const iaLast = last.infoAdvantage;
  if (iaLast < 0.38 && ownLast < 0.42) {
    out.push({
      level: "warning",
      text: "最近摘要：信息优势与己方兵力占比均偏低，宜优先恢复探测/数据链或收缩接敌距离。"
    });
  } else if (iaLast >= 0.55 && ownLast < 0.45) {
    out.push({
      level: "warning",
      text: "信息态势尚可但己方战力占比偏低：可能存在火力交换不利或目标优先级不当，建议对照交互事件高峰回合。"
    });
  }

  if (archive.length >= 3) {
    const early = avgInfoAdv(archive.slice(0, 2), 2);
    const late = avgInfoAdv(archive, 2);
    if (late > early + 0.08 && lastOwnShare(last, commanderSide) < lastOwnShare(archive[0], commanderSide) - 0.06) {
      out.push({
        level: "warning",
        text: "信息优势整体走强，但己方战力占比走弱：信息未有效转化为交战成果，可检查 TARGET/ENGAGE 节奏或敌方规避。"
      });
    }
  }

  const intrMax = archive.reduce((m, r) => Math.max(m, r.roundStatSnapshot?.interactionEventCount ?? 0), 0);
  if (intrMax >= 6 && archive.length >= 2) {
    out.push({
      level: "info",
      text: `交互事件较为密集（单回合最多约 ${intrMax} 条）：建议结合图表中「交互事件(归一)」柱与己方战力曲线对照战损交换。`
    });
  }

  if (rs && rs.statRound >= 0 && rs.redTotalHp + rs.blueTotalHp > 0) {
    const ratio = commanderSide === "RED" ? rs.redTotalHp / (rs.redTotalHp + rs.blueTotalHp) : rs.blueTotalHp / (rs.redTotalHp + rs.blueTotalHp);
    if (ratio >= 0.52) {
      out.push({
        level: "success",
        text: "后端血量汇总显示己方仍占一定血量优势（按 RoundStat），可结合目标完成情况决定是否巩固既有战果。"
      });
    }
  }

  if (!out.some((x) => x.level === "success") && iaLast >= 0.5 && ownLast >= 0.48) {
    out.push({
      level: "success",
      text: "最近回合信息与兵力占比相对均衡：可持续当前杀伤链节奏并观察对方破绽。"
    });
  }

  if (out.length === 0 || (out.length === 1 && out[0].level === "info")) {
    out.push({
      level: "info",
      text: "暂无明显异常模式：继续累积回合或导出复盘包做离线分析。"
    });
  }

  return out.slice(0, 6);
}
