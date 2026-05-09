import type { ForceCamp } from "../force/forceTypes";

/** 战场视角：上帝视角可见全部；指挥视角应用战争迷雾 */
export type BattlePerspective = "GOD" | "COMMAND";

/** 电子战强度档位（己方发射端） */
export type EwIntensityLevel = 0 | 1 | 2 | 3;

/** 单平台信息作战开关（与想定 JSON 兼容的扁平字段） */
export interface UnitInfoCombatState {
  radarEmitting: boolean;
  radioSilent: boolean;
  ewIntensity: EwIntensityLevel;
}

export interface EnvironmentState {
  /** 海况 1–5，越高对声呐/雷达越不利 */
  seaState: number;
  /** 气象衰减系数 0.55–1，乘到探测距离 */
  weatherAttenuation: number;
}

/** 传感器类型（战场感知体系） */
export type SensorModality = "RADAR" | "SONAR" | "EO_IR" | "SATELLITE";

export interface UnitSensorModel {
  modality: SensorModality;
  /** 标称最大探测距离（海里） */
  maxRangeNm: number;
  /** 扇区半角（度），180 表示全向 */
  sectorHalfAngleDeg: number;
  /** 对隐身目标的距离折算系数（越大越难被发现） */
  stealthPenaltyFactor: number;
}

/** 电子干扰压制圈（示意：中心 + 半径） */
export interface JammingZone {
  id: string;
  side: ForceCamp;
  centerLon: number;
  centerLat: number;
  /** 压制半径（海里） */
  radiusNm: number;
  /** 0–1，对进入圈内平台的探测/通信效率乘子 */
  strength: number;
  /** 干扰类型标签 */
  mode: "COMM" | "RADAR" | "BROAD";
}

/** 数据链链路 */
export interface DatalinkEdge {
  fromUnitId: string;
  toUnitId: string;
  /** 0–1 链路质量 */
  quality: number;
  /** 是否被压制导致降质 */
  degradedByJamming: boolean;
}

export interface DetectionHit {
  detectorId: string;
  targetId: string;
  targetSide: ForceCamp;
  rangeNm: number;
  modality: SensorModality;
}

export interface EwCasualtySummary {
  /** 被敌方干扰削弱的己方探测器数量 */
  detectorsDegraded: number;
  /** 进入敌干扰圈的己方单位数 */
  unitsInEnemyJam: number;
  /** 平均探测效率乘子 0–1 */
  avgDetectionEfficiency: number;
  /** 平均武器命中效率乘子（受雷达致盲影响）0–1 */
  avgWeaponAccuracyFactor: number;
}

export interface KillChainInfoFactors {
  find: number;
  fix: number;
  track: number;
  target: number;
  engage: number;
  assess: number;
}

/** 单回合战场战力快照（传统战报维度，与探测/链路等无关） */
export interface BattleRoundSummary {
  /** 红方存活单位战力合计（仅统计 combatPower 大于 0） */
  redCombatPower: number;
  blueCombatPower: number;
  redAlive: number;
  blueAlive: number;
  /** 红方战力占双方合计之比 0–1，便于与信息曲线同轴对照 */
  redShare01: number;
}

export interface RoundInfoDigest {
  round: number;
  timestamp: number;
  detections: DetectionHit[];
  ewSummary: EwCasualtySummary;
  /** 编队平均数据链通畅度 0–1 */
  datalinkIntegrity: number;
  /** 综合信息优势 0–1（己方相对敌方） */
  infoAdvantage: number;
  jammingZones: JammingZone[];
  datalinkEdges: DatalinkEdge[];
  /** 兵力战力汇总；旧会话存档可能缺省，复盘时需兜底 */
  battle?: BattleRoundSummary;
  /** 后端回合统计与交互事件计数；旧存档可缺省 */
  roundStatSnapshot?: RoundStatSnapshot;
}

/** 后端 RoundStat + 与本 digest 回合对齐的交互事件条数 */
export interface RoundStatSnapshot {
  /** 与后端 RoundStat.round 一致；无匹配时为 -1 */
  statRound: number;
  redTotalHp: number;
  blueTotalHp: number;
  redCount: number;
  blueCount: number;
  /** 满足 event.round === digest.round 的交互条数 */
  interactionEventCount: number;
}

export interface InfoReplaySlice {
  round: number;
  detectionCount: number;
  jamEventsNote: string;
  datalinkUpRatio: number;
  infoAdvantage: number;
  /** 指挥视角下「己方」战力占双方合计之比 0–1 */
  ownCombatShare01: number;
  redCombatPower: number;
  blueCombatPower: number;
  /** RoundStat 折算：己方总血量 / 双方总血量 */
  serverOwnHpShare01: number;
  statRedHp: number;
  statBlueHp: number;
  interactionCount: number;
}
