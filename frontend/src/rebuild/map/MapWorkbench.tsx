import React, { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import {
  AimOutlined,
  BorderOutlined,
  CompassOutlined,
  ExpandOutlined,
  FlagOutlined,
  RedoOutlined,
  RollbackOutlined,
  SaveOutlined
} from "@ant-design/icons";
import { Button, Card, Descriptions, Modal, Segmented, Space, Spin, Switch, Tooltip, Typography, message } from "antd";
import Map from "ol/Map";
import View from "ol/View";
import TileLayer from "ol/layer/Tile";
import XYZ from "ol/source/XYZ";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Feature from "ol/Feature";
import Point from "ol/geom/Point";
import LineString from "ol/geom/LineString";
import Polygon from "ol/geom/Polygon";
import { fromLonLat, toLonLat } from "ol/proj";
import { Fill, Stroke, Style, Circle as CircleStyle } from "ol/style";
import { getUnitMarkerStyle } from "./unitMapIcons";
import { defaults as defaultControls, FullScreen } from "ol/control";
import DragBox from "ol/interaction/DragBox";
import Translate from "ol/interaction/Translate";
import DoubleClickZoom from "ol/interaction/DoubleClickZoom";
import { platformModifierKeyOnly } from "ol/events/condition";
import { useDrop } from "react-dnd";
import { httpJson } from "../../lib/api";
import { getUnitTypeLabel } from "../force/forceCatalog";
import type { DeployedUnit, ForceCamp } from "../force/forceTypes";
import { useDeploymentStore } from "../../store/deploymentStore";
import { useInfoStore } from "../../store/infoStore";
import { buildRoundDigest, effectiveDetectorRangeNm, resolveSensorModel } from "../info/infoWarfareEngine";
import { bearingDeg, circlePolygon3857, sectorPolygon3857 } from "../info/infoGeometry";
import type { BattlePerspective } from "../info/infoTypes";
import { remainForTemplate, getTemplateByTypeAndCamp } from "../force/forceCatalog";
import { ensureCanvas2dWillReadFrequentlyPatch } from "./canvas2dContextPatch";
import "ol/ol.css";

const { Text } = Typography;

type ObjectivePayload = Record<string, unknown> & { kind?: string; id?: string };

export interface MapWorkbenchProps {
  commanderSide: ForceCamp;
  objectives: ObjectivePayload[];
  onObjectivesChange: (next: ObjectivePayload[]) => void;
  pendingObjective: { name: string; type: string };
  onPendingObjectiveChange: (fn: (s: { name: string; type: string }) => { name: string; type: string }) => void;
}

export type MapWorkbenchHandle = {
  saveDeployment: () => Promise<void>;
  flyToLonLat: (longitude: number, latitude: number, zoom?: number) => void;
  getCenterLonLat: () => [number, number] | null;
};

const MapWorkbench = forwardRef<MapWorkbenchHandle, MapWorkbenchProps>(function MapWorkbench(
  { commanderSide, objectives, onObjectivesChange, pendingObjective, onPendingObjectiveChange },
  ref
) {
  const deployedUnits = useDeploymentStore((s) => s.deployedUnits);
  const deployAt = useDeploymentStore((s) => s.deployAt);
  const removeUnitIds = useDeploymentStore((s) => s.removeUnitIds);
  const moveUnit = useDeploymentStore((s) => s.moveUnit);
  const clearAllUnits = useDeploymentStore((s) => s.clearAll);
  const undo = useDeploymentStore((s) => s.undo);
  const redo = useDeploymentStore((s) => s.redo);
  const canUndo = useDeploymentStore((s) => s.past.length > 0);
  const canRedo = useDeploymentStore((s) => s.future.length > 0);
  const lastSelectedUnitType = useDeploymentStore((s) => s.lastSelectedUnitType);

  const perspective = useInfoStore((s) => s.perspective);
  const setPerspective = useInfoStore((s) => s.setPerspective);
  const fogOfWarEnabled = useInfoStore((s) => s.fogOfWarEnabled);
  const setFogOfWarEnabled = useInfoStore((s) => s.setFogOfWarEnabled);
  const environment = useInfoStore((s) => s.environment);
  const unitInfoCombat = useInfoStore((s) => s.unitInfoCombat);
  const ghostEnemyUntil = useInfoStore((s) => s.ghostEnemyUntil);
  const setLastDigest = useInfoStore((s) => s.setLastDigest);
  const syncGhostContacts = useInfoStore((s) => s.syncGhostContacts);
  const ensureUnitsHaveDefaults = useInfoStore((s) => s.ensureUnitsHaveDefaults);

  const digest = useMemo(
    () => buildRoundDigest(0, commanderSide, deployedUnits, environment, unitInfoCombat),
    [commanderSide, deployedUnits, environment, unitInfoCombat]
  );

  const unitsToDraw = useMemo(() => {
    if (perspective === "GOD") return deployedUnits;
    const own = deployedUnits.filter((u) => u.side === commanderSide);
    if (!fogOfWarEnabled) return deployedUnits;
    const det = new Set(digest.detections.map((d) => d.targetId));
    const now = Date.now();
    const enemies = deployedUnits.filter((u) => u.side !== commanderSide);
    const visibleEnemies = enemies.filter((e) => det.has(e.id) || (ghostEnemyUntil[e.id] ?? 0) > now);
    return [...own, ...visibleEnemies];
  }, [deployedUnits, commanderSide, perspective, fogOfWarEnabled, digest.detections, ghostEnemyUntil]);

  const objectivesRef = useRef(objectives);
  const unitsRef = useRef(deployedUnits);
  const pendingObjectiveRef = useRef(pendingObjective);
  const routeDraftRef = useRef({ enabled: false, targetIds: [] as string[], waypoints: [] as { longitude: number; latitude: number }[] });
  const selectedEntitiesRef = useRef<unknown[]>([]);
  const commanderSideRef = useRef(commanderSide);
  const perspectiveRef = useRef<BattlePerspective>("COMMAND");

  const mapRootRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<Map | null>(null);
  const unitLayerRef = useRef<VectorLayer<VectorSource> | null>(null);
  const objectiveLayerRef = useRef<VectorLayer<VectorSource> | null>(null);
  const lineLayerRef = useRef<VectorLayer<VectorSource> | null>(null);
  const jamLayerRef = useRef<VectorLayer<VectorSource> | null>(null);
  const datalinkLayerRef = useRef<VectorLayer<VectorSource> | null>(null);
  const detectionLayerRef = useRef<VectorLayer<VectorSource> | null>(null);
  const dragBoxRef = useRef<DragBox | null>(null);
  const translateRef = useRef<Translate | null>(null);

  const [selectedEntity, setSelectedEntity] = useState<Record<string, unknown> | null>(null);
  const [selectedEntities, setSelectedEntities] = useState<Record<string, unknown>[]>([]);
  const [detailUnit, setDetailUnit] = useState<DeployedUnit | null>(null);
  const [menu, setMenu] = useState({ visible: false, x: 0, y: 0, payload: null as Record<string, unknown> | null });
  const [routes, setRoutes] = useState<
    { id: string; targetIds: string[]; waypoints: { longitude: number; latitude: number }[] }[]
  >([]);
  const [routeDraft, setRouteDraft] = useState({
    enabled: false,
    targetIds: [] as string[],
    waypoints: [] as { longitude: number; latitude: number }[]
  });
  const [saving, setSaving] = useState(false);
  const [loadingMap, setLoadingMap] = useState(true);
  const [mapError, setMapError] = useState("");

  const [dropState, drop] = useDrop(
    () => ({
      accept: "UNIT_TEMPLATE",
      drop: (item: { unitType: string; camp: ForceCamp }, monitor) => {
        const clientOffset = monitor.getClientOffset();
        if (!clientOffset || !mapRootRef.current || !mapRef.current) {
          message.warning("地图未就绪，请稍后重试");
          return;
        }
        if (item.camp !== commanderSideRef.current) {
          message.warning("仅可部署当前指挥阵营兵力");
          return;
        }
        const tpl = getTemplateByTypeAndCamp(item.unitType, item.camp);
        const current = useDeploymentStore.getState().deployedUnits;
        if (!tpl || remainForTemplate(current, tpl) <= 0) {
          message.error(`${getUnitTypeLabel(item.unitType)} 已无剩余编制`);
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
        const ok = deployAt(item.unitType, item.camp, longitude, latitude, "拖拽部署");
        if (ok) message.success(`${getUnitTypeLabel(item.unitType)} 已部署至地图`);
      },
      collect: (monitor) => ({
        isOver: monitor.isOver()
      })
    }),
    [deployAt]
  );

  useEffect(() => {
    objectivesRef.current = objectives;
  }, [objectives]);

  useEffect(() => {
    unitsRef.current = deployedUnits;
  }, [deployedUnits]);

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
    commanderSideRef.current = commanderSide;
  }, [commanderSide]);

  useEffect(() => {
    perspectiveRef.current = perspective;
  }, [perspective]);

  useEffect(() => {
    ensureUnitsHaveDefaults(deployedUnits.map((u) => u.id));
  }, [deployedUnits, ensureUnitsHaveDefaults]);

  useEffect(() => {
    setLastDigest(digest);
  }, [digest, setLastDigest]);

  useEffect(() => {
    if (perspective === "GOD" || !fogOfWarEnabled) return;
    syncGhostContacts(digest.detections.map((d) => d.targetId));
  }, [digest.detections, perspective, fogOfWarEnabled, syncGhostContacts]);

  useEffect(() => {
    if (!deployedUnits.length && !objectives.length) {
      setRoutes([]);
      setRouteDraft({ enabled: false, targetIds: [], waypoints: [] });
      setSelectedEntity(null);
      setSelectedEntities([]);
      setDetailUnit(null);
    }
  }, [deployedUnits.length, objectives.length]);

  function embedRouteMetadata(description: string | undefined, routeData: unknown) {
    const marker = "[ROUTE_META]";
    const clean = String(description || "").split(marker)[0].trim();
    return `${clean}\n${marker}${JSON.stringify(routeData)}`;
  }

  const saveDeployment = useCallback(async () => {
    const units = useDeploymentStore.getState().deployedUnits;
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
          description: embedRouteMetadata(String(o.description ?? ""), routes.filter((r) => r.targetIds.includes(String(o.id))))
        })),
        saveType: scenario.saveType || "TEMPLATE"
      };
      await httpJson("/combat/scenario-data/save", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      message.success("部署已持久化到后端想定");
    } catch (error: unknown) {
      message.error(error instanceof Error ? error.message : "部署保存失败");
    } finally {
      setSaving(false);
    }
  }, [objectives, routes]);

  useImperativeHandle(ref, () => ({
    saveDeployment,
    flyToLonLat: (longitude, latitude, zoom = 10) => {
      if (!mapRef.current) return;
      mapRef.current.getView().animate({
        center: fromLonLat([longitude, latitude]),
        zoom,
        duration: 300
      });
    },
    getCenterLonLat: () => {
      if (!mapRef.current) return null;
      const c = mapRef.current.getView().getCenter();
      if (!c) return null;
      const [lon, lat] = toLonLat(c).map((x) => Number(x.toFixed(6)));
      return [lon, lat];
    }
  }));

  const attachTranslate = useCallback(() => {
    const map = mapRef.current;
    const layer = unitLayerRef.current;
    if (!map || !layer) return;
    if (translateRef.current) {
      map.removeInteraction(translateRef.current);
      translateRef.current = null;
    }
    const side = commanderSideRef.current;
    const translate = new Translate({
      layers: [layer],
      filter: (feature) => {
        const p = feature.get("payload") as { kind?: string; side?: string } | undefined;
        return Boolean(p && p.kind === "unit" && p.side === side);
      }
    });
    translate.on("translateend", (e) => {
      e.features.forEach((f) => {
        const p = f.get("payload") as DeployedUnit & { kind?: string };
        if (!p || p.kind !== "unit") return;
        const geom = f.getGeometry();
        if (!geom || geom.getType() !== "Point") return;
        const c = (geom as Point).getCoordinates();
        const [lon, lat] = toLonLat(c).map((x) => Number(Number(x).toFixed(6)));
        moveUnit(p.id, lon, lat, commanderSideRef.current);
      });
    });
    map.addInteraction(translate);
    translateRef.current = translate;
  }, [moveUnit]);

  function initializeMap() {
    if (!mapRootRef.current || mapRef.current) return;
    ensureCanvas2dWillReadFrequentlyPatch();
    setLoadingMap(true);
    setMapError("");
    try {
      const baseLayer = new TileLayer({
        source: new XYZ({
          url: "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        })
      });

      jamLayerRef.current = new VectorLayer({ source: new VectorSource(), zIndex: 4 });
      datalinkLayerRef.current = new VectorLayer({ source: new VectorSource(), zIndex: 5 });
      detectionLayerRef.current = new VectorLayer({ source: new VectorSource(), zIndex: 6 });
      lineLayerRef.current = new VectorLayer({ source: new VectorSource(), zIndex: 7 });
      objectiveLayerRef.current = new VectorLayer({ source: new VectorSource(), zIndex: 8 });
      unitLayerRef.current = new VectorLayer({ source: new VectorSource(), zIndex: 9 });

      mapRef.current = new Map({
        target: mapRootRef.current,
        controls: defaultControls().extend([new FullScreen()]),
        layers: [
          baseLayer,
          jamLayerRef.current,
          datalinkLayerRef.current,
          detectionLayerRef.current,
          lineLayerRef.current,
          objectiveLayerRef.current,
          unitLayerRef.current
        ],
        view: new View({
          center: fromLonLat([121.5, 22.8]),
          zoom: 8
        })
      });

      mapRef.current.getInteractions().forEach((ix) => {
        if (ix instanceof DoubleClickZoom) {
          mapRef.current?.removeInteraction(ix);
        }
      });

      dragBoxRef.current = new DragBox({ condition: platformModifierKeyOnly });
      mapRef.current.addInteraction(dragBoxRef.current);

      dragBoxRef.current.on("boxend", () => {
        const extent = dragBoxRef.current?.getGeometry()?.getExtent();
        if (!extent || !unitLayerRef.current || !objectiveLayerRef.current) return;
        const picked: Feature[] = [];
        unitLayerRef.current.getSource()?.forEachFeatureIntersectingExtent(extent, (feature) => {
          picked.push(feature);
        });
        objectiveLayerRef.current.getSource()?.forEachFeatureIntersectingExtent(extent, (feature) => {
          picked.push(feature);
        });
        if (picked.length) {
          const payloads = picked.map((f) => f.get("payload")).filter(Boolean) as Record<string, unknown>[];
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
        const hit = mapRef.current?.forEachFeatureAtPixel(evt.pixel, (feature) => feature);
        if (hit) {
          const payload = hit.get("payload") as Record<string, unknown> | undefined;
          if (payload?.kind === "unit") {
            setSelectedEntity(payload);
            setSelectedEntities(payload ? [payload] : []);
            setDetailUnit(payload as DeployedUnit);
            return;
          }
          setDetailUnit(null);
          setSelectedEntity(payload || null);
          setSelectedEntities(payload ? [payload] : []);
          return;
        }
        setDetailUnit(null);
        setSelectedEntity(null);
        setSelectedEntities([]);
        const [lon, latitude] = toLonLat(evt.coordinate).map((x) => Number(x.toFixed(6)));
        const currentObjective = pendingObjectiveRef.current;
        if (!currentObjective.name) return;
        onObjectivesChange([
          ...objectivesRef.current,
          {
            id: `OBJ-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
            ...currentObjective,
            side: commanderSideRef.current === "RED" ? "BLUE" : "RED",
            latitude,
            longitude: lon,
            priority: 8
          }
        ]);
        onPendingObjectiveChange((s) => ({ ...s, name: "" }));
        message.success(`目标「${currentObjective.name}」已标绘`);
      });

      mapRef.current.on("dblclick", (evt) => {
        evt.preventDefault();
        if (routeDraftRef.current.enabled) return;
        const hit = mapRef.current?.forEachFeatureAtPixel(evt.pixel, (feature) => feature);
        if (hit) return;
        const type = useDeploymentStore.getState().lastSelectedUnitType;
        if (!type) {
          message.info("请先在兵力面板单击选中一个兵种，再双击地图快捷部署");
          return;
        }
        const tpl = getTemplateByTypeAndCamp(type, commanderSideRef.current);
        const cur = useDeploymentStore.getState().deployedUnits;
        if (!tpl || remainForTemplate(cur, tpl) <= 0) {
          message.warning(`${getUnitTypeLabel(type)} 已无剩余编制`);
          return;
        }
        const [longitude, latitude] = toLonLat(evt.coordinate).map((x) => Number(x.toFixed(6)));
        const ok = deployAt(type, commanderSideRef.current, longitude, latitude, "双击快捷部署");
        if (ok) message.success(`${getUnitTypeLabel(type)} 已部署`);
      });

      mapRootRef.current.addEventListener("contextmenu", onContextMenu);

      const ro = new ResizeObserver(() => {
        mapRef.current?.updateSize();
      });
      ro.observe(mapRootRef.current);
      (mapRootRef.current as HTMLDivElement & { _ro?: ResizeObserver })._ro = ro;

      setTimeout(() => {
        setLoadingMap(false);
        mapRef.current?.updateSize();
        attachTranslate();
      }, 400);
    } catch (error: unknown) {
      setMapError(error instanceof Error ? error.message : "地图加载失败");
      setLoadingMap(false);
    }
  }

  useEffect(() => {
    initializeMap();
    return () => {
      const root = mapRootRef.current as (HTMLDivElement & { _ro?: ResizeObserver }) | null;
      if (root?._ro) {
        root._ro.disconnect();
        delete root._ro;
      }
      if (root) {
        root.removeEventListener("contextmenu", onContextMenu);
      }
      if (mapRef.current) {
        if (translateRef.current) {
          mapRef.current.removeInteraction(translateRef.current);
          translateRef.current = null;
        }
        mapRef.current.setTarget(undefined);
        mapRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 地图仅初始化一次
  }, []);

  useEffect(() => {
    if (mapRef.current && !loadingMap && !mapError) {
      attachTranslate();
    }
  }, [commanderSide, loadingMap, mapError, attachTranslate]);

  function onContextMenu(evt: MouseEvent) {
    evt.preventDefault();
    if (!mapRef.current || !mapRootRef.current) return;
    const rect = mapRootRef.current.getBoundingClientRect();
    const pixel = [evt.clientX - rect.left, evt.clientY - rect.top];
    const feature = mapRef.current.forEachFeatureAtPixel(pixel, (f) => f);
    const payload = feature?.get("payload") as Record<string, unknown> | undefined;
    if (payload?.kind === "unit" && payload.side !== commanderSideRef.current) {
      if (perspectiveRef.current !== "GOD") return;
    }
    if (payload) {
      const selectedIds = new Set(selectedEntitiesRef.current.map((x) => (x as { id?: string }).id));
      if (!selectedIds.has(payload.id as string)) {
        setSelectedEntities([payload]);
        setSelectedEntity(payload);
      }
    }
    if (!payload && !selectedEntitiesRef.current.length) return;
    setMenu({ visible: true, x: evt.clientX, y: evt.clientY, payload: payload || null });
  }

  useEffect(() => {
    if (!unitLayerRef.current || !objectiveLayerRef.current || !lineLayerRef.current) return;
    const unitSource = unitLayerRef.current.getSource();
    const objSource = objectiveLayerRef.current.getSource();
    const lineSource = lineLayerRef.current.getSource();
    if (!unitSource || !objSource || !lineSource) return;
    unitSource.clear();
    objSource.clear();
    lineSource.clear();

    unitsToDraw.forEach((u) => {
      const feature = new Feature({ geometry: new Point(fromLonLat([u.longitude, u.latitude])) });
      feature.set("payload", { kind: "unit", ...u });
      feature.setStyle(getUnitMarkerStyle(String(u.type), u.side, selectedEntity?.id === u.id));
      unitSource.addFeature(feature);
    });

    objectives.forEach((o) => {
      const olon = Number(o.longitude);
      const olat = Number(o.latitude);
      const feature = new Feature({ geometry: new Point(fromLonLat([olon, olat])) });
      feature.set("payload", { kind: "objective", ...o });
      const otype = String(o.type ?? "");
      feature.setStyle(
        new Style({
          image: new CircleStyle({
            radius: otype === "DEFEND" ? 8 : 7,
            fill: new Fill({
              color: otype === "DESTROY" ? "#dc2626" : otype === "CAPTURE" ? "#f59e0b" : "#22c55e"
            }),
            stroke: new Stroke({ color: "#fff", width: 1 })
          })
        })
      );
      objSource.addFeature(feature);
    });

    const ownVisibleLine = unitsToDraw.filter((x) => x.side === commanderSide);
    if (ownVisibleLine.length > 1) {
      const coordinates = ownVisibleLine.map((x) => fromLonLat([x.longitude, x.latitude]));
      const defenseLine = new Feature({ geometry: new LineString(coordinates) });
      defenseLine.setStyle(new Style({ stroke: new Stroke({ color: "#60a5fa", width: 2, lineDash: [8, 6] }) }));
      lineSource.addFeature(defenseLine);
    }
    routes.forEach((r) => {
      if (!r.waypoints?.length) return;
      r.targetIds.forEach((id) => {
        const sourceUnit = deployedUnits.find((u) => u.id === id);
        const sourceObj = objectives.find((o) => String(o.id) === id);
        const src = sourceUnit || sourceObj;
        if (!src) return;
        const sx = "longitude" in src ? Number(src.longitude) : 0;
        const sy = "latitude" in src ? Number(src.latitude) : 0;
        const coordinates = [[sx, sy], ...r.waypoints.map((w) => [w.longitude, w.latitude])].map((x) => fromLonLat(x));
        const routeLine = new Feature({ geometry: new LineString(coordinates) });
        routeLine.setStyle(new Style({ stroke: new Stroke({ color: "#22d3ee", width: 2 }) }));
        lineSource.addFeature(routeLine);
      });
    });
    mapRef.current?.updateSize();
  }, [deployedUnits, unitsToDraw, objectives, selectedEntity, routes, commanderSide]);

  useEffect(() => {
    if (!jamLayerRef.current || !datalinkLayerRef.current || !detectionLayerRef.current) return;
    const jamSrc = jamLayerRef.current.getSource();
    const dlSrc = datalinkLayerRef.current.getSource();
    const detSrc = detectionLayerRef.current.getSource();
    if (!jamSrc || !dlSrc || !detSrc) return;
    jamSrc.clear();
    dlSrc.clear();
    detSrc.clear();

    const { jammingZones, datalinkEdges } = digest;
    const mySide = commanderSide;

    jammingZones.forEach((z) => {
      const ring = circlePolygon3857(z.centerLon, z.centerLat, z.radiusNm);
      const f = new Feature({ geometry: new Polygon([ring]) });
      const isOwn = z.side === mySide;
      f.setStyle(
        new Style({
          fill: new Fill({
            color: isOwn ? "rgba(34, 211, 238, 0.12)" : "rgba(248, 113, 113, 0.14)"
          }),
          stroke: new Stroke({
            color: isOwn ? "#22d3ee" : "#f87171",
            width: 2,
            lineDash: [6, 5]
          })
        })
      );
      jamSrc.addFeature(f);
    });

    datalinkEdges.forEach((e) => {
      const a = deployedUnits.find((u) => u.id === e.fromUnitId);
      const b = deployedUnits.find((u) => u.id === e.toUnitId);
      if (!a || !b) return;
      const line = new Feature({
        geometry: new LineString([fromLonLat([a.longitude, a.latitude]), fromLonLat([b.longitude, b.latitude])])
      });
      const q = e.quality;
      line.setStyle(
        new Style({
          stroke: new Stroke({
            color: e.degradedByJamming ? `rgba(196, 181, 253, ${0.35 + q * 0.45})` : `rgba(167, 139, 250, ${0.45 + q * 0.5})`,
            width: 1.5 + q,
            lineDash: e.degradedByJamming ? [4, 4] : undefined
          })
        })
      );
      dlSrc.addFeature(line);
    });

    const own = deployedUnits.filter((u) => u.side === mySide);
    const enemies = deployedUnits.filter((u) => u.side !== mySide);
    const elon = enemies.length ? enemies.reduce((s, x) => s + x.longitude, 0) / enemies.length : mySide === "RED" ? 122.2 : 121.2;
    const elat = enemies.length ? enemies.reduce((s, x) => s + x.latitude, 0) / enemies.length : 22.8;

    own.forEach((u) => {
      const model = resolveSensorModel(u);
      if (!model) return;
      const rng = effectiveDetectorRangeNm(u, mySide, environment, unitInfoCombat, jammingZones);
      if (rng <= 0.5) return;
      let coords: number[][];
      if (model.sectorHalfAngleDeg >= 179) {
        coords = [circlePolygon3857(u.longitude, u.latitude, rng)];
      } else {
        const br = bearingDeg(u.longitude, u.latitude, elon, elat);
        coords = [sectorPolygon3857(u.longitude, u.latitude, br, model.sectorHalfAngleDeg, rng)];
      }
      const poly = new Feature({ geometry: new Polygon(coords) });
      poly.setStyle(
        new Style({
          fill: new Fill({ color: "rgba(56, 189, 248, 0.06)" }),
          stroke: new Stroke({ color: "rgba(56, 189, 248, 0.55)", width: 1.2, lineDash: [10, 6] })
        })
      );
      detSrc.addFeature(poly);
    });

    mapRef.current?.updateSize();
  }, [digest, deployedUnits, commanderSide, environment, unitInfoCombat]);

  const selectedCount = selectedEntities.length;

  function retryMapLoad() {
    const root = mapRootRef.current as (HTMLDivElement & { _ro?: ResizeObserver }) | null;
    if (root?._ro) {
      root._ro.disconnect();
      delete root._ro;
    }
    if (mapRef.current) {
      if (translateRef.current) {
        mapRef.current.removeInteraction(translateRef.current);
        translateRef.current = null;
      }
      mapRef.current.setTarget(undefined);
      mapRef.current = null;
    }
    jamLayerRef.current = null;
    datalinkLayerRef.current = null;
    detectionLayerRef.current = null;
    unitLayerRef.current = null;
    objectiveLayerRef.current = null;
    lineLayerRef.current = null;
    dragBoxRef.current = null;
    initializeMap();
  }

  function handleMenuDelete() {
    const targetIds = selectedEntities.length
      ? selectedEntities.map((x) => String((x as { id?: string }).id))
      : menu.payload
        ? [String(menu.payload.id)]
        : [];
    if (!targetIds.length) return;

    const unitIds = targetIds.filter((id) => {
      const u = deployedUnits.find((x) => x.id === id);
      return u && u.side === commanderSide;
    });
    const objIds = targetIds.filter((id) => objectives.some((o) => String(o.id) === id));

    if (unitIds.length) removeUnitIds(unitIds, commanderSide);
    if (objIds.length) {
      onObjectivesChange(objectives.filter((o) => !objIds.includes(String(o.id))));
    }
    setRoutes((prev) => prev.filter((r) => !r.targetIds.some((id) => targetIds.includes(id))));
    setSelectedEntities([]);
    setSelectedEntity(null);
    setDetailUnit(null);
    setMenu({ visible: false, x: 0, y: 0, payload: null });
    message.success(`已删除 ${targetIds.length} 个要素`);
  }

  function handleSetRoute() {
    const targetIds = selectedEntities.length
      ? selectedEntities.map((x) => String((x as { id?: string }).id))
      : menu.payload
        ? [String(menu.payload.id)]
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
    const ustore = useDeploymentStore.getState();
    ustore.commitWithHistory(
      ustore.deployedUnits.map((u) => (routeDraft.targetIds.includes(u.id) ? { ...u, mission: routeText } : u))
    );
    onObjectivesChange(
      objectives.map((o) => (routeDraft.targetIds.includes(String(o.id)) ? { ...o, description: routeText } : o))
    );

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

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA") return;
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "z" && !e.shiftKey) {
        e.preventDefault();
        if (useDeploymentStore.getState().canUndo()) {
          useDeploymentStore.getState().undo();
          message.success("已撤销兵力操作");
        }
      }
      if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === "y" || (e.key.toLowerCase() === "z" && e.shiftKey))) {
        e.preventDefault();
        if (useDeploymentStore.getState().canRedo()) {
          useDeploymentStore.getState().redo();
          message.success("已重做兵力操作");
        }
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const core = detailUnit?.coreParams;

  return (
    <Card
      className="commander-map-card"
      title="战场主视图 · OpenLayers"
      size="small"
      styles={{ body: { flex: 1, minHeight: 0, display: "flex", flexDirection: "column", padding: 10 } }}
      extra={
        <Space size={6} wrap>
          <Segmented
            size="small"
            value={perspective}
            onChange={(v) => setPerspective(v as BattlePerspective)}
            options={[
              { label: "指挥", value: "COMMAND" },
              { label: "上帝", value: "GOD" }
            ]}
          />
          <Tooltip title="指挥视角：仅己方 + 已探测/延迟隐去的敌方">
            <Space size={4} align="center">
              <Text type="secondary" style={{ fontSize: 11 }}>
                迷雾
              </Text>
              <Switch
                size="small"
                checked={fogOfWarEnabled}
                onChange={setFogOfWarEnabled}
                disabled={perspective === "GOD"}
              />
            </Space>
          </Tooltip>
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
        {loadingMap ? (
          <div
            style={{
              flex: 1,
              minHeight: 360,
              display: "grid",
              placeItems: "center",
              background: "#0f172a",
              borderRadius: 10
            }}
          >
            <Spin tip="地图载入中..." />
          </div>
        ) : null}
        {mapError ? (
          <div
            style={{
              flex: 1,
              minHeight: 360,
              display: "grid",
              placeItems: "center",
              background: "#0f172a",
              borderRadius: 10
            }}
          >
            <Space direction="vertical" align="center">
              <div>{mapError}</div>
              <Button onClick={retryMapLoad}>重试</Button>
            </Space>
          </div>
        ) : null}
        <div
          ref={(node) => {
            mapRootRef.current = node;
            drop(node);
          }}
          className="commander-ol-target"
          style={{
            flex: 1,
            minHeight: 360,
            borderRadius: 10,
            overflow: "hidden",
            border: dropState.isOver ? "2px dashed #38bdf8" : "1px solid #334155",
            display: loadingMap || mapError ? "none" : "block"
          }}
        />

        <Modal
          open={!!detailUnit}
          onCancel={() => setDetailUnit(null)}
          footer={
            <Button type="primary" onClick={() => setDetailUnit(null)}>
              关闭
            </Button>
          }
          title="兵力详情"
          destroyOnClose
        >
          {detailUnit ? (
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="兵力类型">{getUnitTypeLabel(detailUnit.type)}</Descriptions.Item>
              <Descriptions.Item label="型号">{detailUnit.modelLabel || "—"}</Descriptions.Item>
              <Descriptions.Item label="阵营">{detailUnit.side}</Descriptions.Item>
              <Descriptions.Item label="坐标">
                {detailUnit.longitude?.toFixed(5)}, {detailUnit.latitude?.toFixed(5)}
              </Descriptions.Item>
              <Descriptions.Item label="任务">{detailUnit.mission || "—"}</Descriptions.Item>
              {core ? (
                <Descriptions.Item label="核心参数">
                  <div>雷达 {core.radarRangeNm} nm</div>
                  <div>导弹射程 {core.missileRangeNm} nm</div>
                  <div>航速 {core.speedKts} kn</div>
                  {core.maxDepthM != null ? <div>潜深 ≤ {core.maxDepthM} m</div> : null}
                </Descriptions.Item>
              ) : (
                <Descriptions.Item label="核心参数">—</Descriptions.Item>
              )}
            </Descriptions>
          ) : null}
        </Modal>

        {selectedEntity && selectedEntity.kind === "objective" ? (
          <Card
            size="small"
            style={{ position: "absolute", right: 12, bottom: 52, width: 260, background: "rgba(15,23,42,0.95)" }}
            title="目标详情"
          >
            <div className="kv">
              <span>名称</span>
              <strong>{String(selectedEntity.name || selectedEntity.type || "—")}</strong>
            </div>
            <div className="kv">
              <span>经纬度</span>
              <strong>
                {Number(selectedEntity.longitude).toFixed(3)},{Number(selectedEntity.latitude).toFixed(3)}
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

        <div
          style={{
            marginTop: 8,
            paddingTop: 8,
            borderTop: "1px solid #334155",
            display: "flex",
            flexWrap: "wrap",
            gap: 8,
            alignItems: "center"
          }}
        >
          <Text type="secondary" style={{ fontSize: 12, marginRight: 8 }}>
            兵力操作栈：Ctrl+Z 撤销 · Ctrl+Shift+Z / Ctrl+Y 重做
          </Text>
          <Button size="small" icon={<RollbackOutlined />} disabled={!canUndo} onClick={() => undo()}>
            撤销
          </Button>
          <Button size="small" icon={<RedoOutlined />} disabled={!canRedo} onClick={() => redo()}>
            重做
          </Button>
          <Button
            size="small"
            danger
            disabled={!deployedUnits.filter((u) => u.side === commanderSide).length}
            onClick={() => {
              const ids = deployedUnits.filter((u) => u.side === commanderSide).map((u) => u.id);
              if (ids.length) removeUnitIds(ids, commanderSide);
              message.success("已清空当前阵营地图上的兵力");
            }}
          >
            清空当前阵营部署
          </Button>
        </div>
      </div>
    </Card>
  );
});

export default MapWorkbench;
