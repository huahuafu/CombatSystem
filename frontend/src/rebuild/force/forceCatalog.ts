import type { DeployedUnit, ForceCamp, ForceCategory, ForceTemplate, UnitCoreParams, UnitTypeCode } from "./forceTypes";

export const FORCE_CATEGORY_LABELS: Record<ForceCategory, string> = {
  SURFACE: "水面舰艇",
  AIR: "空中力量",
  UNDERWATER: "水下力量",
  SHORE: "岸基力量",
  INFORMATION: "信息作战"
};

const cp = (
  radarRangeNm: number,
  missileRangeNm: number,
  speedKts: number,
  maxDepthM?: number,
  extra?: Partial<UnitCoreParams>
): UnitCoreParams => ({ radarRangeNm, missileRangeNm, speedKts, maxDepthM, ...extra });

function row(
  id: string,
  type: UnitTypeCode,
  name: string,
  icon: ForceTemplate["icon"],
  camp: ForceCamp,
  category: ForceCategory,
  maxCount: number,
  modelLabel: string,
  coreParams: ForceTemplate["coreParams"]
): ForceTemplate {
  return { id, type, name, icon, camp, category, maxCount, modelLabel, coreParams };
}

/** 红蓝双方各自编制（面板按 commanderSide 过滤） */
export const FORCE_TEMPLATES: ForceTemplate[] = [
  // —— 水面 ——
  row("RED_CARRIER", "CARRIER", "航母", "carrier", "RED", "SURFACE", 1, "001型", cp(280, 540, 30)),
  row("BLUE_CARRIER", "CARRIER", "航母", "carrier", "BLUE", "SURFACE", 1, "核动力型", cp(320, 600, 32)),
  row("RED_LHD", "LHD", "两栖攻击舰", "amphib", "RED", "SURFACE", 2, "075型", cp(180, 40, 22)),
  row("BLUE_LHD", "LHD", "两栖攻击舰", "amphib", "BLUE", "SURFACE", 2, "美级", cp(200, 45, 23)),
  row("RED_SUPPLY", "SUPPLY_SHIP", "补给舰", "supply", "RED", "SURFACE", 3, "901型", cp(80, 12, 21)),
  row("BLUE_SUPPLY", "SUPPLY_SHIP", "补给舰", "supply", "BLUE", "SURFACE", 3, "T-AKE", cp(90, 8, 20)),
  row("RED_MINESWEEPER", "MINESWEEPER", "扫雷舰", "minesweeper", "RED", "SURFACE", 2, "081型", cp(45, 8, 18)),
  row("BLUE_MINESWEEPER", "MINESWEEPER", "扫雷舰", "minesweeper", "BLUE", "SURFACE", 2, "复仇者级", cp(50, 6, 16)),
  row("RED_DESTROYER", "DESTROYER", "驱逐舰", "destroyer", "RED", "SURFACE", 4, "052D型", cp(220, 300, 30)),
  row("BLUE_DESTROYER", "DESTROYER", "驱逐舰", "destroyer", "BLUE", "SURFACE", 4, "伯克级", cp(240, 320, 31)),
  row("RED_FRIGATE", "FRIGATE", "护卫舰", "frigate", "RED", "SURFACE", 6, "054A型", cp(160, 85, 27)),
  row("BLUE_FRIGATE", "FRIGATE", "护卫舰", "frigate", "BLUE", "SURFACE", 6, "星座级", cp(170, 90, 28)),
  // —— 空中 ——
  row("RED_AWACS", "AWACS", "预警机", "awacs", "RED", "AIR", 2, "空警-600", cp(350, 0, 450)),
  row("BLUE_AWACS", "AWACS", "预警机", "awacs", "BLUE", "AIR", 2, "E-2D", cp(380, 0, 460)),
  row("RED_EW", "EW_JET", "电子战飞机", "ew", "RED", "AIR", 2, "歼-16D", cp(200, 120, 520)),
  row("BLUE_EW", "EW_JET", "电子战飞机", "ew", "BLUE", "AIR", 2, "EA-18G", cp(220, 110, 540)),
  row("RED_ASW_HELO", "ASW_HELO", "反潜直升机", "helo", "RED", "AIR", 4, "直-20F", cp(45, 8, 150)),
  row("BLUE_ASW_HELO", "ASW_HELO", "反潜直升机", "helo", "BLUE", "AIR", 4, "MH-60R", cp(50, 12, 145)),
  row("RED_UAV", "UAV_RECON", "侦察无人机", "uav", "RED", "AIR", 6, "无侦-7", cp(120, 0, 350)),
  row("BLUE_UAV", "UAV_RECON", "侦察无人机", "uav", "BLUE", "AIR", 6, "MQ-9", cp(100, 0, 320)),
  // —— 水下 ——
  row("RED_SUB", "SUBMARINE", "潜艇", "submarine", "RED", "UNDERWATER", 3, "09III型", cp(60, 280, 20, 400)),
  row("BLUE_SUB", "SUBMARINE", "潜艇", "submarine", "BLUE", "UNDERWATER", 3, "弗吉尼亚级", cp(70, 300, 25, 500)),
  row("RED_UUV", "UUV", "无人潜航器(UUV)", "uuv", "RED", "UNDERWATER", 4, "HSU-001", cp(25, 0, 6, 600)),
  row("BLUE_UUV", "UUV", "无人潜航器(UUV)", "uuv", "BLUE", "UNDERWATER", 4, "虎鲸", cp(30, 0, 8, 600)),
  // —— 岸基 ——
  row("RED_SHORE_AD", "SHORE_AIR_DEFENSE", "岸基防空阵地", "shoreAd", "RED", "SHORE", 2, "红旗-9B阵地", cp(220, 120, 0)),
  row("BLUE_SHORE_AD", "SHORE_AIR_DEFENSE", "岸基防空阵地", "shoreAd", "BLUE", "SHORE", 2, "PAC-3阵地", cp(200, 100, 0)),
  row("RED_SHORE_ASM", "SHORE_MISSILE_BATTERY", "岸防导弹阵地", "shoreMissile", "RED", "SHORE", 2, "鹰击-12阵地", cp(180, 220, 0)),
  row("BLUE_SHORE_ASM", "SHORE_MISSILE_BATTERY", "岸防导弹阵地", "shoreMissile", "BLUE", "SHORE", 2, "NSM阵地", cp(160, 100, 0)),
  // —— 信息作战（制信息权专用编制）——
  row("RED_EW_SHIP", "EW_ELINT_SHIP", "电子侦察船", "elintShip", "RED", "INFORMATION", 2, "815A型", cp(160, 20, 18, undefined, { sensorSectorHalfDeg: 140, signatureFactor: 0.95 })),
  row("BLUE_EW_SHIP", "EW_ELINT_SHIP", "电子侦察船", "elintShip", "BLUE", "INFORMATION", 2, "胜利级", cp(170, 15, 19, undefined, { sensorSectorHalfDeg: 140, signatureFactor: 0.95 })),
  row("RED_SHORE_RADAR", "SHORE_RADAR", "岸基雷达站", "shoreRadar", "RED", "INFORMATION", 2, "远程对海警戒雷达", cp(260, 0, 0, undefined, { sensorSectorHalfDeg: 180, signatureFactor: 1 })),
  row("BLUE_SHORE_RADAR", "SHORE_RADAR", "岸基雷达站", "shoreRadar", "BLUE", "INFORMATION", 2, "AN/TPS-80", cp(240, 0, 0, undefined, { sensorSectorHalfDeg: 180, signatureFactor: 1 })),
  row("RED_SONAR_ARRAY", "SONAR_ARRAY", "水下声呐阵", "sonarArray", "RED", "INFORMATION", 3, "固定阵元区", cp(55, 0, 0, 200, { sensorSectorHalfDeg: 180, signatureFactor: 1 })),
  row("BLUE_SONAR_ARRAY", "SONAR_ARRAY", "水下声呐阵", "sonarArray", "BLUE", "INFORMATION", 3, "SOSUS 区段", cp(60, 0, 0, 200, { sensorSectorHalfDeg: 180, signatureFactor: 1 })),
  row("RED_UAV_LONG", "UAV_LONG_ENDURANCE", "长航时侦察无人机", "uavLong", "RED", "INFORMATION", 4, "无侦-10", cp(160, 0, 280, undefined, { sensorSectorHalfDeg: 70, signatureFactor: 0.88 })),
  row("BLUE_UAV_LONG", "UAV_LONG_ENDURANCE", "长航时侦察无人机", "uavLong", "BLUE", "INFORMATION", 4, "RQ-4 衍生型", cp(150, 0, 270, undefined, { sensorSectorHalfDeg: 70, signatureFactor: 0.88 }))
];

