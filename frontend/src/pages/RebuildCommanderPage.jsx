import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Button,
  Card,
  Divider,
  Empty,
  Input,
  Popconfirm,
  Progress,
  Radio,
  Segmented,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message
} from "antd";
import {
  AimOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  FlagOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  RocketOutlined,
  RollbackOutlined,
  RedoOutlined
} from "@ant-design/icons";
import { DndProvider } from "react-dnd";
import { HTML5Backend } from "react-dnd-html5-backend";
import MapWorkbench from "../rebuild/map/MapWorkbench";
import { PaletteUnit, UNIT_TEMPLATES } from "../rebuild/map/DeployPalette";
import { OBJECTIVE_TYPE_OPTIONS, UNIT_TYPE_LABELS, addUnitToList, countDeployedByType } from "../rebuild/map/deployMeta";
import { httpJson } from "../lib/api";
import { useSimulationStore } from "../store/simulationStore";
import { EnvironmentOutlined, DeleteOutlined, SaveOutlined } from "@ant-design/icons";

const { Text, Paragraph } = Typography;

function parseIssueActions(issues) {
  return (issues || []).map((raw) => {
    const text = String(raw);
    let action = null;
    if (/目标|据点|objective/i.test(text)) {
      action = { label: "去标绘目标", panel: "right", tab: "objectives" };
    } else if (/单位|兵力|unit|部署/i.test(text)) {
      action = { label: "去部署兵力", panel: "right", tab: "units" };
    } else if (/想定|scenario|激活|锁定/i.test(text)) {
      action = { label: "去想定管理", panel: "left" };
    }
    return { text, action };
  });
}

