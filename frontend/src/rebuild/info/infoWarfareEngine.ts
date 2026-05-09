import type { DeployedUnit, ForceCamp } from "../force/forceTypes";
import { bearingDeg, distanceNm } from "./infoGeometry";
import type {
  BattleRoundSummary,
  DatalinkEdge,
  DetectionHit,
  EnvironmentState,
  EwCasualtySummary,
  JammingZone,
  KillChainInfoFactors,
  RoundInfoDigest,
  UnitInfoCombatState,
  UnitSensorModel
} from "./infoTypes";

const DEFAULT_INFO: UnitInfoCombatState = {
  radarEmitting: true,
  radioSilent: false,
  ewIntensity: 0
};

function unitInfoState(map: Record<string, UnitInfoCombatState> | undefined, id: string): UnitInfoCombatState {
  return map?.[id] ?? DEFAULT_INFO;
}

/** 按兵种给出主传感器模型（雷达/声呐/光电/卫通侦察简化归类） */
export function resolveSensorModel(unit: DeployedUnit): UnitSensorModel | null {
  const t = unit.type;
  const r = unit.coreParams?.radarRangeNm ?? 80;
  const sector = unit.coreParams?.sensorSectorHalfDeg ?? 120;
  const stealthPen = unit.coreParams?.signatureFactor ?? 1;

  if (t === "SUBMARINE" || t === "UUV") {
    return { modality: "SONAR", maxRangeNm: r * 0.35, sectorHalfAngleDeg: 90, stealthPenaltyFactor: stealthPen };
  }
  if (t === "SONAR_ARRAY") {
    return { modality: "SONAR", maxRangeNm: r, sectorHalfAngleDeg: 180, stealthPenaltyFactor: stealthPen };
  }
  if (t === "UAV_RECON" || t === "UAV_LONG_ENDURANCE") {
    return { modality: "EO_IR", maxRangeNm: Math.min(r, 180), sectorHalfAngleDeg: 75, stealthPenaltyFactor: stealthPen };
  }
  if (t === "AWACS") {
    return { modality: "RADAR", maxRangeNm: Math.max(r, 280), sectorHalfAngleDeg: 180, stealthPenaltyFactor: stealthPen };
  }
  if (t === "SHORE_RADAR") {
    return { modality: "RADAR", maxRangeNm: Math.max(r, 220), sectorHalfAngleDeg: 180, stealthPenaltyFactor: stealthPen };
  }
  if (t === "EW_JET" || t === "EW_ELINT_SHIP") {
    return { modality: "RADAR", maxRangeNm: r * 0.85, sectorHalfAngleDeg: 140, stealthPenaltyFactor: stealthPen };
  }
  if (t === "ASW_HELO") {
    return { modality: "SONAR", maxRangeNm: Math.min(r, 55), sectorHalfAngleDeg: 110, stealthPenaltyFactor: stealthPen };
  }
  if (t === "DESTROYER" || t === "FRIGATE" || t === "CARRIER" || t === "LHD" || t === "MINESWEEPER" || t === "SUPPLY_SHIP") {
    return { modality: "RADAR", maxRangeNm: r, sectorHalfAngleDeg: sector, stealthPenaltyFactor: stealthPen };
  }
  return { modality: "RADAR", maxRangeNm: r * 0.6, sectorHalfAngleDeg: 120, stealthPenaltyFactor: stealthPen };
}

function modalityStack(mod: SensorModality): number {
  switch (mod) {
    case "SATELLITE":
      return 4;
    case "RADAR":
      return 3;
    case "EO_IR":
      return 2;
    case "SONAR":
      return 1;
    default:
      return 0;
  }
}

function effectiveRangeNm(
  base: number,
  env: EnvironmentState,
  jamFactor: number,
  radarOn: boolean,
  modality: SensorModality
): number {
  if (!radarOn && modality === "RADAR") return 0;
  const wx = env.weatherAttenuation * (1 - (env.seaState - 1) * 0.04);
  const w = Math.max(0.45, Math.min(1, wx));
  return Math.max(0, base * w * jamFactor);
}

/** 用于地图绘制：单平台当前有效探测半径（海里） */
export function effectiveDetectorRangeNm(
  det: DeployedUnit,
  ownSide: ForceCamp,
  env: EnvironmentState,
  infoMap: Record<string, UnitInfoCombatState> | undefined,
  jamZonesAll: JammingZone[]
): number {
  const model = resolveSensorModel(det);
  if (!model) return 0;
  const st = unitInfoState(infoMap, det.id);
  const enemyJam = enemyZonesFor(ownSide, jamZonesAll);
  const jamF = jammingEfficiencyForDetector(det.longitude, det.latitude, enemyJam);
  return effectiveRangeNm(model.maxRangeNm, env, jamF, st.radarEmitting || model.modality !== "RADAR", model.modality);
}

