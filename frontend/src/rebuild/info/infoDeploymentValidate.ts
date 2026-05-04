import type { DeployedUnit, ForceCamp } from "../force/forceTypes";

const EARLY_TYPES = new Set(["AWACS", "SHORE_RADAR", "UAV_LONG_ENDURANCE", "UAV_RECON"]);
const EW_TYPES = new Set(["EW_JET", "EW_ELINT_SHIP"]);
const ASW_INFO_TYPES = new Set(["ASW_HELO", "SONAR_ARRAY", "DESTROYER", "FRIGATE"]);

export interface InfoDeploymentValidation {
  valid: boolean;
  issues: string[];
}

export function validateInfoDeployment(units: DeployedUnit[], commanderSide: ForceCamp): InfoDeploymentValidation {
  const mine = units.filter((u) => u.side === commanderSide);
  const issues: string[] = [];

  const hasEarly = mine.some((u) => EARLY_TYPES.has(u.type));
  if (!hasEarly) {
    issues.push("信息维度：缺少远程预警/岸基雷达/长航时侦察等战场感知节点");
  }

  const hasEw = mine.some((u) => EW_TYPES.has(u.type));
  if (!hasEw) {
    issues.push("信息维度：缺少电子战掩护（电子战飞机或电子侦察/干扰舰）");
  }

  const hasAswInfo = mine.some((u) => ASW_INFO_TYPES.has(u.type));
  if (!hasAswInfo) {
    issues.push("信息维度：缺少反潜信息支撑（反潜机、声呐阵或主力驱护舰传感器网）");
  }

  return { valid: issues.length === 0, issues };
}
