import React, { useEffect, useImperativeHandle, useLayoutEffect, useRef, useState, forwardRef } from "react";
import {
  AimOutlined,
  BorderOutlined,
  CompassOutlined,
  ExpandOutlined,
  FlagOutlined,
  SaveOutlined
} from "@ant-design/icons";
import { Button, Card, Space, Spin, Tooltip, message } from "antd";
import Map from "ol/Map";
import View from "ol/View";
import { createBasemapTileLayer } from "./basemapLayer";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Feature from "ol/Feature";
import Point from "ol/geom/Point";
import LineString from "ol/geom/LineString";
import { fromLonLat, toLonLat } from "ol/proj";
import { Fill, Stroke, Style, Circle as CircleStyle } from "ol/style";
import { defaults as defaultControls, FullScreen } from "ol/control";
import DragBox from "ol/interaction/DragBox";
import { platformModifierKeyOnly } from "ol/events/condition";
import { useDrop } from "react-dnd";
import { httpJson } from "../../lib/api";
import { UNIT_TYPE_LABELS, UNIT_TEMPLATES, addUnitToList } from "./deployMeta";
import "ol/ol.css";

function getUnitColor(side, selected = false) {
  if (side === "RED") return selected ? "#ef4444" : "#dc2626";
  return selected ? "#3b82f6" : "#2563eb";
}

