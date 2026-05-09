/** 与 simulationStore / 后端 CombatUnit.side 一致 */
export type ForceCamp = "RED" | "BLUE";

export type ForceCategory = "SURFACE" | "AIR" | "UNDERWATER" | "SHORE" | "INFORMATION";

/** 兵力类型编码（写入想定 JSON 的 type 字段） */
export type UnitTypeCode =
  | "DESTROYER"
  | "FRIGATE"
  | "SUBMARINE"
  | "UAV_RECON"
  | "UAV_LONG_ENDURANCE"
  | "CARRIER"
  | "LHD"
  | "SUPPLY_SHIP"
  | "MINESWEEPER"
  | "AWACS"
  | "EW_JET"
  | "EW_ELINT_SHIP"
  | "ASW_HELO"
  | "UUV"
  | "SHORE_MISSILE_BATTERY"
  | "SHORE_AIR_DEFENSE"
  | "SHORE_RADAR"
  | "SONAR_ARRAY";

/** 核心战技参数（前端推演展示；后端可忽略额外字段） */
export interface UnitCoreParams {
  /** 雷达探测距离（海里） */
  radarRangeNm: number;
  /** 导弹射程（海里） */
  missileRangeNm: number;
  /** 水面/空中航速（节）；岸基可为 0 */
  speedKts: number;
  /** 最大潜深（米），水面/空中/岸基为 undefined */
  maxDepthM?: number;
  /** 主传感器扇区半角（度），180 为全向 */
  sensorSectorHalfDeg?: number;
  /** 作为目标时的隐身/RCS 折算，1 为基准，越小越难被探测 */
  signatureFactor?: number;
}

/** 兵力面板条目（编制表） */
export interface ForceTemplate {
  id: string;
  type: UnitTypeCode;
  name: string;
  /** 面板用图标 key，对应 Ant Design 图标名 */
  icon: ForceIconKey;
  camp: ForceCamp;
  category: ForceCategory;
  maxCount: number;
  /** 型号/编制说明 */
  modelLabel: string;
  coreParams: UnitCoreParams;
}

export type ForceIconKey =
  | "ship"
  | "carrier"
  | "amphib"
  | "supply"
  | "minesweeper"
  | "destroyer"
  | "frigate"
  | "submarine"
  | "uuv"
  | "uav"
  | "uavLong"
  | "awacs"
  | "ew"
  | "elintShip"
  | "helo"
  | "shoreMissile"
  | "shoreAd"
  | "shoreRadar"
  | "sonarArray";

/** 已部署兵力（与现有 API 字段兼容，可附加 modelLabel / coreParams） */
export interface DeployedUnit {
  id: string;
  name: string;
  side: ForceCamp;
  type: string;
  latitude: number;
  longitude: number;
  mission?: string;
  modelLabel?: string;
  coreParams?: UnitCoreParams;
  /** 仿真/想定兵力战力，用于战报层复盘（可选） */
  combatPower?: number;
}
