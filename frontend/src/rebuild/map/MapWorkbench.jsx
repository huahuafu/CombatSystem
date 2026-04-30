import React, { useEffect, useMemo, useRef, useState } from "react";
import { Button, Card, Col, Input, Row, Select, Space, Tag } from "antd";
import Map from "ol/Map";
import View from "ol/View";
import TileLayer from "ol/layer/Tile";
import XYZ from "ol/source/XYZ";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Feature from "ol/Feature";
import Point from "ol/geom/Point";
import LineString from "ol/geom/LineString";
import { fromLonLat, toLonLat } from "ol/proj";
import { Fill, Stroke, Style, Circle as CircleStyle } from "ol/style";
import { useDrag, useDrop } from "react-dnd";
import "ol/ol.css";

const UNIT_TYPES = ["DESTROYER", "FRIGATE", "SUBMARINE", "UAV_RECON"];

function PaletteUnit({ unitType }) {
  const [{ isDragging }, drag] = useDrag(() => ({
    type: "UNIT_TEMPLATE",
    item: { unitType },
    collect: (monitor) => ({ isDragging: monitor.isDragging() })
  }));
  return (
    <Tag ref={drag} color="processing" style={{ opacity: isDragging ? 0.45 : 1, cursor: "grab" }}>
      {unitType}
    </Tag>
  );
}

