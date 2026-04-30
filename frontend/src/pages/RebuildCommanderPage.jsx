import React, { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Col, Divider, Input, Row, Select, Space, Statistic, Tabs, Tag } from "antd";
import { DndProvider } from "react-dnd";
import { HTML5Backend } from "react-dnd-html5-backend";
import MapWorkbench from "../rebuild/map/MapWorkbench";
import TacticsPanel from "../rebuild/tactics/TacticsPanel";
import ReplayDashboard from "../rebuild/replay/ReplayDashboard";
import { httpJson } from "../lib/api";

export default function RebuildCommanderPage() {
  const [scenarios, setScenarios] = useState([]);
  const [activeScenarioId, setActiveScenarioId] = useState("");
  const [selectedScenarioId, setSelectedScenarioId] = useState("");
  const [commanderSide, setCommanderSide] = useState("RED");
  const [commanderRole, setCommanderRole] = useState("ATTACK");
  const [goal, setGoal] = useState("以关键据点为核心组织攻防推演");
  const [units, setUnits] = useState([]);
  const [objectives, setObjectives] = useState([]);
  const [events, setEvents] = useState([]);
  const [scenarioName, setScenarioName] = useState("OpenLayers重构想定");
  const [validation, setValidation] = useState(null);

  useEffect(() => {
    loadInitialData();
  }, []);

  async function loadInitialData() {
    const [scenarioList, active] = await Promise.all([
      httpJson("/combat/scenario-data/list").catch(() => []),
      httpJson("/combat/scenario-data/active").catch(() => ({ activeScenarioId: "" }))
    ]);
    setScenarios(Array.isArray(scenarioList) ? scenarioList : []);
    setActiveScenarioId(active.activeScenarioId || "");
    setSelectedScenarioId(active.activeScenarioId || "");
  }

  async function activateScenario() {
    if (!selectedScenarioId) return;
    await fetch(`/combat/scenario-data/set-active?id=${encodeURIComponent(selectedScenarioId)}`, { method: "POST" });
    setActiveScenarioId(selectedScenarioId);
  }

  async function validateDeployment() {
    const payload = { name: scenarioName, goal, units, objectives };
    const result = await httpJson("/combat/v3/commander/deployment/validate", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    setValidation(result);
    return result;
  }

  async function createAndActivateScenario() {
    const check = await validateDeployment();
    if (!check?.valid) return;
    const centerLatitude = units[0]?.latitude || objectives[0]?.latitude || 22.8;
    const centerLongitude = units[0]?.longitude || objectives[0]?.longitude || 121.5;
    const payload = {
      name: scenarioName,
      goal,
      centerLatitude,
      centerLongitude,
      units,
      objectives,
      maxRounds: 50
    };
    const data = await httpJson("/combat/scenario-data/create-full", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    const id = data?.activeScenarioId || "";
    setActiveScenarioId(id);
    setSelectedScenarioId(id);
    await loadInitialData();
  }

  const scenarioOptions = useMemo(
    () => scenarios.map((x) => ({ label: x.name || x.id, value: x.id })),
    [scenarios]
  );

  return (
    <DndProvider backend={HTML5Backend}>
      <div className="p-4 space-y-3">
        <Alert
          type="info"
          showIcon
          message="OpenLayers重构工作台（并行迁移）"
          description="新架构与旧工作台并行，优先完成部署、AI决策、推演复盘的全链路重建。"
        />
        <Row gutter={12}>
          <Col span={16}>
            <Card title="指挥参数">
              <Space wrap>
                <Select
                  style={{ minWidth: 220 }}
                  placeholder="选择想定"
                  value={selectedScenarioId || undefined}
                  options={scenarioOptions}
                  onChange={setSelectedScenarioId}
                />
                <Button type="primary" onClick={activateScenario} disabled={!selectedScenarioId}>
                  激活想定
                </Button>
                <Select
                  value={commanderSide}
                  onChange={setCommanderSide}
                  options={[
                    { label: "红方", value: "RED" },
                    { label: "蓝方", value: "BLUE" }
                  ]}
                />
                <Select
                  value={commanderRole}
                  onChange={setCommanderRole}
                  options={[
                    { label: "进攻方", value: "ATTACK" },
                    { label: "防守方", value: "DEFEND" }
                  ]}
                />
                <Input style={{ minWidth: 320 }} value={goal} onChange={(e) => setGoal(e.target.value)} />
                <Input style={{ minWidth: 220 }} value={scenarioName} onChange={(e) => setScenarioName(e.target.value)} />
                <Button onClick={validateDeployment} disabled={!units.length && !objectives.length}>
                  校验部署
                </Button>
                <Button type="primary" onClick={createAndActivateScenario} disabled={!units.length || !objectives.length}>
                  创建并激活新想定
                </Button>
              </Space>
              <Divider />
              <Space size={20}>
                <Statistic title="激活想定" value={activeScenarioId || "-"} />
                <Statistic title="已部署单位" value={units.length} />
                <Statistic title="已部署目标" value={objectives.length} />
                <Tag color={activeScenarioId ? "success" : "default"}>{activeScenarioId ? "READY" : "NO SCENARIO"}</Tag>
              </Space>
              {validation ? (
                <>
                  <Divider />
                  <Alert
                    type={validation.valid ? "success" : "error"}
                    showIcon
                    message={validation.valid ? "部署校验通过" : "部署校验失败"}
                    description={(validation.issues || []).join("；") || "无问题"}
                  />
                </>
              ) : null}
            </Card>
          </Col>
          <Col span={8}>
            <Card title="迁移状态">
              <div className="space-y-2 text-sm">
                <div>MapCore: OpenLayers</div>
                <div>UI: Ant Design + Tailwind</div>
                <div>DragDeploy: react-dnd</div>
                <div>Replay: ECharts</div>
              </div>
            </Card>
          </Col>
        </Row>

        <Tabs
          items={[
            {
              key: "deploy",
              label: "部署与标绘",
              children: (
                <MapWorkbench
                  commanderSide={commanderSide}
                  units={units}
                  objectives={objectives}
                  onUnitsChange={setUnits}
                  onObjectivesChange={setObjectives}
                />
              )
            },
            {
              key: "tactics",
              label: "AI战术",
              children: (
                <TacticsPanel
                  activeScenarioId={activeScenarioId}
                  goal={goal}
                  commanderSide={commanderSide}
                  commanderRole={commanderRole}
                  onRunEvents={setEvents}
                />
              )
            },
            {
              key: "replay",
              label: "推演复盘",
              children: <ReplayDashboard events={events} />
            }
          ]}
        />
      </div>
    </DndProvider>
  );
}
