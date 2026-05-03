import TileLayer from "ol/layer/Tile";
import XYZ from "ol/source/XYZ";

/**
 * 彩色街道栅格底图（CARTO Voyager，观感接近原先 OSM 彩图）。
 * 仍走 basemaps.cartocdn.com，避免直连 tile.openstreetmap.org 在国内/部分网络超时。
 * @see https://carto.com/help/build-maps/basemap-list/
 */
export function createBasemapTileLayer() {
  return new TileLayer({
    className: "combat-basemap-layer",
    source: new XYZ({
      urls: [
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
        "https://d.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
      ],
      attributions:
        '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors, © <a href="https://carto.com/" target="_blank" rel="noreferrer">CARTO</a>',
      maxZoom: 19,
      crossOrigin: "anonymous"
    })
  });
}