/** 敌方干扰对己方探测器造成的效率乘子 */
export function jammingEfficiencyForDetector(
  detectorLon: number,
  detectorLat: number,
  enemyZones: JammingZone[]
): number {
  let worst = 1;
  for (const z of enemyZones) {
    const d = distanceNm(z.centerLon, z.centerLat, detectorLon, detectorLat);
    if (d <= z.radiusNm) {
      const edge = Math.max(0, 1 - d / Math.max(z.radiusNm, 1e-6));
      const f = 1 - z.strength * (0.35 + 0.65 * edge);
      worst = Math.min(worst, f);
    }
  }
  return Math.max(0.08, worst);
}

export function buildJammingZones(units: DeployedUnit[], infoMap: Record<string, UnitInfoCombatState> | undefined): JammingZone[] {
  const zones: JammingZone[] = [];
  let seq = 0;
  for (const u of units) {
    const st = unitInfoState(infoMap, u.id);
    if (st.ewIntensity <= 0) continue;
    const baseR = u.type === "EW_JET" ? 85 + st.ewIntensity * 25 : u.type === "EW_ELINT_SHIP" ? 55 + st.ewIntensity * 20 : 0;
    if (baseR <= 0) continue;
    const strength = Math.min(1, 0.25 + st.ewIntensity * 0.22);
    zones.push({
      id: `jam-${u.id}-${seq++}`,
      side: u.side,
      centerLon: u.longitude,
      centerLat: u.latitude,
      radiusNm: baseR,
      strength,
      mode: u.type === "EW_ELINT_SHIP" ? "COMM" : "RADAR"
    });
  }
  return zones;
}

export function enemyZonesFor(side: ForceCamp, zones: JammingZone[]): JammingZone[] {
  return zones.filter((z) => z.side !== side);
}

export function computeDetections(
  ownSide: ForceCamp,
  units: DeployedUnit[],
  env: EnvironmentState,
  infoMap: Record<string, UnitInfoCombatState> | undefined,
  jamZonesAll: JammingZone[]
): DetectionHit[] {
  const own = units.filter((u) => u.side === ownSide);
  const enemies = units.filter((u) => u.side !== ownSide);
  const enemyJam = enemyZonesFor(ownSide, jamZonesAll);
  const hits: DetectionHit[] = [];

  for (const det of own) {
    const model = resolveSensorModel(det);
    if (!model) continue;
    const st = unitInfoState(infoMap, det.id);
    const jamF = jammingEfficiencyForDetector(det.longitude, det.latitude, enemyJam);
    const rng = effectiveRangeNm(model.maxRangeNm, env, jamF, st.radarEmitting || model.modality !== "RADAR", model.modality);
    if (rng <= 0) continue;

    let bearRef = 0;
    if (enemies.length) {
      const elon = enemies.reduce((s, e) => s + e.longitude, 0) / enemies.length;
      const elat = enemies.reduce((s, e) => s + e.latitude, 0) / enemies.length;
      bearRef = bearingDeg(det.longitude, det.latitude, elon, elat);
    }

    for (const tgt of enemies) {
      const d = distanceNm(det.longitude, det.latitude, tgt.longitude, tgt.latitude);
      const sig = tgt.coreParams?.signatureFactor ?? 1;
      const need = d * sig;
      if (need > rng) continue;
      if (model.sectorHalfAngleDeg < 179.9) {
        const b = bearingDeg(det.longitude, det.latitude, tgt.longitude, tgt.latitude);
        let diff = Math.abs(b - bearRef);
        if (diff > 180) diff = 360 - diff;
        if (diff > model.sectorHalfAngleDeg) continue;
      }
      hits.push({
        detectorId: det.id,
        targetId: tgt.id,
        targetSide: tgt.side,
        rangeNm: d,
        modality: model.modality
      });
    }
  }

  const best = new Map<string, DetectionHit>();
  for (const h of hits) {
    const prev = best.get(h.targetId);
    if (!prev || modalityStack(h.modality) > modalityStack(prev.modality) || h.rangeNm < prev.rangeNm) {
      best.set(h.targetId, h);
    }
  }
  return [...best.values()];
}

export function buildDatalinkEdges(
  ownSide: ForceCamp,
  units: DeployedUnit[],
  infoMap: Record<string, UnitInfoCombatState> | undefined,
  jamZonesAll: JammingZone[]
): DatalinkEdge[] {
  const own = units.filter((u) => u.side === ownSide);
  if (own.length < 2) return [];
  const enemyJam = enemyZonesFor(ownSide, jamZonesAll);
  const hub =
    own.find((u) => u.type === "AWACS" || u.type === "CARRIER")?.id ??
    own.find((u) => u.type === "DESTROYER")?.id ??
    own[0].id;
  const edges: DatalinkEdge[] = [];
  for (const u of own) {
    if (u.id === hub) continue;
    const st = unitInfoState(infoMap, u.id);
    const jamF = jammingEfficiencyForDetector(u.longitude, u.latitude, enemyJam);
    const silent = st.radioSilent ? 0.45 : 1;
    const q = Math.max(0.05, Math.min(1, jamF * silent * (u.type === "SUBMARINE" ? 0.55 : 1)));
    edges.push({
      fromUnitId: hub,
      toUnitId: u.id,
      quality: q,
      degradedByJamming: jamF < 0.92
    });
  }
  return edges;
}