export default function MapWorkbench({ commanderSide, units, objectives, onUnitsChange, onObjectivesChange }) {
  const objectivesRef = useRef(objectives);
  const pendingObjectiveRef = useRef({ name: "", type: "DEFEND" });
  const [, drop] = useDrop(
    () => ({
      accept: "UNIT_TEMPLATE",
      drop: (item, monitor) => {
        const clientOffset = monitor.getClientOffset();
        if (!clientOffset || !mapRootRef.current || !mapRef.current) return;
        const rect = mapRootRef.current.getBoundingClientRect();
        const pixel = [clientOffset.x - rect.left, clientOffset.y - rect.top];
        const coord = mapRef.current.getCoordinateFromPixel(pixel);
        if (!coord) return;
        const [longitude, latitude] = toLonLat(coord).map((x) => Number(x.toFixed(6)));
        onUnitsChange([
          ...units,
          {
            name: `${item.unitType}-${units.length + 1}`,
            side: commanderSide,
            type: item.unitType,
            latitude,
            longitude,
            mission: "拖拽部署"
          }
        ]);
      }
    }),
    [commanderSide, onUnitsChange, units]
  );

  const mapRootRef = useRef(null);
  const mapRef = useRef(null);
  const unitLayerRef = useRef(null);
  const objectiveLayerRef = useRef(null);
  const lineLayerRef = useRef(null);
  const [pendingObjective, setPendingObjective] = useState({ name: "", type: "DEFEND" });

  useEffect(() => {
    objectivesRef.current = objectives;
  }, [objectives]);

  useEffect(() => {
    pendingObjectiveRef.current = pendingObjective;
  }, [pendingObjective]);

  useEffect(() => {
    if (!mapRootRef.current || mapRef.current) return;
    const baseLayer = new TileLayer({
      source: new XYZ({
        url: "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      })
    });

    unitLayerRef.current = new VectorLayer({ source: new VectorSource() });
    objectiveLayerRef.current = new VectorLayer({ source: new VectorSource() });
    lineLayerRef.current = new VectorLayer({ source: new VectorSource() });

    mapRef.current = new Map({
      target: mapRootRef.current,
      layers: [baseLayer, lineLayerRef.current, objectiveLayerRef.current, unitLayerRef.current],
      view: new View({
        center: fromLonLat([121.5, 22.8]),
        zoom: 8
      })
    });

    mapRef.current.on("singleclick", (evt) => {
      const [lon, latitude] = toLonLat(evt.coordinate).map((x) => Number(x.toFixed(6)));
      const currentObjective = pendingObjectiveRef.current;
      if (!currentObjective.name) return;
      onObjectivesChange([
        ...objectivesRef.current,
        { ...currentObjective, side: commanderSide === "RED" ? "BLUE" : "RED", latitude, longitude: lon, priority: 8 }
      ]);
      setPendingObjective((s) => ({ ...s, name: "" }));
    });
  }, [commanderSide, onObjectivesChange]);

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
      feature.setStyle(
        new Style({
          image: new CircleStyle({
            radius: 6,
            fill: new Fill({ color: u.side === "RED" ? "#f43f5e" : "#38bdf8" }),
            stroke: new Stroke({ color: "#fff", width: 1 })
          })
        })
      );
      unitSource.addFeature(feature);
    });

    objectives.forEach((o) => {
      const feature = new Feature({ geometry: new Point(fromLonLat([o.longitude, o.latitude])) });
      feature.setStyle(
        new Style({
          image: new CircleStyle({
            radius: 7,
            fill: new Fill({ color: "#f59e0b" }),
            stroke: new Stroke({ color: "#fff", width: 1 })
          })
        })
      );
      objSource.addFeature(feature);
    });

    // 防线示意（M2阶段可替换为MilStd2525符号）
    if (units.length > 1) {
      const coordinates = units.map((x) => fromLonLat([x.longitude, x.latitude]));
      const defenseLine = new Feature({ geometry: new LineString(coordinates) });
      defenseLine.setStyle(new Style({ stroke: new Stroke({ color: "#60a5fa", width: 2 }) }));
      lineSource.addFeature(defenseLine);
    }
  }, [units, objectives]);

  const unitSummary = useMemo(() => units.map((x, i) => `${i + 1}. ${x.name} ${x.type}`).slice(0, 6), [units]);

  function addUnitTemplate(unitType) {
    const next = {
      name: `${unitType}-${units.length + 1}`,
      side: commanderSide,
      type: unitType,
      latitude: 22.8 + units.length * 0.02,
      longitude: 121.4 + units.length * 0.03,
      mission: "机动部署"
    };
    onUnitsChange([...units, next]);
  }

  return (
    <Row gutter={12}>
      <Col span={6}>
        <Card title="兵力拖拽托盘" size="small">
          <Space wrap>
            {UNIT_TYPES.map((u) => (
              <span key={u} onDoubleClick={() => addUnitTemplate(u)}>
                <PaletteUnit unitType={u} />
              </span>
            ))}
          </Space>
          <div className="mt-3 text-xs text-slate-400">双击单位类型可快速部署到默认点位。</div>
        </Card>
        <Card title="目标编辑" size="small" style={{ marginTop: 12 }}>
          <Space direction="vertical" style={{ width: "100%" }}>
            <Input
              placeholder="目标名称"
              value={pendingObjective.name}
              onChange={(e) => setPendingObjective((s) => ({ ...s, name: e.target.value }))}
            />
            <Select
              value={pendingObjective.type}
              onChange={(v) => setPendingObjective((s) => ({ ...s, type: v }))}
              options={[
                { value: "CAPTURE", label: "占领" },
                { value: "DESTROY", label: "歼灭" },
                { value: "DEFEND", label: "坚守" }
              ]}
            />
            <div className="text-xs text-slate-400">输入名称后在地图单击可落点。</div>
          </Space>
        </Card>
        <Card title="部署摘要" size="small" style={{ marginTop: 12 }}>
          {unitSummary.length ? unitSummary.map((x) => <div key={x}>{x}</div>) : <div className="text-slate-400">暂无单位</div>}
          <Button danger style={{ marginTop: 10 }} onClick={() => { onUnitsChange([]); onObjectivesChange([]); }}>
            清空部署
          </Button>
        </Card>
      </Col>
      <Col span={18}>
        <Card title="OpenLayers战场地图" size="small">
          <div ref={(node) => { mapRootRef.current = node; drop(node); }} style={{ height: 620, borderRadius: 10, overflow: "hidden" }} />
        </Card>
      </Col>
    </Row>
  );
}
