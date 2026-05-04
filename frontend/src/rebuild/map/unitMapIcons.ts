import Style from "ol/style/Style";
import Icon from "ol/style/Icon";
import type { ForceCamp } from "../force/forceTypes";

function unitFill(side: ForceCamp, selected: boolean): string {
  if (side === "RED") return selected ? "#f87171" : "#ef4444";
  return selected ? "#60a5fa" : "#3b82f6";
}

/** 每种兵种的矢量轮廓（viewBox 0 0 32 32），便于地图区分 */
function unitGlyphSvgPaths(unitType: string): string {
  switch (unitType) {
    case "CARRIER":
      return `<path d="M4 20 L28 20 L26 14 L6 14 Z"/><rect x="12" y="10" width="4" height="5" rx="0.5"/>`;
    case "LHD":
      return `<path d="M5 21 L27 21 L25 15 L7 15 Z"/><rect x="10" y="11" width="6" height="5"/>`;
    case "SUPPLY_SHIP":
      return `<rect x="5" y="15" width="22" height="6" rx="1"/><line x1="20" y1="15" x2="22" y2="10" stroke-linecap="round"/>`;
    case "MINESWEEPER":
      return `<path d="M6 20 L26 20 L24 16 L8 16 Z"/><circle cx="10" cy="17" r="1.8" fill="none" stroke="inherit"/>`;
    case "DESTROYER":
      return `<path d="M4 20 L28 18 L26 14 L8 14 Z"/><line x1="16" y1="14" x2="16" y2="10" stroke-linecap="round"/>`;
    case "FRIGATE":
      return `<path d="M6 20 L26 19 L24 15 L8 15 Z"/><line x1="15" y1="15" x2="15" y2="11" stroke-linecap="round"/>`;
    case "SUBMARINE":
      return `<ellipse cx="16" cy="18" rx="11" ry="4"/><path d="M10 14 L12 10 L16 12 L20 10 L22 14"/>`;
    case "UUV":
      return `<ellipse cx="16" cy="18" rx="9" ry="3"/><circle cx="22" cy="18" r="1.2"/>`;
    case "UAV_RECON":
      return `<path d="M4 16 L10 16 L16 12 L22 16 L28 16 L22 18 L10 18 Z"/>`;
    case "AWACS":
      return `<ellipse cx="16" cy="17" rx="10" ry="4"/><ellipse cx="16" cy="12" rx="3" ry="2"/>`;
    case "EW_JET":
      return `<path d="M6 17 L26 15 L24 12 L10 12 Z"/><path d="M18 12 L22 8 L20 12"/>`;
    case "ASW_HELO":
      return `<ellipse cx="16" cy="13" rx="7" ry="1.5"/><rect x="11" y="14" width="10" height="5" rx="1"/>`;
    case "SHORE_MISSILE_BATTERY":
      return `<path d="M16 6 L20 22 L12 22 Z"/><line x1="16" y1="22" x2="16" y2="26" stroke-linecap="round"/><line x1="12" y1="26" x2="20" y2="26" stroke-linecap="round"/>`;
    case "SHORE_AIR_DEFENSE":
      return `<path d="M8 22 Q16 8 24 22 Z"/><rect x="12" y="22" width="8" height="4" rx="0.5"/>`;
    default:
      return `<path d="M6 20 L26 19 L24 15 L8 15 Z"/>`;
  }
}

const markerCache = new Map<string, string>();

function markerDataUrl(unitType: string, side: ForceCamp, selected: boolean): string {
  const key = `${unitType}|${side}|${selected ? 1 : 0}`;
  const hit = markerCache.get(key);
  if (hit) return hit;

  const fill = unitFill(side, selected);
  const ring = selected ? "#fef08a" : "#ffffff";
  const sw = selected ? 2.2 : 1.3;
  const glyphs = unitGlyphSvgPaths(unitType);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32">
  <defs>
    <filter id="s" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="1" stdDeviation="1" flood-color="#000" flood-opacity="0.45"/>
    </filter>
  </defs>
  <circle cx="16" cy="16" r="14" fill="#0f172a" fill-opacity="0.42" filter="url(#s)"/>
  <g fill="${fill}" stroke="${ring}" stroke-width="${sw}" stroke-linejoin="round" filter="url(#s)">
    ${glyphs}
  </g>
</svg>`;
  const url = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  markerCache.set(key, url);
  return url;
}

/** OpenLayers 点样式：一兵种一图标，阵营配色 + 选中描边 */
export function getUnitMarkerStyle(unitType: string, side: ForceCamp, selected: boolean): Style {
  return new Style({
    image: new Icon({
      src: markerDataUrl(unitType, side, selected),
      scale: 1,
      anchor: [0.5, 0.52],
      anchorXUnits: "fraction",
      anchorYUnits: "fraction"
    })
  });
}
