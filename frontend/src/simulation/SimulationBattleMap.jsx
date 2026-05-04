import React, { useEffect, useRef } from "react";
import { Button, Card, Space, Spin, Tooltip, message } from "antd";
import { BorderOutlined, FullscreenOutlined } from "@ant-design/icons";
import Map from "ol/Map";
import View from "ol/View";
import TileLayer from "ol/layer/Tile";
import XYZ from "ol/source/XYZ";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Feature from "ol/Feature";
import Point from "ol/geom/Point";
import LineString from "ol/geom/LineString";
import { fromLonLat } from "ol/proj";
import { Fill, Stroke, Style, Circle as CircleStyle } from "ol/style";
import { defaults as defaultControls, FullScreen } from "ol/control";
import "ol/ol.css";

function getUnitColor(side, selected = false) {
  if (side === "RED") return selected ? "#ef4444" : "#dc2626";
  return selected ? "#3b82f6" : "#2563eb";
}

/**
 * 仿真战场主视图：与指挥端相同的 OSM 底图与矢量层逻辑，只读展示。
 * units / objectives 与想定或 /v2/simulation/units 联动；planRoutes 为方案预览折线。
 */
export default function SimulationBattleMap({
  units = [],
  objectives = [],
  planRoutes = [],
  flashToken = 0,
  center = [121.5, 22.8],
  zoom = 8
}) {
  const rootRef = useRef(null);
  const mapRef = useRef(null);
  const unitLayerRef = useRef(null);
  const objLayerRef = useRef(null);
  const routeLayerRef = useRef(null);
  const [loading, setLoading] = React.useState(true);

  useEffect(() => {
    if (!rootRef.current || mapRef.current) return;
    try {
      const base = new TileLayer({
        source: new XYZ({ url: "https://tile.openstreetmap.org/{z}/{x}/{y}.png" })
      });
      unitLayerRef.current = new VectorLayer({ source: new VectorSource() });
      objLayerRef.current = new VectorLayer({ source: new VectorSource() });
      routeLayerRef.current = new VectorLayer({ source: new VectorSource() });
      mapRef.current = new Map({
        target: rootRef.current,
        controls: defaultControls().extend([new FullScreen()]),
        layers: [base, routeLayerRef.current, objLayerRef.current, unitLayerRef.current],
        view: new View({ center: fromLonLat(center), zoom })
      });
      const ro = new ResizeObserver(() => mapRef.current?.updateSize());
      ro.observe(rootRef.current);
      rootRef.current._ro = ro;
      setTimeout(() => {
        setLoading(false);
        mapRef.current?.updateSize();
      }, 300);
    } catch (e) {
      message.error(e?.message || "地图初始化失败");
      setLoading(false);
    }
    return () => {
      const r = rootRef.current;
      if (r?._ro) {
        r._ro.disconnect();
        delete r._ro;
      }
      if (mapRef.current) {
        mapRef.current.setTarget(undefined);
        mapRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (!mapRef.current || !unitLayerRef.current) return;
    mapRef.current.getView().setCenter(fromLonLat(center));
    mapRef.current.getView().setZoom(zoom);
  }, [center[0], center[1], zoom]);

  useEffect(() => {
    if (!unitLayerRef.current || !objLayerRef.current || !routeLayerRef.current) return;
    const uSrc = unitLayerRef.current.getSource();
    const oSrc = objLayerRef.current.getSource();
    const rSrc = routeLayerRef.current.getSource();
    uSrc.clear();
    oSrc.clear();
    rSrc.clear();

    planRoutes.forEach((pr) => {
      if (!pr.coords?.length) return;
      const line = new Feature({
        geometry: new LineString(pr.coords.map((c) => fromLonLat(c)))
      });
      line.setStyle(
        new Style({
          stroke: new Stroke({
            color: pr.color || "#22d3ee",
            width: pr.dashed ? 3 : 2,
            lineDash: pr.dashed ? [10, 8] : undefined
          })
        })
      );
      rSrc.addFeature(line);
    });

    units.forEach((u) => {
      const lon = u.longitude;
      const lat = u.latitude;
      if (lon == null || lat == null) return;
      const feature = new Feature({ geometry: new Point(fromLonLat([lon, lat])) });
      feature.setStyle(
        new Style({
          image: new CircleStyle({
            radius: 7,
            fill: new Fill({ color: getUnitColor(u.side) }),
            stroke: new Stroke({ color: "#fff", width: 1 })
          })
        })
      );
      uSrc.addFeature(feature);
    });

    objectives.forEach((o) => {
      const lon = o.longitude;
      const lat = o.latitude;
      if (lon == null || lat == null) return;
      const feature = new Feature({ geometry: new Point(fromLonLat([lon, lat])) });
      feature.setStyle(
        new Style({
          image: new CircleStyle({
            radius: 8,
            fill: new Fill({ color: o.type === "DESTROY" ? "#dc2626" : o.type === "CAPTURE" ? "#f59e0b" : "#22c55e" }),
            stroke: new Stroke({ color: "#fff", width: 1 })
          })
        })
      );
      oSrc.addFeature(feature);
    });

    mapRef.current?.updateSize();
  }, [units, objectives, planRoutes, flashToken]);

  function resetView() {
    mapRef.current?.getView().animate({ center: fromLonLat(center), zoom, duration: 280 });
  }

  function fitToData() {
    if (!mapRef.current || !units.length && !objectives.length) {
      resetView();
      return;
    }
    const coords = [];
    units.forEach((u) => {
      if (u.longitude != null && u.latitude != null) coords.push(fromLonLat([u.longitude, u.latitude]));
    });
    objectives.forEach((o) => {
      if (o.longitude != null && o.latitude != null) coords.push(fromLonLat([o.longitude, o.latitude]));
    });
    if (!coords.length) return;
    const extent = coords.reduce(
      (acc, c) => {
        return [
          Math.min(acc[0], c[0]),
          Math.min(acc[1], c[1]),
          Math.max(acc[2], c[0]),
          Math.max(acc[3], c[1])
        ];
      },
      [coords[0][0], coords[0][1], coords[0][0], coords[0][1]]
    );
    mapRef.current.getView().fit(extent, { padding: [80, 80, 80, 80], maxZoom: 11, duration: 320 });
  }

  return (
    <Card
      className="commander-map-card simulation-map-card"
      size="small"
      title="战场主视图 · 仿真联动"
      extra={
        <Space size={4}>
          <Tooltip title="适配兵力与目标">
            <Button size="small" icon={<FullscreenOutlined />} onClick={fitToData} />
          </Tooltip>
          <Tooltip title="重置视角">
            <Button size="small" icon={<BorderOutlined />} onClick={resetView} />
          </Tooltip>
        </Space>
      }
      styles={{ body: { flex: 1, minHeight: 0, display: "flex", flexDirection: "column", padding: 10 } }}
    >
      <div style={{ position: "relative", flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
        {loading ? (
          <div style={{ flex: 1, minHeight: 320, display: "grid", placeItems: "center", background: "#0f172a", borderRadius: 10 }}>
            <Spin tip="地图载入..." />
          </div>
        ) : null}
        <div
          ref={rootRef}
          className="commander-ol-target"
          style={{
            flex: 1,
            minHeight: 360,
            borderRadius: 10,
            overflow: "hidden",
            border: "1px solid #334155",
            display: loading ? "none" : "block"
          }}
        />
        <div className="sim-map-legend muted" style={{ fontSize: 11, marginTop: 8 }}>
          圆点：己方/敌方兵力；方块色：战役目标；青色虚线：当前选中方案的机动预览（与部署想定数据同源，回合推进后同步战场单位）。
        </div>
      </div>
    </Card>
  );
}