export default function RebuildCommanderPage() {
  const [scenarios, setScenarios] = useState([]);
  const [selectedScenarioId, setSelectedScenarioId] = useState("");
  const [scenarioDescription, setScenarioDescription] = useState("");
  const activeScenarioId = useSimulationStore((s) => s.activeScenarioId);
  const setActiveScenarioId = useSimulationStore((s) => s.setActiveScenarioId);
  const commanderSide = useSimulationStore((s) => s.commanderSide);
  const setCommanderSide = useSimulationStore((s) => s.setCommanderSide);
  const commanderRole = useSimulationStore((s) => s.commanderRole);
  const setCommanderRole = useSimulationStore((s) => s.setCommanderRole);
  const [goal, setGoal] = useState("以关键据点为核心组织攻防推演");
  const [units, setUnits] = useState([]);
  const [objectives, setObjectives] = useState([]);
  const [scenarioName, setScenarioName] = useState("OpenLayers重构想定");
  const [validation, setValidation] = useState(null);
  const [goalTemplate, setGoalTemplate] = useState("CUSTOM");
  const [leftCollapsed, setLeftCollapsed] = useState(false);
  const [rightCollapsed, setRightCollapsed] = useState(false);
  const [deployTabKey, setDeployTabKey] = useState("units");
  const [pendingObjective, setPendingObjective] = useState({ name: "", type: "DEFEND" });
  const [undoStack, setUndoStack] = useState([]);
  const [redoStack, setRedoStack] = useState([]);
  const mapRef = useRef(null);

  const handleWillMutate = useCallback(() => {
    setUndoStack((s) => [...s.slice(-45), { units, objectives }]);
    setRedoStack([]);
  }, [units, objectives]);

  const undo = useCallback(() => {
    if (!undoStack.length) {
      message.info("没有可撤销的操作");
      return;
    }
    const snap = undoStack[undoStack.length - 1];
    setRedoStack((r) => [...r, { units, objectives }]);
    setUndoStack((s) => s.slice(0, -1));
    setUnits(snap.units);
    setObjectives(snap.objectives);
    message.success("已撤销");
  }, [undoStack, units, objectives]);

  const redo = useCallback(() => {
    if (!redoStack.length) {
      message.info("没有可重做");
      return;
    }
    const snap = redoStack[redoStack.length - 1];
    setUndoStack((s) => [...s, { units, objectives }]);
    setRedoStack((r) => r.slice(0, -1));
    setUnits(snap.units);
    setObjectives(snap.objectives);
    message.success("已重做");
  }, [redoStack, units, objectives]);

  useEffect(() => {
    const onKey = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "z" && !e.shiftKey) {
        e.preventDefault();
        undo();
      }
      if ((e.ctrlKey || e.metaKey) && (e.key.toLowerCase() === "y" || (e.key.toLowerCase() === "z" && e.shiftKey))) {
        e.preventDefault();
        redo();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [undo, redo]);

  useEffect(() => {
    loadInitialData();
  }, []);

  async function loadInitialData() {
    const [scenarioList, active] = await Promise.all([
      httpJson("/combat/scenario-data/list").catch(() => []),
      httpJson("/combat/scenario-data/active").catch(() => ({ activeScenarioId: "" }))
    ]);
    setScenarios(Array.isArray(scenarioList) ? scenarioList : []);
    const aid = active.activeScenarioId || "";
    setActiveScenarioId(aid);
    setSelectedScenarioId(aid);
  }

  async function activateScenario() {
    if (!selectedScenarioId) return;
    await fetch(`/combat/scenario-data/set-active?id=${encodeURIComponent(selectedScenarioId)}`, { method: "POST" });
    setActiveScenarioId(selectedScenarioId);
    message.success("已激活所选想定");
  }

  async function deleteSelectedScenario() {
    if (!selectedScenarioId) return;
    const res = await fetch(`/combat/scenario-data/delete?id=${encodeURIComponent(selectedScenarioId)}`, {
      method: "POST"
    });
    if (!res.ok) {
      const t = await res.text().catch(() => "");
      message.error(t || "删除失败");
      return;
    }
    message.success("想定已删除");
    if (activeScenarioId === selectedScenarioId) {
      setActiveScenarioId("");
    }
    setSelectedScenarioId("");
    await loadInitialData();
  }

  async function createEmptyScenario() {
    const name = scenarioName.trim() || `新建想定-${new Date().toLocaleString("zh-CN", { hour12: false })}`;
    try {
      const data = await httpJson("/combat/scenario-data/create-full", {
        method: "POST",
        body: JSON.stringify({
          name,
          goal: goal || "海上交战推演",
          centerLatitude: 22.8,
          centerLongitude: 121.5,
          units: [],
          objectives: [],
          maxRounds: 50
        })
      });
      const id = data?.activeScenarioId || "";
      setActiveScenarioId(id);
      setSelectedScenarioId(id);
      setScenarioName(name);
      await loadInitialData();
      message.success("已新建并激活想定");
    } catch (e) {
      message.error(e?.message || "新建想定失败");
    }
  }

  async function saveScenarioBasic() {
    if (!selectedScenarioId) {
      message.warning("请先选择要编辑的想定");
      return;
    }
    try {
      await httpJson("/combat/scenario-data/update-basic", {
        method: "POST",
        body: JSON.stringify({
          id: selectedScenarioId,
          name: scenarioName.trim() || undefined,
          description: scenarioDescription.trim() || undefined
        })
      });
      message.success("想定基础信息已保存");
      await loadInitialData();
    } catch (e) {
      message.error(e?.message || "保存失败");
    }
  }

  async function validateDeployment() {
    const payload = { name: scenarioName, goal, units, objectives };
    const result = await httpJson("/combat/v3/commander/deployment/validate", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    setValidation(result);
    if (result?.valid) {
      message.success("部署校验通过");
    } else {
      message.warning("部署校验未通过，请查看右侧说明");
    }
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
    message.success("已根据当前部署创建并激活想定");
  }

  const scenarioOptions = useMemo(
    () =>
      scenarios.map((x) => ({
        label: `${x.name || x.id}${x.id === activeScenarioId ? " （当前激活）" : ""}`,
        value: x.id
      })),
    [scenarios, activeScenarioId]
  );

  const activeScenarioLabel = useMemo(() => {
    const row = scenarios.find((x) => x.id === activeScenarioId);
    return row?.name || activeScenarioId || "未锁定";
  }, [scenarios, activeScenarioId]);

  const scenarioColumns = useMemo(
    () => [
      {
        title: "想定名称",
        dataIndex: "name",
        key: "name",
        ellipsis: true,
        render: (text, record) => text || record.id || "—"
      },
      {
        title: "标识",
        dataIndex: "id",
        key: "id",
        width: 100,
        ellipsis: true,
        render: (id) => (
          <span style={{ fontFamily: "monospace", fontSize: 11, color: "#94a3b8" }}>{id}</span>
        )
      },
      {
        title: "状态",
        key: "status",
        width: 88,
        render: (_, record) =>
          record.id === activeScenarioId ? <Tag color="success">激活</Tag> : <Tag>未激活</Tag>
      }
    ],
    [activeScenarioId]
  );

  const goalTemplates = [
    { value: "CAPTURE", label: "占领据点", text: "优先占领关键据点并建立火力控制区" },
    { value: "DEFEND", label: "坚守阵地", text: "构建防御纵深并在目标区域持续坚守" },
    { value: "CUSTOM", label: "自定义", text: goal }
  ];

  function applyGoalTemplate(val) {
    setGoalTemplate(val);
    const item = goalTemplates.find((x) => x.value === val);
    if (val !== "CUSTOM" && item) {
      setGoal(item.text);
    }
  }

  const deployedByType = useMemo(() => countDeployedByType(units), [units]);
  const sideCount = useMemo(() => {
    const red = units.filter((u) => u.side === "RED").length;
    const blue = units.filter((u) => u.side === "BLUE").length;
    return { red, blue };
  }, [units]);
  const deployProgress = Math.min(100, Math.round(((units.length + objectives.length) / 12) * 100));
  const unitSummary = useMemo(() => units.map((x, i) => `${i + 1}. ${x.name}`).slice(0, 6), [units]);

  const validationStatus = useMemo(() => {
    if (validation == null) return { key: "pending", label: "待校验", color: "#94a3b8", icon: ExclamationCircleOutlined };
    if (validation.valid) return { key: "ok", label: "通过", color: "#22c55e", icon: CheckCircleOutlined };
    return { key: "err", label: "异常", color: "#f87171", icon: CloseCircleOutlined };
  }, [validation]);

  const issueRows = useMemo(() => parseIssueActions(validation?.issues), [validation]);

  const nextStepHint = useMemo(() => {
    if (!objectives.length) {
      return { strong: "缺少战役目标", rest: "：请在右侧「目标标绘」填写名称并在地图上点击落点，或从校验异常项跳转。" };
    }
    if (!units.length) {
      return { strong: "尚未部署兵力", rest: "：从右侧兵种卡片拖入地图，或双击快速部署。" };
    }
    if (!activeScenarioId) {
      return { strong: "未锁定激活想定", rest: "：在左侧想定列表选择并点击「激活」，或新建想定。" };
    }
    if (validation && !validation.valid) {
      return { strong: "部署未通过校验", rest: "：请根据右侧异常项修正后再次点击「执行校验」。" };
    }
    if (validation?.valid) {
      return { strong: "部署就绪", rest: "：可创建并激活想定，或进入顶部「仿真运行」继续链路。" };
    }
    return { strong: "建议执行校验", rest: "：点击右侧「执行部署校验」确认当前配置可进入下一阶段。" };
  }, [objectives.length, units.length, activeScenarioId, validation]);

  function jumpToAction(action) {
    if (!action) return;
    if (action.panel === "left") {
      setLeftCollapsed(false);
    }
    if (action.panel === "right") {
      setRightCollapsed(false);
      if (action.tab) setDeployTabKey(action.tab);
    }
  }

  function deployUnitTemplate(unitType) {
    const tpl = UNIT_TEMPLATES.find((t) => t.type === unitType);
    const deployed = units.filter((u) => u.type === unitType).length;
    if (tpl && deployed >= tpl.total) {
      message.error(`${UNIT_TYPE_LABELS[unitType] || unitType} 已达编制上限`);
      return;
    }
    handleWillMutate();
    setUnits(addUnitToList(units, commanderSide, unitType));
    message.success(`${UNIT_TYPE_LABELS[unitType] || unitType} 已加入战场`);
  }

  function clearAllDeployment() {
    if (!units.length && !objectives.length) {
      message.info("当前无部署可清空");
      return;
    }
    handleWillMutate();
    setUnits([]);
    setObjectives([]);
    message.success("已清空部署");
  }

  function focusObjectiveOnMap(obj) {
    mapRef.current?.flyToLonLat(obj.longitude, obj.latitude, 10);
  }

  const isActivatedSelection = selectedScenarioId && selectedScenarioId === activeScenarioId;

  return (
    <DndProvider backend={HTML5Backend}>
      <div className="rebuild-commander-page page-container commander-page-wrap">
        <Alert
          type="info"
          showIcon
          message="指挥终端 · 态势与部署"
          description={
            <span>
              中央为战场主视图；左侧为态势与想定配置，右侧为兵力/目标与校验。
              <Text strong style={{ color: "#fbbf24", marginLeft: 8 }}>
                下一步：
              </Text>
              <Text strong>{nextStepHint.strong}</Text>
              {nextStepHint.rest}
            </span>
          }
        />

        <div className="commander-workbench">
          <aside className={`commander-sider commander-sider-left ${leftCollapsed ? "collapsed" : ""}`}>
            <div className="commander-sider-head">
              {!leftCollapsed ? <span className="commander-sider-title">态势配置</span> : null}
              <Button
                type="text"
                size="small"
                icon={leftCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setLeftCollapsed((v) => !v)}
              />
            </div>
            {!leftCollapsed ? (
              <div className="commander-sider-scroll">
                <Card size="small" title="阵营与角色" className="commander-panel-card">
                  <Space wrap>
                    <Radio.Group
                      optionType="button"
                      buttonStyle="solid"
                      value={commanderSide}
                      onChange={(e) => setCommanderSide(e.target.value)}
                      options={[
                        { label: "红方", value: "RED" },
                        { label: "蓝方", value: "BLUE" }
                      ]}
                    />
                    <Radio.Group
                      optionType="button"
                      buttonStyle="solid"
                      value={commanderRole}
                      onChange={(e) => setCommanderRole(e.target.value)}
                      options={[
                        { label: "进攻方", value: "ATTACK" },
                        { label: "防守方", value: "DEFEND" }
                      ]}
                    />
                  </Space>
                </Card>

                <Card size="small" title="想定管理（增删查改）" className="commander-panel-card">
                  <div className="commander-active-strip">
                    <Text type="secondary">当前激活想定</Text>
                    <div className="commander-active-name">
                      <FlagOutlined style={{ color: activeScenarioId ? "#22c55e" : "#94a3b8" }} />
                      <Text strong={!!activeScenarioId} ellipsis style={{ maxWidth: "100%" }}>
                        {activeScenarioLabel}
                      </Text>
                    </div>
                  </div>
                  <div className="muted" style={{ marginBottom: 6 }}>
                    快速切换
                  </div>
                  <Select
                    style={{ width: "100%", marginBottom: 8 }}
                    placeholder="选择想定"
                    value={selectedScenarioId || undefined}
                    options={scenarioOptions}
                    onChange={(id) => {
                      setSelectedScenarioId(id);
                      const row = scenarios.find((x) => x.id === id);
                      if (row?.name) setScenarioName(row.name);
                      setScenarioDescription(row?.description || "");
                    }}
                    showSearch
                    optionFilterProp="label"
                  />
                  <Space direction="vertical" style={{ width: "100%" }} size={8}>
                    <Input
                      placeholder="想定名称"
                      value={scenarioName}
                      onChange={(e) => setScenarioName(e.target.value)}
                    />
                    <Input.TextArea
                      placeholder="想定描述（可选，保存基础信息用）"
                      value={scenarioDescription}
                      onChange={(e) => setScenarioDescription(e.target.value)}
                      rows={2}
                    />
                    <Space wrap>
                      <Button size="small" onClick={loadInitialData}>
                        刷新
                      </Button>
                      <Button size="small" onClick={createEmptyScenario}>
                        新建
                      </Button>
                      <Button
                        size="small"
                        type="primary"
                        disabled={!selectedScenarioId || isActivatedSelection}
                        onClick={activateScenario}
                      >
                        {isActivatedSelection ? "已激活" : "激活所选"}
                      </Button>
                      <Button size="small" disabled={!selectedScenarioId || isActivatedSelection} onClick={saveScenarioBasic}>
                        保存修改
                      </Button>
                      <Popconfirm
                        title="确定删除所选想定？"
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{ danger: true }}
                        onConfirm={deleteSelectedScenario}
                        disabled={!selectedScenarioId}
                      >
                        <Button size="small" danger disabled={!selectedScenarioId}>
                          删除
                        </Button>
                      </Popconfirm>
                    </Space>
                    <Button
                      type="primary"
                      block
                      size="small"
                      onClick={createAndActivateScenario}
                      disabled={!units.length || !objectives.length}
                    >
                      按当前部署创建并激活
                    </Button>
                  </Space>
                  <div className="rebuild-scenario-table" style={{ marginTop: 10 }}>
                    <Table
                      size="small"
                      rowKey="id"
                      dataSource={scenarios}
                      columns={scenarioColumns}
                      pagination={{ pageSize: 5, hideOnSinglePage: true, showSizeChanger: false }}
                      locale={{ emptyText: "暂无想定" }}
                      rowClassName={(record) => (record.id === selectedScenarioId ? "rebuild-scenario-row-active" : "")}
                      onRow={(record) => ({
                        onClick: () => {
                          setSelectedScenarioId(record.id);
                          if (record.name) setScenarioName(record.name);
                          setScenarioDescription(record.description || "");
                        }
                      })}
                    />
                  </div>
                </Card>

                <Card size="small" title="作战目标" className="commander-panel-card">
                  <Space direction="vertical" style={{ width: "100%" }}>
                    <Select
                      style={{ width: "100%" }}
                      value={goalTemplate}
                      onChange={applyGoalTemplate}
                      options={goalTemplates.map((x) => ({ label: x.label, value: x.value }))}
                    />
                    <Input.TextArea value={goal} onChange={(e) => { setGoalTemplate("CUSTOM"); setGoal(e.target.value); }} rows={2} />
                    <Tag color="blue">当前：{goal}</Tag>
                  </Space>
                </Card>
              </div>
            ) : null}
          </aside>

          <main className="commander-main-map">
            <MapWorkbench
              ref={mapRef}
              commanderSide={commanderSide}
              units={units}
              objectives={objectives}
              onUnitsChange={setUnits}
              onObjectivesChange={setObjectives}
              onWillMutate={handleWillMutate}
              pendingObjective={pendingObjective}
              onPendingObjectiveChange={setPendingObjective}
            />
          </main>

          <aside className={`commander-sider commander-sider-right ${rightCollapsed ? "collapsed" : ""}`}>
            <div className="commander-sider-head">
              {!rightCollapsed ? <span className="commander-sider-title">兵力与校验</span> : null}
              <Button
                type="text"
                size="small"
                icon={rightCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setRightCollapsed((v) => !v)}
              />
            </div>
            {!rightCollapsed ? (
              <div className="commander-sider-scroll">
                <Card size="small" className="commander-panel-card commander-status-card" title="部署状态与校验">
                  <div className="validation-status-pill" style={{ borderColor: validationStatus.color }}>
                    <validationStatus.icon style={{ color: validationStatus.color }} />
                    <span style={{ color: validationStatus.color }}>{validationStatus.label}</span>
                    <Tag color={validationStatus.key === "ok" ? "success" : validationStatus.key === "err" ? "error" : "default"}>
                      {validationStatus.key === "pending" ? "待校验" : validationStatus.key === "ok" ? "校验通过" : "存在异常"}
                    </Tag>
                  </div>
                  <div className="commander-metrics">
                    <div className="commander-metric">
                      <RocketOutlined />
                      <div>
                        <div className="metric-label">已部署单位</div>
                        <div className="metric-value">{units.length}</div>
                      </div>
                    </div>
                    <div className="commander-metric">
                      <AimOutlined />
                      <div>
                        <div className="metric-label">已部署目标</div>
                        <div className="metric-value">{objectives.length}</div>
                      </div>
                    </div>
                    <div className="commander-metric">
                      <FlagOutlined />
                      <div>
                        <div className="metric-label">激活锁定</div>
                        <div className="metric-value">{activeScenarioId ? 1 : 0}</div>
                      </div>
                    </div>
                  </div>
                  <Space wrap style={{ marginTop: 10 }}>
                    <Button type="primary" size="small" icon={<CheckCircleOutlined />} onClick={validateDeployment}>
                      执行部署校验
                    </Button>
                  </Space>
                  {validation ? (
                    <Alert
                      style={{ marginTop: 10 }}
                      type={validation.valid ? "success" : "error"}
                      showIcon
                      message={validation.valid ? "校验通过" : "校验未通过"}
                      description={
                        <div>
                          {(validation.issues || []).length ? (
                            <ul className="validation-issue-list">
                              {issueRows.map((row, i) => (
                                <li key={i}>
                                  <Text type="danger">{row.text}</Text>
                                  {row.action ? (
                                    <Button type="link" size="small" onClick={() => jumpToAction(row.action)}>
                                      {row.action.label}
                                    </Button>
                                  ) : null}
                                </li>
                              ))}
                            </ul>
                          ) : (
                            "未返回具体问题条目"
                          )}
                        </div>
                      }
                    />
                  ) : (
                    <Paragraph type="secondary" style={{ marginTop: 8, fontSize: 12 }}>
                      尚未执行校验。完成兵力与目标标绘后，点击「执行部署校验」获取结论。
                    </Paragraph>
                  )}
                  <Divider style={{ margin: "12px 0" }} />
                  <div className="kv text-sm">
                    <span>兵力匹配度</span>
                    <strong className={units.length >= objectives.length ? "text-emerald-400" : "text-amber-400"}>
                      {units.length >= objectives.length ? "满足" : "不足"}
                    </strong>
                  </div>
                  <div className="kv text-sm">
                    <span>目标可执行性</span>
                    <strong className={objectives.length ? "text-emerald-400" : "text-rose-400"}>
                      {objectives.length ? "已建立" : "缺少目标"}
                    </strong>
                  </div>
                </Card>

                <Tabs
                  size="small"
                  activeKey={deployTabKey}
                  onChange={setDeployTabKey}
                  items={[
                    {
                      key: "units",
                      label: "兵力部署",
                      children: (
                        <Card size="small" bordered={false} className="commander-panel-card">
                          <Space wrap>
                            {UNIT_TEMPLATES.map((u) => {
                              const remain = Math.max(0, u.total - (deployedByType[u.type] || 0));
                              return (
                                <PaletteUnit
                                  key={u.type}
                                  template={u}
                                  remain={remain}
                                  onQuickDeploy={() => deployUnitTemplate(u.type)}
                                />
                              );
                            })}
                          </Space>
                          <Space style={{ marginTop: 10 }} wrap>
                            <Button size="small" icon={<RollbackOutlined />} onClick={undo}>
                              撤销
                            </Button>
                            <Button size="small" icon={<RedoOutlined />} onClick={redo}>
                              重做
                            </Button>
                            <Popconfirm title="清空所有兵力与目标？" onConfirm={clearAllDeployment}>
                              <Button size="small" danger icon={<DeleteOutlined />}>
                                一键清空
                              </Button>
                            </Popconfirm>
                          </Space>
                          <div className="muted" style={{ marginTop: 8, fontSize: 12 }}>
                            拖入中央地图部署；Ctrl+Z / Ctrl+Y 撤销重做。
                          </div>
                          {!units.length ? (
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="拖拽或双击部署" style={{ marginTop: 12 }} />
                          ) : null}
                        </Card>
                      )
                    },
                    {
                      key: "objectives",
                      label: "目标标绘",
                      children: (
                        <Card size="small" bordered={false} className="commander-panel-card">
                          <Space direction="vertical" style={{ width: "100%" }}>
                            <Segmented
                              block
                              options={OBJECTIVE_TYPE_OPTIONS.map((x) => ({ label: x.label, value: x.value }))}
                              value={pendingObjective.type}
                              onChange={(v) => setPendingObjective((s) => ({ ...s, type: v }))}
                            />
                            <Input
                              placeholder="目标名称（填后在地图点击）"
                              value={pendingObjective.name}
                              onChange={(e) => setPendingObjective((s) => ({ ...s, name: e.target.value }))}
                            />
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {!pendingObjective.name ? (
                                <Text strong className="text-amber-400">
                                  缺少目标名称
                                </Text>
                              ) : null}
                              {pendingObjective.name ? "在战场主视图单击落点即可创建。" : "：请先填写名称再点击地图。"}
                            </Text>
                            <div style={{ maxHeight: 200, overflow: "auto", display: "grid", gap: 6 }}>
                              {objectives.map((obj) => (
                                <Button key={obj.id} size="small" onClick={() => focusObjectiveOnMap(obj)} icon={<EnvironmentOutlined />}>
                                  {obj.name || obj.type}
                                </Button>
                              ))}
                            </div>
                          </Space>
                        </Card>
                      )
                    },
                    {
                      key: "summary",
                      label: "摘要",
                      children: (
                        <Card size="small" bordered={false} className="commander-panel-card">
                          <div className="kv">
                            <span>已部署单位</span>
                            <strong>{units.length}</strong>
                          </div>
                          <div className="kv">
                            <span>已部署目标</span>
                            <strong>{objectives.length}</strong>
                          </div>
                          <div style={{ marginTop: 8 }}>
                            <div className="muted" style={{ marginBottom: 6 }}>
                              双方兵力
                            </div>
                            <Progress
                              percent={
                                sideCount.red + sideCount.blue ? Math.round((sideCount.red / (sideCount.red + sideCount.blue)) * 100) : 0
                              }
                              format={(p) => `红 ${p}%`}
                              strokeColor="#dc2626"
                              size="small"
                            />
                            <Progress
                              percent={
                                sideCount.red + sideCount.blue ? Math.round((sideCount.blue / (sideCount.red + sideCount.blue)) * 100) : 0
                              }
                              format={(p) => `蓝 ${p}%`}
                              strokeColor="#2563eb"
                              size="small"
                            />
                          </div>
                          <div style={{ marginTop: 8 }}>
                            <div className="muted" style={{ marginBottom: 6 }}>
                              部署完成度
                            </div>
                            <Progress percent={deployProgress} size="small" />
                          </div>
                          <Button
                            type="primary"
                            block
                            size="small"
                            icon={<SaveOutlined />}
                            style={{ marginTop: 10 }}
                            onClick={() => mapRef.current?.saveDeployment?.()}
                          >
                            保存部署到想定
                          </Button>
                          <div style={{ marginTop: 8, fontSize: 12 }} className="muted">
                            {unitSummary.length ? unitSummary.join(" · ") : "暂无摘要"}
                          </div>
                        </Card>
                      )
                    }
                  ]}
                />
              </div>
            ) : null}
          </aside>
        </div>
      </div>
    </DndProvider>
  );
}