export function summarizeEw(
  ownSide: ForceCamp,
  units: DeployedUnit[],
  jamZonesAll: JammingZone[]
): EwCasualtySummary {
  const own = units.filter((u) => u.side === ownSide);
  const enemyJam = enemyZonesFor(ownSide, jamZonesAll);
  let degraded = 0;
  let inJam = 0;
  let detSum = 0;
  let wpnSum = 0;
  let n = 0;
  for (const u of own) {
    const model = resolveSensorModel(u);
    if (!model) continue;
    const f = jammingEfficiencyForDetector(u.longitude, u.latitude, enemyJam);
    detSum += f;
    wpnSum += f * f;
    n += 1;
    if (f < 0.9) degraded += 1;
    if (f < 0.75) inJam += 1;
  }
  const avgDet = n ? detSum / n : 1;
  const avgWpn = n ? wpnSum / n : 1;
  return {
    detectorsDegraded: degraded,
    unitsInEnemyJam: inJam,
    avgDetectionEfficiency: avgDet,
    avgWeaponAccuracyFactor: avgWpn
  };
}

export function datalinkIntegrity(edges: DatalinkEdge[]): number {
  if (!edges.length) return 1;
  const s = edges.reduce((a, e) => a + e.quality, 0);
  return Math.max(0, Math.min(1, s / edges.length));
}

/** 从当前兵力列表汇总双方战力（传统战报层，用于与信息维度同轴复盘） */
export function summarizeBattleForRound(units: DeployedUnit[]): BattleRoundSummary {
  let redCombatPower = 0;
  let blueCombatPower = 0;
  let redAlive = 0;
  let blueAlive = 0;
  for (const u of units) {
    const p = typeof u.combatPower === "number" && !Number.isNaN(u.combatPower) ? u.combatPower : 0;
    if (p <= 0) {
      continue;
    }
    if (u.side === "RED") {
      redCombatPower += p;
      redAlive += 1;
    } else if (u.side === "BLUE") {
      blueCombatPower += p;
      blueAlive += 1;
    }
  }
  const total = redCombatPower + blueCombatPower;
  const redShare01 = total > 0 ? redCombatPower / total : 0.5;
  return {
    redCombatPower,
    blueCombatPower,
    redAlive,
    blueAlive,
    redShare01
  };
}

export function computeInfoAdvantage(
  ownSide: ForceCamp,
  units: DeployedUnit[],
  ownDetections: DetectionHit[],
  enemyDetections: DetectionHit[],
  datalinkIntegrity01: number
): number {
  const enemyCount = units.filter((u) => u.side !== ownSide).length;
  const ownCount = units.filter((u) => u.side === ownSide).length;
  const vis = ownDetections.length / Math.max(1, enemyCount);
  const visOpp = enemyDetections.length / Math.max(1, ownCount);
  const raw = 0.5 * Math.min(1, vis) + 0.25 * datalinkIntegrity01 + 0.25 * (1 - Math.min(1, visOpp));
  return Math.max(0, Math.min(1, raw));
}

export function killChainFactorsFromDigest(digest: RoundInfoDigest): KillChainInfoFactors {
  const d = digest.ewSummary.avgDetectionEfficiency;
  const w = digest.ewSummary.avgWeaponAccuracyFactor;
  const l = digest.datalinkIntegrity;
  const detBoost = Math.min(1, 0.35 + (digest.detections.length / 8) * 0.65);
  return {
    find: detBoost * d,
    fix: d * l,
    track: d * 0.85 + l * 0.15,
    target: l * w,
    engage: w * l,
    assess: 0.55 + 0.45 * l
  };
}

export function buildRoundDigest(
  round: number,
  ownSide: ForceCamp,
  units: DeployedUnit[],
  env: EnvironmentState,
  infoMap: Record<string, UnitInfoCombatState> | undefined
): RoundInfoDigest {
  const jammingZones = buildJammingZones(units, infoMap);
  const other: ForceCamp = ownSide === "RED" ? "BLUE" : "RED";
  const detections = computeDetections(ownSide, units, env, infoMap, jammingZones);
  const enemyDetections = computeDetections(other, units, env, infoMap, jammingZones);
  const datalinkEdges = buildDatalinkEdges(ownSide, units, infoMap, jammingZones);
  const ewSummary = summarizeEw(ownSide, units, jammingZones);
  const dl = datalinkIntegrity(datalinkEdges);
  const infoAdvantage = computeInfoAdvantage(ownSide, units, detections, enemyDetections, dl);
  const battle = summarizeBattleForRound(units);
  return {
    round,
    timestamp: Date.now(),
    detections,
    ewSummary,
    datalinkIntegrity: dl,
    infoAdvantage,
    jammingZones,
    datalinkEdges,
    battle
  };
}
