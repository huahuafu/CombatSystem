export const UNIT_TYPE_LABELS = {
  DESTROYER: "驱逐舰",
  FRIGATE: "护卫舰",
  SUBMARINE: "潜艇",
  UAV_RECON: "侦察无人机"
};

/** 与后端协议字段 type 一致；label 为界面展示 */
export const UNIT_TEMPLATES = [
  { type: "DESTROYER", label: "驱逐舰", total: 6 },
  { type: "FRIGATE", label: "护卫舰", total: 8 },
  { type: "SUBMARINE", label: "潜艇", total: 4 },
  { type: "UAV_RECON", label: "侦察无人机", total: 10 }
];

export const OBJECTIVE_TYPE_OPTIONS = [
  { value: "DEFEND", label: "坚守" },
  { value: "CAPTURE", label: "占领" },
  { value: "DESTROY", label: "摧毁" }
];

export function countDeployedByType(units) {
  const counter = {};
  units.forEach((u) => {
    counter[u.type] = (counter[u.type] || 0) + 1;
  });
  return counter;
}

export function addUnitToList(baseUnits, side, unitType, extra = {}) {
  const nextIndex = baseUnits.filter((u) => u.type === unitType).length + 1;
  const label = UNIT_TYPE_LABELS[unitType] || unitType;
  return [
    ...baseUnits,
    {
      id: `${unitType}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      name: `${label}-${nextIndex}`,
      side,
      type: unitType,
      latitude: extra.latitude ?? 22.8 + baseUnits.length * 0.02,
      longitude: extra.longitude ?? 121.4 + baseUnits.length * 0.03,
      mission: extra.mission || "机动部署"
    }
  ];
}