const CATEGORY_ORDER: ForceCategory[] = ["SURFACE", "AIR", "UNDERWATER", "SHORE", "INFORMATION"];

export function templatesForCamp(camp: ForceCamp): ForceTemplate[] {
  return FORCE_TEMPLATES.filter((t) => t.camp === camp).sort(
    (a, b) => CATEGORY_ORDER.indexOf(a.category) - CATEGORY_ORDER.indexOf(b.category) || a.name.localeCompare(b.name)
  );
}

export function templatesByCategory(camp: ForceCamp): Record<ForceCategory, ForceTemplate[]> {
  const out: Record<ForceCategory, ForceTemplate[]> = {
    SURFACE: [],
    AIR: [],
    UNDERWATER: [],
    SHORE: [],
    INFORMATION: []
  };
  templatesForCamp(camp).forEach((t) => {
    out[t.category].push(t);
  });
  return out;
}

export function getTemplate(templateId: string): ForceTemplate | undefined {
  return FORCE_TEMPLATES.find((t) => t.id === templateId);
}

export function getTemplateByTypeAndCamp(type: string, camp: ForceCamp): ForceTemplate | undefined {
  return FORCE_TEMPLATES.find((t) => t.type === type && t.camp === camp);
}

export function deployedCountForTemplate(units: { type: string; side: string }[], tpl: ForceTemplate): number {
  return units.filter((u) => u.type === tpl.type && u.side === tpl.camp).length;
}