const MapWorkbench = forwardRef(function MapWorkbench(
  {
    commanderSide,
    units,
    objectives,
    onUnitsChange,
    onObjectivesChange,
    onWillMutate,
    pendingObjective,
    onPendingObjectiveChange
  },
  ref
) {
  const objectivesRef = useRef(objectives);
  const unitsRef = useRef(units);
  const pendingObjectiveRef = useRef(pendingObjective);
  const routeDraftRef = useRef({ enabled: false, targetIds: [], waypoints: [] });
  const selectedEntitiesRef = useRef([]);
  const [selectedEntity, setSelectedEntity] = useState(null);
  const [selectedEntities, setSelectedEntities] = useState([]);
  const [menu, setMenu] = useState({ visible: false, x: 0, y: 0, payload: null });
  const [routes, setRoutes] = useState([]);
  const [routeDraft, setRouteDraft] = useState({ enabled: false, targetIds: [], waypoints: [] });
  const [saving, setSaving] = useState(false);
  const [loadingMap, setLoadingMap] = useState(true);
  const [mapError, setMapError] = useState("");
  const mapRootRef = useRef(null);
  const mapRef = useRef(null);
  const unitLayerRef = useRef(null);
  const objectiveLayerRef = useRef(null);
  const lineLayerRef = useRef(null);
  const dragBoxRef = useRef(null);

  const [dropState, drop] = useDrop(
    () => ({
      accept: "UNIT_TEMPLATE",
      drop: (item, monitor) => {
        const clientOffset = monitor.getClientOffset();
        if (!clientOffset || !mapRootRef.current || !mapRef.current) {
          message.warning("地图未就绪，请稍后重试");
          return;
        }
        const tpl = UNIT_TEMPLATES.find((t) => t.type === item.unitType);
        const deployed = unitsRef.current.filter((u) => u.type === item.unitType).length;
        if (tpl && deployed >= tpl.total) {
          message.error(`${UNIT_TYPE_LABELS[item.unitType] || item.unitType} 已达编制上限，无法继续部署`);
          return;
        }
        const rect = mapRootRef.current.getBoundingClientRect();
        const pixel = [clientOffset.x - rect.left, clientOffset.y - rect.top];
        const coord = mapRef.current.getCoordinateFromPixel(pixel);
        if (!coord) {
          message.error("无法解析落点坐标，请拖放到地图区域内");
          return;
        }
        const [longitude, latitude] = toLonLat(coord).map((x) => Number(x.toFixed(6)));
        onWillMutate?.();
        onUnitsChange(
          addUnitToList(unitsRef.current, commanderSide, item.unitType, { longitude, latitude, mission: "拖拽部署" })
        );
        message.success(`${UNIT_TYPE_LABELS[item.unitType] || item.unitType} 已部署至地图`);
      },
      collect: (monitor) => ({
        isOver: monitor.isOver()
      })
    }),
    [commanderSide, onUnitsChange, onWillMutate]
  );

  useEffect(() => {
    objectivesRef.current = objectives;
  }, [objectives]);

  useEffect(() => {
    unitsRef.current = units;
  }, [units]);

  useEffect(() => {
    pendingObjectiveRef.current = pendingObjective;
  }, [pendingObjective]);

  useEffect(() => {
    routeDraftRef.current = routeDraft;
  }, [routeDraft]);

  useEffect(() => {
    selectedEntitiesRef.current = selectedEntities;
  }, [selectedEntities]);

  useEffect(() => {
    if (!units.length && !objectives.length) {
      setRoutes([]);
      setRouteDraft({ enabled: false, targetIds: [], waypoints: [] });
      setSelectedEntity(null);
      setSelectedEntities([]);
    }
  }, [units.length, objectives.length]);

  function embedRouteMetadata(description, routeData) {
    const marker = "[ROUTE_META]";
    const clean = String(description || "").split(marker)[0].trim();
    return `${clean}\n${marker}${JSON.stringify(routeData)}`;
  }

  async function saveDeployment() {
    if (!units.length && !objectives.length) {
      message.warning("当前无部署数据可保存");
      return;
    }
    setSaving(true);
    try {
      const active = await httpJson("/combat/scenario-data/active");
      const scenarioId = active?.activeScenarioId;
      if (!scenarioId) {
        message.warning("请先激活想定后再保存部署");
        return;
      }
      const scenario = await httpJson(`/combat/scenario-data/${encodeURIComponent(scenarioId)}`);
      const payload = {
        ...scenario,
        id: scenarioId,
        units,
        objectives: objectives.map((o) => ({
          ...o,
          description: embedRouteMetadata(o.description, routes.filter((r) => r.targetIds.includes(o.id)))
        })),
        saveType: scenario.saveType || "TEMPLATE"
      };
      await httpJson("/combat/scenario-data/save", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      message.success("部署已持久化到后端想定");
    } catch (error) {
      message.error(error?.message || "部署保存失败");
    } finally {
      setSaving(false);
    }
  }

  useImperativeHandle(ref, () => ({
    saveDeployment,
    flyToLonLat: (longitude, latitude, zoom = 10) => {
      if (!mapRef.current) return;
      mapRef.current.getView().animate({
        center: fromLonLat([longitude, latitude]),
        zoom,
        duration: 300
      });
    }
  }));

  function initializeMap() {
    if (!mapRootRef.current || mapRef.current) return;
    setLoadingMap(true);
    setMapError("");
    try {
      const baseLayer = createBasemapTileLayer();

      unitLayerRef.current = new VectorLayer({ source: new VectorSource() });
      objectiveLayerRef.current = new VectorLayer({ source: new VectorSource() });
      lineLayerRef.current = new VectorLayer({ source: new VectorSource() });

      mapRef.current = new Map({
        target: mapRootRef.current,
        controls: defaultControls().extend([new FullScreen()]),
        layers: [baseLayer, lineLayerRef.current, objectiveLayerRef.current, unitLayerRef.current],
        view: new View({
          center: fromLonLat([121.5, 22.8]),
          zoom: 8
        })
      });

      dragBoxRef.current = new DragBox({ condition: platformModifierKeyOnly });
      mapRef.current.addInteraction(dragBoxRef.current);

      dragBoxRef.current.on("boxend", () => {
        const extent = dragBoxRef.current.getGeometry().getExtent();
        const picked = [];
        unitLayerRef.current.getSource().forEachFeatureIntersectingExtent(extent, (feature) => {
          picked.push(feature);
        });
        objectiveLayerRef.current.getSource().forEachFeatureIntersectingExtent(extent, (feature) => {
          picked.push(feature);
        });
        if (picked.length) {
          const payloads = picked.map((f) => f.get("payload")).filter(Boolean);
          const first = payloads[0];
          setSelectedEntity(first || null);
          setSelectedEntities(payloads);
          message.info(`已框选 ${picked.length} 个要素`);
        }
      });

      mapRef.current.on("singleclick", (evt) => {
        setMenu((prev) => ({ ...prev, visible: false }));
        if (routeDraftRef.current.enabled) {
          const [longitude, latitude] = toLonLat(evt.coordinate).map((x) => Number(x.toFixed(6)));
          setRouteDraft((prev) => ({ ...prev, waypoints: [...prev.waypoints, { longitude, latitude }] }));
          return;
        }
        const hit = mapRef.current.forEachFeatureAtPixel(evt.pixel, (feature) => feature);
        if (hit) {
          const payload = hit.get("payload") || null;
          setSelectedEntity(payload);
          setSelectedEntities(payload ? [payload] : []);
          return;
        }
        const [lon, latitude] = toLonLat(evt.coordinate).map((x) => Number(x.toFixed(6)));
        const currentObjective = pendingObjectiveRef.current;
        if (!currentObjective.name) return;
        onWillMutate?.();
        onObjectivesChange([
          ...objectivesRef.current,
          {
            id: `OBJ-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
            ...currentObjective,
            side: commanderSide === "RED" ? "BLUE" : "RED",
            latitude,
            longitude,
            priority: 8
          }
        ]);
        onPendingObjectiveChange?.((s) => ({ ...s, name: "" }));
        message.success(`目标「${currentObjective.name}」已标绘`);
      });

      mapRootRef.current.addEventListener("contextmenu", onContextMenu);

      const ro = new ResizeObserver(() => {
        mapRef.current?.updateSize();
      });
      ro.observe(mapRootRef.current);
      mapRootRef.current._ro = ro;

      setTimeout(() => {
        setLoadingMap(false);
      }, 200);
    } catch (error) {
      setMapError(error?.message || "地图加载失败");
      setLoadingMap(false);
    }
  }

  useEffect(() => {
    initializeMap();
    return () => {
      const root = mapRootRef.current;
      if (root?._ro) {
        root._ro.disconnect();
        delete root._ro;
      }
      if (root) {
        root.removeEventListener("contextmenu", onContextMenu);
      }
      if (mapRef.current) {
        mapRef.current.setTarget(undefined);
        mapRef.current = null;
      }
    };
  }, []);

  /** 地图容器曾用 display:none 导致 OL 以 0×0 初始化；加载结束后必须在布局提交后再 updateSize */
  useLayoutEffect(() => {
    if (loadingMap || mapError || !mapRef.current) return;
    const id = requestAnimationFrame(() => {
      mapRef.current?.updateSize();
      requestAnimationFrame(() => {
        mapRef.current?.updateSize();
      });
    });
    return () => cancelAnimationFrame(id);
  }, [loadingMap, mapError]);

  function onContextMenu(evt) {
    evt.preventDefault();
    if (!mapRef.current) return;
    const rect = mapRootRef.current.getBoundingClientRect();
    const pixel = [evt.clientX - rect.left, evt.clientY - rect.top];
    const feature = mapRef.current.forEachFeatureAtPixel(pixel, (f) => f);
    const payload = feature?.get("payload");
    if (payload) {
      const selectedIds = new Set(selectedEntitiesRef.current.map((x) => x.id));
      if (!selectedIds.has(payload.id)) {
        setSelectedEntities([payload]);
        setSelectedEntity(payload);
      }
    }
    if (!payload && !selectedEntitiesRef.current.length) return;
    setMenu({ visible: true, x: evt.clientX, y: evt.clientY, payload });
  }

  useEffect(() => {
    if (!unitLayerRef.current || !objectiveLayerRef.current || !lineLayerRef.current) return;
    const unitSource = unitLayerRef.current.getSource();
    const objSource = objectiveLayerRef.current.getSource();
    const lineSource = lineLayerRef.current.getSource();
    unitSource.clear();
    objSource.clear();
    lineSource.clear();

    units.forEach((u) => {
      const feature = new Feature({ geometry: new Point(fromLonLat([u.longitude, u.latitude])) });
      feature.set("payload", { kind: "unit", ...u });
      feature.setStyle(
        new Style({
          image: new CircleStyle({
            radius: 7,
            fill: new Fill({ color: getUnitColor(u.side, selectedEntity?.id === u.id) }),
            stroke: new Stroke({ color: "#fff", width: 1 })
          })
        })
      );
      unitSource.addFeature(feature);
    });

    objectives.forEach((o) => {
      const feature = new Feature({ geometry: new Point(fromLonLat([o.longitude, o.latitude])) });
      feature.set("payload", { kind: "objective", ...o });
      feature.setStyle(
        new Style({
          image: new CircleStyle({
            radius: o.type === "DEFEND" ? 8 : 7,
            fill: new Fill({ color: o.type === "DESTROY" ? "#dc2626" : o.type === "CAPTURE" ? "#f59e0b" : "#22c55e" }),
            stroke: new Stroke({ color: "#fff", width: 1 })
          })
        })
      );
      objSource.addFeature(feature);
    });

    if (units.length > 1) {
      const coordinates = units.map((x) => fromLonLat([x.longitude, x.latitude]));
      const defenseLine = new Feature({ geometry: new LineString(coordinates) });
      defenseLine.setStyle(new Style({ stroke: new Stroke({ color: "#60a5fa", width: 2, lineDash: [8, 6] }) }));
      lineSource.addFeature(defenseLine);
    }
    routes.forEach((r) => {
      if (!r.waypoints?.length) return;
      r.targetIds.forEach((id) => {
        const sourceUnit = units.find((u) => u.id === id);
        const sourceObj = objectives.find((o) => o.id === id);
        const src = sourceUnit || sourceObj;
        if (!src) return;
        const coordinates = [[src.longitude, src.latitude], ...r.waypoints.map((w) => [w.longitude, w.latitude])].map((x) =>
          fromLonLat(x)
        );
        const routeLine = new Feature({ geometry: new LineString(coordinates) });
        routeLine.setStyle(new Style({ stroke: new Stroke({ color: "#22d3ee", width: 2 }) }));
        lineSource.addFeature(routeLine);
      });
    });
    mapRef.current?.updateSize();
  }, [units, objectives, selectedEntity, routes]);

  const selectedCount = selectedEntities.length;

  function retryMapLoad() {
    setMapError("");
    const root = mapRootRef.current;
    if (root?._ro) {
      root._ro.disconnect();
      delete root._ro;
    }
    if (mapRef.current) {
      mapRef.current.setTarget(undefined);
      mapRef.current = null;
    }
    initializeMap();
  }

  function handleMenuDelete() {
    const targetIds = selectedEntities.length
      ? selectedEntities.map((x) => x.id)
      : menu.payload
        ? [menu.payload.id]
        : [];
    if (!targetIds.length) return;
    onWillMutate?.();
    onUnitsChange(units.filter((u) => !targetIds.includes(u.id)));
    onObjectivesChange(objectives.filter((o) => !targetIds.includes(o.id)));
    setRoutes((prev) => prev.filter((r) => !r.targetIds.some((id) => targetIds.includes(id))));
    setSelectedEntities([]);
    setSelectedEntity(null);
    setMenu({ visible: false, x: 0, y: 0, payload: null });
    message.success(`已删除 ${targetIds.length} 个要素`);
  }

  function handleSetRoute() {
    const targetIds = selectedEntities.length
      ? selectedEntities.map((x) => x.id)
      : menu.payload
        ? [menu.payload.id]
        : [];
    if (!targetIds.length) {
      message.warning("请先选中要设置路线的要素");
      return;
    }
    setRouteDraft({ enabled: true, targetIds, waypoints: [] });
    setMenu({ visible: false, x: 0, y: 0, payload: null });
    message.info("路线绘制模式已开启：在地图上点击添加路径点");
  }

  function cancelRouteDraft() {
    setRouteDraft({ enabled: false, targetIds: [], waypoints: [] });
  }

  function finishRouteDraft() {
    if (routeDraft.waypoints.length < 2) {
      message.warning("至少需要 2 个路径点");
      return;
    }
    const nextRoutes = [
      ...routes.filter((r) => !r.targetIds.some((id) => routeDraft.targetIds.includes(id))),
      {
        id: `route-${Date.now()}`,
        targetIds: routeDraft.targetIds,
        waypoints: routeDraft.waypoints
      }
    ];
    setRoutes(nextRoutes);

    const routeText = `路线(${routeDraft.waypoints.map((w) => `${w.longitude.toFixed(3)},${w.latitude.toFixed(3)}`).join(" -> ")})`;
    onWillMutate?.();
    onUnitsChange(units.map((u) => (routeDraft.targetIds.includes(u.id) ? { ...u, mission: routeText } : u)));
    onObjectivesChange(objectives.map((o) => (routeDraft.targetIds.includes(o.id) ? { ...o, description: routeText } : o)));

    setRouteDraft({ enabled: false, targetIds: [], waypoints: [] });
    message.success(`已为 ${routeDraft.targetIds.length} 个要素设置路线`);
  }

  function resetView() {
    if (!mapRef.current) return;
    mapRef.current.getView().animate({ center: fromLonLat([121.5, 22.8]), zoom: 8, duration: 300 });
  }

  function toggleUnitLayer() {
    const next = !(unitLayerRef.current?.getVisible() ?? true);
    unitLayerRef.current?.setVisible(next);
  }
  function toggleObjectiveLayer() {
    const next = !(objectiveLayerRef.current?.getVisible() ?? true);
    objectiveLayerRef.current?.setVisible(next);
  }
  function toggleLineLayer() {
    const next = !(lineLayerRef.current?.getVisible() ?? true);
    lineLayerRef.current?.setVisible(next);
  }

  return (
    <Card
      className="commander-map-card"
      title="战场主视图 · OpenLayers"
      size="small"
      styles={{ body: { flex: 1, minHeight: 0, display: "flex", flexDirection: "column", padding: 10 } }}
      extra={
        <Space size={6}>
          <Tooltip title="显示/隐藏单位层">
            <Button size="small" icon={<CompassOutlined />} onClick={toggleUnitLayer} />
          </Tooltip>
          <Tooltip title="显示/隐藏目标层">
            <Button size="small" icon={<FlagOutlined />} onClick={toggleObjectiveLayer} />
          </Tooltip>
          <Tooltip title="显示/隐藏路线层">
            <Button size="small" icon={<AimOutlined />} onClick={toggleLineLayer} />
          </Tooltip>
          <Tooltip title="重置视角">
            <Button size="small" icon={<BorderOutlined />} onClick={resetView} />
          </Tooltip>
          <Tooltip title="保存至当前激活想定">
            <Button size="small" type="primary" icon={<SaveOutlined />} loading={saving} onClick={saveDeployment} />
          </Tooltip>
        </Space>
      }
    >
      <div style={{ position: "relative", flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
        <div
          ref={(node) => {
            mapRootRef.current = node;
            drop(node);
          }}
          className="commander-ol-target"
          style={{
            flex: 1,
            minHeight: 360,
            width: "100%",
            borderRadius: 10,
            overflow: "hidden",
            border: dropState.isOver ? "2px dashed #38bdf8" : "1px solid #334155",
            visibility: mapError ? "hidden" : "visible"
          }}
        />
        {loadingMap ? (
          <div
            style={{
              position: "absolute",
              inset: 0,
              display: "grid",
              placeItems: "center",
              background: "rgba(15,23,42,0.82)",
              borderRadius: 10,
              zIndex: 3,
              pointerEvents: "none"
            }}
          >
            <Spin tip="地图载入中..." />
          </div>
        ) : null}
        {mapError ? (
          <div
            style={{
              position: "absolute",
              inset: 0,
              display: "grid",
              placeItems: "center",
              background: "rgba(15,23,42,0.94)",
              borderRadius: 10,
              zIndex: 4
            }}
          >
            <Space direction="vertical" align="center">
              <div>{mapError}</div>
              <Button onClick={retryMapLoad}>重试</Button>
            </Space>
          </div>
        ) : null}
        {selectedEntity ? (
          <Card
            size="small"
            style={{ position: "absolute", right: 12, bottom: 12, width: 260, background: "rgba(15,23,42,0.95)" }}
            title="要素详情"
          >
            <div className="kv">
              <span>类型</span>
              <strong>{selectedEntity.kind === "unit" ? "兵力单位" : "战役目标"}</strong>
            </div>
            <div className="kv">
              <span>名称</span>
              <strong>
                {selectedEntity.name ||
                  (selectedEntity.kind === "unit"
                    ? UNIT_TYPE_LABELS[selectedEntity.type] || selectedEntity.type
                    : selectedEntity.type)}
              </strong>
            </div>
            {selectedEntity.kind === "unit" && selectedEntity.type ? (
              <div className="kv">
                <span>兵种</span>
                <strong>{UNIT_TYPE_LABELS[selectedEntity.type] || selectedEntity.type}</strong>
              </div>
            ) : null}
            <div className="kv">
              <span>阵营</span>
              <strong>{selectedEntity.side || "-"}</strong>
            </div>
            <div className="kv">
              <span>经纬度</span>
              <strong>
                {selectedEntity.longitude?.toFixed?.(3)},{selectedEntity.latitude?.toFixed?.(3)}
              </strong>
            </div>
          </Card>
        ) : null}
        {routeDraft.enabled ? (
          <Card
            size="small"
            style={{ position: "absolute", left: 12, top: 12, width: 300, background: "rgba(15,23,42,0.96)" }}
            title={`路线绘制中（目标 ${routeDraft.targetIds.length}）`}
          >
            <div className="muted" style={{ marginBottom: 8 }}>
              已添加路径点：{routeDraft.waypoints.length}
            </div>
            <Space>
              <Button type="primary" size="small" onClick={finishRouteDraft}>
                完成路线
              </Button>
              <Button size="small" onClick={cancelRouteDraft}>
                取消
              </Button>
            </Space>
          </Card>
        ) : null}
        {menu.visible ? (
          <div
            style={{
              position: "fixed",
              left: menu.x,
              top: menu.y,
              zIndex: 1500,
              border: "1px solid #334155",
              borderRadius: 8,
              background: "#0f172a",
              padding: 6,
              display: "grid",
              gap: 6
            }}
          >
            <Button size="small" danger onClick={handleMenuDelete}>
              批量删除（{selectedCount || 1}）
            </Button>
            <Button size="small" onClick={handleSetRoute} icon={<ExpandOutlined />}>
              批量设路线（{selectedCount || 1}）
            </Button>
          </div>
        ) : null}
      </div>
    </Card>
  );
});

export default MapWorkbench;
