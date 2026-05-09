import React, { useEffect, useRef } from "react";
import { Button, Card, Space, Spin, Tooltip, message, Switch } from "antd";
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
import { Fill, Stroke, Style, Circle as CircleStyle, Text } from "ol/style";
import { defaults as defaultControls, FullScreen } from "ol/control";
import "ol/ol.css";
import { useInfoStore } from "../store/infoStore";
import { useSimulationStore } from "../store/simulationStore";

function getUnitColor(side, selected = false) {
  if (side === "RED") return selected ? "#ef4444" : "#dc2626";
  return selected ? "#3b82f6" : "#2563eb";
}

/**
 * 成熟系统风格的战争迷雾单位样式（参考 Command: Modern Operations / JTLS-GO）
 * - 正常敌方：实心彩色 + 白色描边（清晰可见）
 * - 幽灵敌方（刚离开探测）：半透明 + 虚线边框 + 位置滞后感
 * - 未识别接触（演示模式）：灰色问号圆圈
 */
function getEnemyStyle(isGhost = false) {
  if (isGhost) {
    return new Style({
      image: new CircleStyle({
        radius: 6,
        fill: new Fill({ color: "rgba(148, 163, 184, 0.35)" }),
        stroke: new Stroke({
          color: "#64748b",
          width: 1.5,
          lineDash: [4, 3]
        })
      })
    });
  }
  // 正常探测到的敌方
  return new Style({
    image: new CircleStyle({
      radius: 7,
      fill: new Fill({ color: "#475569" }),
      stroke: new Stroke({ color: "#e2e8f0", width: 1.5 })
    })
  });
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

  // 战争迷雾 + 未识别接触模式（阶段1增强）
  const commanderSide = useSimulationStore((s) => s.commanderSide);
  const fogOfWarEnabled = useInfoStore((s) => s.fogOfWarEnabled);
  const showUnidentifiedContacts = useInfoStore((s) => s.showUnidentifiedContacts);
  const lastDigest = useInfoStore((s) => s.lastDigest);
  const ghostEnemyUntil = useInfoStore((s) => s.ghostEnemyUntil);

  // 新接触脉冲动画支持
  const prevDetectedRef = useRef(new Set());
  const [pulsingIds, setPulsingIds] = React.useState(new Set());

  // 计算可见单位（战争迷雾 + 未识别接触模式）
  const visibleUnits = React.useMemo(() => {
    const own = units.filter((u) => u.side === commanderSide);
    const detIds = new Set((lastDigest?.detections || []).map((d) => d.targetId));
    const now = Date.now();
    const enemies = units.filter((u) => u.side !== commanderSide);

    const result = [];

    if (showUnidentifiedContacts) {
      // 未识别接触模式：显示全部敌方，但未探测的显示为“?”图标
      enemies.forEach((e) => {
        const isDetected = detIds.has(e.id);
        const isGhost = !isDetected && (ghostEnemyUntil[e.id] ?? 0) > now;
        result.push({
          ...e,
          _isGhost: isGhost,
          _isDetected: isDetected,
          _isUnidentified: !isDetected
        });
      });
    } else if (fogOfWarEnabled) {
      // 传统迷雾：只显示己方 + 已探测/幽灵敌方
      enemies.forEach((e) => {
        const isDetected = detIds.has(e.id);
        const isGhost = !isDetected && (ghostEnemyUntil[e.id] ?? 0) > now;
        if (isDetected || isGhost) {
          result.push({ ...e, _isGhost: isGhost, _isDetected: isDetected, _isUnidentified: false });
        }
      });
    } else {
      // 无迷雾：显示全部
      enemies.forEach((e) => {
        result.push({ ...e, _isGhost: false, _isDetected: detIds.has(e.id), _isUnidentified: false });
      });
    }

    return [...own, ...result];
  }, [units, commanderSide, fogOfWarEnabled, showUnidentifiedContacts, lastDigest, ghostEnemyUntil]);

  // 新接触脉冲检测（每次 detections 变化时触发 2.2s 高亮）
  React.useEffect(() => {
    const current = new Set((lastDigest?.detections || []).map((d) => d.targetId));
    const prev = prevDetectedRef.current;
    const newly = new Set([...current].filter((id) => !prev.has(id)));

    if (newly.size > 0) {
      setPulsingIds(new Set([...pulsingIds, ...newly]));
      // 2.2 秒后清除脉冲
      setTimeout(() => {
        setPulsingIds((old) => {
          const next = new Set(old);
          newly.forEach((id) => next.delete(id));
          return next;
        });
      }, 2200);
    }
    prevDetectedRef.current = current;
  }, [lastDigest?.detections]);

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

    visibleUnits.forEach((u) => {
      const lon = u.longitude;
      const lat = u.latitude;
      if (lon == null || lat == null) return;

      const feature = new Feature({ geometry: new Point(fromLonLat([lon, lat])) });

      if (u.side === commanderSide) {
        // 己方单位：正常实心
        feature.setStyle(
          new Style({
            image: new CircleStyle({
              radius: 7,
              fill: new Fill({ color: getUnitColor(u.side) }),
              stroke: new Stroke({ color: "#fff", width: 1.5 })
            })
          })
        );
      } else {
        // 敌方单位：未识别接触 / 正常 / 幽灵 三种样式
        const isUnidentified = u._isUnidentified;
        const isGhost = u._isGhost;
        const isPulsing = pulsingIds.has(u.id);

        if (isUnidentified) {
          // 未识别接触：灰色“?”（震撼的未知海域效果）
          feature.setStyle(
            new Style({
              image: new CircleStyle({
                radius: isPulsing ? 9 : 7,
                fill: new Fill({ color: "rgba(148, 163, 184, 0.9)" }),
                stroke: new Stroke({ color: "#64748b", width: 1.5 })
              }),
              text: new Text({
                text: "?",
                offsetY: 0,
                font: "bold 11px sans-serif",
                fill: new Fill({ color: "#1e293b" })
              })
            })
          );
        } else {
          const baseStyle = getEnemyStyle(isGhost);

          if (isGhost) {
            feature.setStyle([
              baseStyle,
              new Style({
                text: new Text({
                  text: "已失联",
                  offsetY: -18,
                  font: "10px sans-serif",
                  fill: new Fill({ color: "#94a3b8" }),
                  stroke: new Stroke({ color: "#0f172a", width: 2 })
                })
              })
            ]);
          } else if (isPulsing) {
            // 新探测到的高亮脉冲（外发光效果）
            feature.setStyle([
              new Style({
                image: new CircleStyle({
                  radius: 12,
                  fill: new Fill({ color: "rgba(163, 163, 172, 0.25)" })
                })
              }),
              new Style({
                image: new CircleStyle({
                  radius: 8,
                  fill: new Fill({ color: "#475569" }),
                  stroke: new Stroke({ color: "#e0f2fe", width: 2.5 })
                })
              })
            ]);
          } else {
            feature.setStyle(baseStyle);
          }
        }
      }

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
  }, [visibleUnits, objectives, planRoutes, flashToken]);

  function resetView() {
    mapRef.current?.getView().animate({ center: fromLonLat(center), zoom, duration: 280 });
  }

  function fitToData() {
    if (!mapRef.current || (!visibleUnits.length && !objectives.length)) {
      resetView();
      return;
    }
    const coords = [];
    visibleUnits.forEach((u) => {
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