export function remainForTemplate(
  units: { type: string; side: string }[],
  tpl: ForceTemplate
): number {
  return Math.max(0, tpl.maxCount - deployedCountForTemplate(units, tpl));
}

/** 兼容旧 UNIT_TYPE_LABELS：按 type 返回中文（同 type 多阵营时取首个名称） */
export const UNIT_TYPE_LABELS: Partial<Record<string, string>> = FORCE_TEMPLATES.reduce(
  (acc, t) => {
    if (!acc[t.type]) acc[t.type] = t.name;
    return acc;
  },
  {} as Partial<Record<string, string>>
);

export function getUnitTypeLabel(type: string): string {
  return UNIT_TYPE_LABELS[type] || type;
}

export function buildDeployedUnit(tpl: ForceTemplate, longitude: number, latitude: number, index: number): DeployedUnit {
  return {
    id: `${tpl.type}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    name: `${tpl.name}-${index}`,
    side: tpl.camp,
    type: tpl.type,
    longitude,
    latitude,
    mission: "机动部署",
    modelLabel: tpl.modelLabel,
    coreParams: { ...tpl.coreParams }
  };
}

/** 兼容旧 countDeployedByType（不按阵营聚合上限校验时用） */
export function countDeployedByType(units: { type: string }[]): Record<string, number> {
  const counter: Record<string, number> = {};
  units.forEach((u) => {
    counter[u.type] = (counter[u.type] || 0) + 1;
  });
  return counter;
}
