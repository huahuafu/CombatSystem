import { fromLonLat } from "ol/proj";
import type { Coordinate } from "ol/coordinate";

const NM_TO_METERS = 1852;

/** Web Mercator 下半径（米）近似：在纬度 lat 处将海里换算为地面米 */
export function nmToMetersAtLat(nm: number, latDeg: number): number {
  const cos = Math.max(0.2, Math.cos((latDeg * Math.PI) / 180));
  return nm * NM_TO_METERS * cos;
}

/** 以 center 为圆心、海里为半径，生成近似圆多边形（EPSG:3857） */
export function circlePolygon3857(centerLon: number, centerLat: number, radiusNm: number, segments = 48): Coordinate[] {
  const c = fromLonLat([centerLon, centerLat]);
  const rM = nmToMetersAtLat(radiusNm, centerLat);
  const coords: Coordinate[] = [];
  for (let i = 0; i <= segments; i++) {
    const a = (i / segments) * Math.PI * 2;
    coords.push([c[0] + rM * Math.cos(a), c[1] + rM * Math.sin(a)]);
  }
  return coords;
}

/**
 * 扇区多边形：从 center 出发，朝向 bearingDeg，半宽 halfAngleDeg，外沿弧为 radiusNm
 */
export function sectorPolygon3857(
  centerLon: number,
  centerLat: number,
  bearingDeg: number,
  halfAngleDeg: number,
  radiusNm: number,
  segments = 24
): Coordinate[] {
  const c = fromLonLat([centerLon, centerLat]);
  const rM = nmToMetersAtLat(radiusNm, centerLat);
  const br = (bearingDeg * Math.PI) / 180;
  const hw = (halfAngleDeg * Math.PI) / 180;
  const coords: Coordinate[] = [c];
  for (let i = 0; i <= segments; i++) {
    const t = -hw + (i / segments) * 2 * hw;
    const a = br + t;
    coords.push([c[0] + rM * Math.cos(a), c[1] + rM * Math.sin(a)]);
  }
  coords.push(c);
  return coords;
}

/** 两点大地线方位角（度），正北为 0 */
export function bearingDeg(lon1: number, lat1: number, lon2: number, lat2: number): number {
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const Δλ = ((lon2 - lon1) * Math.PI) / 180;
  const y = Math.sin(Δλ) * Math.cos(φ2);
  const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);
  const θ = Math.atan2(y, x);
  return (((θ * 180) / Math.PI + 360) % 360);
}

/** 海里球面距离（简化球体） */
export function distanceNm(lon1: number, lat1: number, lon2: number, lat2: number): number {
  const R = 3440.065; // 地球半径（海里）
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const Δφ = ((lat2 - lat1) * Math.PI) / 180;
  const Δλ = ((lon2 - lon1) * Math.PI) / 180;
  const a = Math.sin(Δφ / 2) ** 2 + Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}
