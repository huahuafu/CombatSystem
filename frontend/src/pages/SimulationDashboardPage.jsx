import React, { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  Alert,
  Button,
  Card,
  Divider,
  Empty,
  Progress,
  Space,
  Tag,
  Tooltip,
  Typography,
  message
} from "antd";
import {
  ArrowRightOutlined,
  CheckCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  ThunderboltOutlined
} from "@ant-design/icons";
import PlanCard from "../components/PlanCard";
import Timeline from "../components/Timeline";
import SimulationBattleMap from "../simulation/SimulationBattleMap";
import KillChainStagePanel from "../simulation/KillChainStagePanel";
import { KILL_CHAIN_STAGES } from "../simulation/killChainMeta";
import { httpJson } from "../lib/api";
import { useSimulationStore } from "../store/simulationStore";
import { useInfoStore } from "../store/infoStore";
import { buildRoundDigest, killChainFactorsFromDigest } from "../rebuild/info/infoWarfareEngine";

const { Text, Paragraph } = Typography;

const STAGES = ["find", "fix", "track", "target", "engage", "assess"];
const PLAN_META = [
  { id: "WIN_MAX", title: "胜率优先", cls: "x-win" },
  { id: "LOSS_MIN", title: "战损可控", cls: "x-loss" },
  { id: "SPEED_MAX", title: "节奏最快", cls: "x-speed" },
  { id: "BALANCED", title: "均衡推荐", cls: "x-balance" }
];

function mergeUnits(scenarioUnits, simUnits) {
  if (!scenarioUnits?.length && simUnits?.length) {
    return simUnits.map((u) => ({
      id: u.id,
      name: u.name,
      side: u.side,
      type: u.type,
      longitude: u.longitude,
      latitude: u.latitude,
      mission: u.mission
    }));
  }
  const byId = new Map((simUnits || []).map((x) => [x.id, x]));
  return (scenarioUnits || []).map((su) => {
    const live = byId.get(su.id);
    if (live && live.longitude != null && live.latitude != null) {
      return { ...su, longitude: live.longitude, latitude: live.latitude };
    }
    return su;
  });
}

function derivePlanRoutes(plan, units, objectives, side) {
  if (!plan || !units?.length) return [];
  const colors = {
    WIN_MAX: "#4ade80",
    LOSS_MIN: "#38bdf8",
    SPEED_MAX: "#f472b6",
    BALANCED: "#c4b5fd"
  };
  const color = colors[plan.id] || "#22d3ee";
  if (Array.isArray(plan.routePlan) && plan.routePlan.length) {
    return plan.routePlan
      .map((r) => {
        const wp = r.waypoints || r.points || [];
        const coords = wp
          .map((w) => [w.longitude ?? w.lon, w.latitude ?? w.lat])
          .filter((c) => c[0] != null && c[1] != null);
        return coords.length >= 2 ? { color, coords, dashed: true } : null;
      })
      .filter(Boolean);
  }
  const my = units.filter((u) => u.side === side);
  const obs = objectives || [];
  if (!my.length || !obs.length) return [];
  return my
    .map((u, i) => {
      const o = obs[i % obs.length];
      const lon = u.longitude;
      const lat = u.latitude;
      const olon = o.longitude;
      const olat = o.latitude;
      if (lon == null || lat == null || olon == null || olat == null) return null;
      const midLon = (Number(lon) + Number(olon)) / 2 + i * 0.012;
      const midLat = (Number(lat) + Number(olat)) / 2;
      return { color, coords: [[lon, lat], [midLon, midLat], [olon, olat]], dashed: true };
    })
    .filter(Boolean);
}

/** 杀伤链快照中是否已有真实评估数值（非前端臆造胜率） */
function hasTacticalMetrics(snap) {
  const engage = snap.engage || {};
  const assess = snap.assess || {};
  const ok = (v) => v !== undefined && v !== null && v !== "" && !Number.isNaN(Number(v));
  return ok(engage.winRate) || ok(engage.lossRate) || ok(assess.winRate) || ok(assess.lossRate);
}

function mapCenterFromData(units, objectives) {
  const pts = [];
  units.forEach((u) => {
    if (u.longitude != null && u.latitude != null) pts.push([u.longitude, u.latitude]);
  });
  objectives.forEach((o) => {
    if (o.longitude != null && o.latitude != null) pts.push([o.longitude, o.latitude]);
  });
  if (!pts.length) return { center: [121.5, 22.8], zoom: 8 };
  const lon = pts.reduce((s, p) => s + p[0], 0) / pts.length;
  const lat = pts.reduce((s, p) => s + p[1], 0) / pts.length;
  return { center: [lon, lat], zoom: 9 };
}

export default function SimulationDashboardPage() {
  const commanderSide = useSimulationStore((s) => s.commanderSide);
  const setStoreScenarioId = useSimulationStore((s) => s.setActiveScenarioId);
  const lastDigest = useInfoStore((s) => s.lastDigest);

  const [state, setState] = useState({});
  const [snapshots, setSnapshots] = useState({ find: {}, fix: {}, track: {}, target: {}, engage: {}, assess: {} });
  const [readiness, setReadiness] = useState({});
  const [assessment, setAssessment] = useState({});
  const [events, setEvents] = useState([]);
  const [plans, setPlans] = useState([]);
  const [selectedPlanId, setSelectedPlanId] = useState("BALANCED");
  const [loading, setLoading] = useState(false);
  const [running, setRunning] = useState(false);
  const [stageRunningPath, setStageRunningPath] = useState("");
  const [mapUnits, setMapUnits] = useState([]);
  const [mapObjectives, setMapObjectives] = useState([]);
  const [mapFlash, setMapFlash] = useState(0);
  const [roundTrail, setRoundTrail] = useState([]);

  const selectedPlan = useMemo(() => plans.find((p) => p.id === selectedPlanId) || null, [plans, selectedPlanId]);

  const stageAlertCount = (k) => {
    const s = snapshots[k] || {};
    const alerts = s.alerts || s.warnings || s.anomalies || (s.metrics && (s.metrics.alerts || s.metrics.warnings)) || [];
    if (Array.isArray(alerts)) return alerts.length;
    if (alerts && typeof alerts === "object") return Object.keys(alerts).length;
    if (typeof alerts === "number") return alerts;
    return 0;
  };

  const overallAlertCount = useMemo(() => STAGES.reduce((sum, k) => sum + stageAlertCount(k), 0), [snapshots]);

  /** 仅在 hasTacticalMetrics(snap) 为真时调用：以快照中的交战/评估指标为基准做方案差分对比；infoAdvantage 0–1 调制信息优势 */
  const buildPlansBySnapshot = (snap, infoAdvantage = 0.55) => {
    const engage = snap.engage || {};
    const assess = snap.assess || {};
    const baseWin = Number(engage.winRate ?? assess.winRate ?? 0);
    const baseLoss = Number(engage.lossRate ?? assess.lossRate ?? 0);
    const safeWin = Number.isFinite(baseWin) ? baseWin : 0.55;
    const safeLoss = Number.isFinite(baseLoss) ? baseLoss : 0.25;
    const list = [
      {
        id: "WIN_MAX",
        strategyName: "高压夺控",
        projectedWinRate: Math.min(0.99, safeWin + 0.08),
        expectedLoss: Math.max(0.05, safeLoss + 0.06),
        missionSuccessRate: Math.min(0.99, safeWin + 0.05),
        timeline: ["T+5m 侦察压制", "T+10m 主攻突进", "T+15m 侧翼包抄", "T+20m 固控评估"],
        routePlan: []
      },
      {
        id: "LOSS_MIN",
        strategyName: "低损耗拒止",
        projectedWinRate: Math.max(0.01, safeWin - 0.03),
        expectedLoss: Math.max(0.03, safeLoss - 0.12),
        missionSuccessRate: Math.max(0.01, safeWin - 0.01),
        timeline: ["T+5m 远距侦察", "T+10m 精确火力", "T+15m 机动换位", "T+20m 战损复核"],
        routePlan: []
      },
      {
        id: "SPEED_MAX",
        strategyName: "极速穿插",
        projectedWinRate: Math.min(0.99, safeWin + 0.02),
        expectedLoss: safeLoss + 0.08,
        missionSuccessRate: Math.min(0.99, safeWin + 0.04),
        timeline: ["T+3m 快速集结", "T+6m 前突穿插", "T+9m 节点夺控", "T+12m 纵深推进"],
        routePlan: []
      },
      {
        id: "BALANCED",
        strategyName: "均衡联动",
        projectedWinRate: Math.min(0.99, safeWin + 0.01),
        expectedLoss: Math.max(0.03, safeLoss - 0.02),
        missionSuccessRate: Math.min(0.99, safeWin + 0.03),
        timeline: ["T+5m 情报融合", "T+10m 分层行动", "T+15m 弹性调度", "T+20m 稳态评估"],
        routePlan: []
      }
    ];
    const bias = 0.9 + Math.max(0, Math.min(1, infoAdvantage)) * 0.22;
    const adjusted = list.map((p) => ({
      ...p,
      projectedWinRate: Math.min(0.99, p.projectedWinRate * bias),
      missionSuccessRate: Math.min(0.99, (p.missionSuccessRate ?? p.projectedWinRate) * (0.96 + infoAdvantage * 0.06))
    }));
    const best = adjusted.reduce((a, b) => (a.projectedWinRate >= b.projectedWinRate ? a : b));
    return adjusted.map((p) => ({ ...p, recommended: p.id === best.id }));
  };

  const refreshAll = async () => {
    setLoading(true);
    try {
      const st = await httpJson("/combat/v2/simulation/state");
      const fresh = {};
      for (const k of STAGES) fresh[k] = await httpJson("/combat/v2/simulation/" + k);
      const ready = await httpJson("/combat/v2/simulation/readiness");
      const assess = await httpJson("/combat/v2/simulation/assessment");
      setState(st || {});
      setSnapshots(fresh);
      setReadiness(ready || {});
      setAssessment(assess || {});

      const sid = String(st?.activeScenarioId || "").trim();

      if (!sid) {
        setStoreScenarioId("");
        setPlans([]);
        setMapUnits([]);
        setMapObjectives([]);
        setMapFlash(Date.now());
        return;
      }

      setStoreScenarioId(sid);

      let scenario = null;
      try {
        scenario = await httpJson(`/combat/scenario-data/${encodeURIComponent(sid)}`);
      } catch (_) {
        /* 想定可能未就绪 */
      }
      let simUnits = [];
      try {
        simUnits = await httpJson("/combat/v2/simulation/units");
      } catch (_) {}
      const filteredLive = (simUnits || []).filter((u) => String(u.scenarioId || "") === sid);
      const merged = mergeUnits(scenario?.units, filteredLive);
      setMapUnits(merged);
      setMapObjectives(scenario?.objectives || []);
      setMapFlash(Date.now());

      useInfoStore.getState().setBoundScenarioId(sid);
      useInfoStore.getState().ensureUnitsHaveDefaults(merged.map((u) => u.id));
      const roundNum = typeof st?.round === "number" && !Number.isNaN(st.round) ? st.round : 0;
      if (merged.length) {
        const digest = buildRoundDigest(
          roundNum,
          commanderSide,
          merged,
          useInfoStore.getState().environment,
          useInfoStore.getState().unitInfoCombat
        );
        useInfoStore.getState().setLastDigest(digest);
        useInfoStore.getState().appendRoundArchive(digest);
        useInfoStore.getState().persistArchiveToSession();
      }

      if (hasTacticalMetrics(fresh)) {
        const lastAdv = useInfoStore.getState().lastDigest?.infoAdvantage ?? 0.55;
        setPlans(buildPlansBySnapshot(fresh, lastAdv));
      } else {
        setPlans([]);
      }
    } catch (e) {
      message.error(e?.message || "刷新失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (typeof state?.round === "number" && !Number.isNaN(state.round)) {
      setRoundTrail((t) => (t[t.length - 1] === state.round ? t : [...t.slice(-20), state.round]));
    }
  }, [state?.round]);

  const runKillChainRound = async () => {
    setRunning(true);
    try {
      const out = await httpJson("/combat/v2/simulation/start-kill-chain", { method: "POST" });
      setEvents(out?.battleEvents || []);
      await refreshAll();
      message.success("杀伤链推进 1 回合完成，战场态势已刷新");
    } catch (e) {
      message.error(e?.message || "推进失败");
    } finally {
      setRunning(false);
    }
  };

  const runStage = async (path) => {
    setRunning(true);
    setStageRunningPath(path);
    try {
      await httpJson("/combat/v2/simulation/" + path, { method: "POST" });
      await refreshAll();
      const def = KILL_CHAIN_STAGES.find((s) => s.actionPath === path);
      message.success(`已执行：${def ? `${def.label}（${def.en}）` : path}，地图已同步`);
    } catch (e) {
      message.error(e?.message || "执行失败");
    } finally {
      setRunning(false);
      setStageRunningPath("");
    }
  };

  useEffect(() => {
    refreshAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅挂载时拉取全量仿真与想定联动
  }, []);

  const planRoutes = useMemo(
    () => derivePlanRoutes(selectedPlan, mapUnits, mapObjectives, commanderSide),
    [selectedPlan, mapUnits, mapObjectives, commanderSide, selectedPlanId]
  );

  const { center, zoom } = useMemo(
    () => mapCenterFromData(mapUnits, mapObjectives),
    [mapUnits, mapObjectives, mapFlash]
  );

  const readinessLabel = readiness?.readinessLevel || readiness?.level || "—";
  const phaseCn = state.operationalPhaseName || state.operationalPhase || "—";
  const alertOk = overallAlertCount === 0;
  const scenarioReady = Boolean(String(state?.activeScenarioId || "").trim());
  const plansReady = plans.length > 0;

  return (
    <div className="page-container commander-page-wrap rebuild-commander-page">
      <Alert
        type="info"
        showIcon
        message="仿真运行 · 杀伤链推演（与指挥端态势同源）"
        description={
          <Space direction="vertical" size={4} style={{ width: "100%" }}>
            <div className="sim-flow-pills">
              <Link to="/app/commander-next">
                <Tag>① 配置与部署</Tag>
              </Link>
              <ArrowRightOutlined className="muted" />
              <Tag color="processing">② 仿真运行</Tag>
              <ArrowRightOutlined className="muted" />
              <Link to="/app/run-center">
                <Tag>③ 复盘回放</Tag>
              </Link>
            </div>
            <Paragraph style={{ marginBottom: 0, fontSize: 13 }}>
              左侧为 <Text strong>杀伤链六阶段</Text>（FIND→ASSESS 已附中文）；中央{" "}
              <Text strong>OpenLayers</Text> 与想定兵力/目标及仿真单位位置联动；右侧选定 AI
              方案后地图预览机动轴线，推进回合后自动刷新。
            </Paragraph>
          </Space>
        }
      />

      <div className="commander-workbench">
        <aside className="commander-sider commander-sider-left sim-rail-left">
          <div className="commander-sider-head">
            <span className="commander-sider-title">全局态势 · 杀伤链</span>
          </div>
          <div className="commander-sider-scroll">
            {!String(state?.activeScenarioId || "").trim() ? (
              <Alert
                showIcon
                type="warning"
                message="当前未激活想定"
                description="仿真地图只显示「已激活想定」下的兵力与目标。请先在「态势与部署」中选择想定并点击激活（或新建并激活）。"
              />
            ) : null}
            <Card size="small" className="commander-panel-card" title="统一状态（整备 / 回合 / 告警）">
              <div className="sim-unified-metrics">
                <div>
                  <Text type="secondary">当前回合</Text>
                  <div className="sim-metric-val">{state.round ?? "—"}</div>
                </div>
                <div>
                  <Text type="secondary">作战阶段</Text>
                  <div className="sim-metric-val">{phaseCn}</div>
                </div>
                <div>
                  <Text type="secondary">建模整备</Text>
                  <div className="sim-metric-val">{readinessLabel}</div>
                </div>
                <div>
                  <Text type="secondary">杀伤链告警</Text>
                  <div className={`sim-metric-val ${alertOk ? "ok" : "bad"}`}>
                    {alertOk ? "正常" : `${overallAlertCount} 条`}
                  </div>
                </div>
              </div>
              <Divider style={{ margin: "12px 0" }} />
              <Space wrap>
                <Link to="/app/commander-next">
                  <Button size="small" type="default">
                    返回部署与校验
                  </Button>
                </Link>
                <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={refreshAll}>
                  刷新联动数据
                </Button>
              </Space>
            </Card>

            <Card size="small" className="commander-panel-card" title="杀伤链六阶段（状态色标）">
              <KillChainStagePanel
                state={state}
                snapshots={snapshots}
                stageAlertCount={stageAlertCount}
                stageRunningPath={stageRunningPath}
                roundTrail={roundTrail}
              />
            </Card>

            <Card size="small" className="commander-panel-card" title="信息作战 → 杀伤链六段效率">
              {!lastDigest ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="刷新后将按探测/干扰/数据链计算" />
              ) : (
                <Space direction="vertical" size={8} style={{ width: "100%" }}>
                  <div className="kv text-sm">
                    <span>本回合探测批数</span>
                    <strong>{lastDigest.detections.length}</strong>
                  </div>
                  <div className="kv text-sm">
                    <span>数据链通畅度</span>
                    <strong>{(lastDigest.datalinkIntegrity * 100).toFixed(0)}%</strong>
                  </div>
                  <div className="kv text-sm">
                    <span>信息优势指数</span>
                    <strong>{(lastDigest.infoAdvantage * 100).toFixed(0)}%</strong>
                  </div>
                  <Divider style={{ margin: "8px 0" }} />
                  {(() => {
                    const f = killChainFactorsFromDigest(lastDigest);
                    const rows = [
                      { k: "FIND", v: f.find },
                      { k: "FIX", v: f.fix },
                      { k: "TRACK", v: f.track },
                      { k: "TARGET", v: f.target },
                      { k: "ENGAGE", v: f.engage },
                      { k: "ASSESS", v: f.assess }
                    ];
                    return rows.map((r) => (
                      <div key={r.k} style={{ display: "flex", justifyContent: "space-between", fontSize: 12 }}>
                        <span className="muted">{r.k}</span>
                        <Progress
                          style={{ width: 120, marginInlineStart: 8 }}
                          percent={Math.round(r.v * 100)}
                          size="small"
                          showInfo={false}
                          strokeColor="#38bdf8"
                        />
                        <span>{(r.v * 100).toFixed(0)}%</span>
                      </div>
                    ));
                  })()}
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    因子由探测结果、电子对抗损益、数据链质量综合导出，并参与右侧 AI 方案胜率加权。
                  </Text>
                </Space>
              )}
            </Card>
          </div>
        </aside>

        <main className="commander-main-map">
          <SimulationBattleMap
            units={mapUnits}
            objectives={mapObjectives}
            planRoutes={planRoutes}
            flashToken={mapFlash}
            center={center}
            zoom={zoom}
          />
        </main>

        <aside className="commander-sider commander-sider-right sim-rail-right">
          <div className="commander-sider-head">
            <span className="commander-sider-title">AI 战术 · 执行控制</span>
          </div>
          <div className="commander-sider-scroll sim-tactics-flow">
            <Card size="small" className="commander-panel-card" title="流程进度（与指挥端一致语义）">
              <div className="x-flow" style={{ flexWrap: "wrap" }}>
                <Tag>部署校验</Tag>
                <Tag>兵力部署</Tag>
                <Tag color="processing">AI 决策</Tag>
                <Tag color={events.length ? "success" : "default"}>推演</Tag>
                <Tag>
                  <Link to="/app/run-center">复盘</Link>
                </Tag>
              </div>
              <Text type="secondary" style={{ fontSize: 12, display: "block", marginTop: 8 }}>
                「部署校验 / 兵力部署」在指挥端完成；本页承接方案选择与杀伤链推进。
              </Text>
            </Card>

            <Card
              size="small"
              className="commander-panel-card"
              title="战术方案（对比 · 选中高亮 · 地图预览）"
            >
              <Alert
                type="info"
                showIcon
                style={{ marginBottom: 12 }}
                message="方案与胜率从何而来？"
                description="四套方案是在「交战 / 评估」阶段快照里已有胜率、战损等数值时，在此基础上做的差分对比示意，不是无数据时的臆测。未激活想定或杀伤链尚未产出评估指标时，此处留空。"
              />
              {!scenarioReady ? (
                <Empty description="请先激活想定后再查看战术方案对比" />
              ) : !plansReady ? (
                <Empty
                  description={
                    <span>
                      当前想定已激活，但杀伤链快照中还没有可用于推算的评估数据（如胜率、战损率）。
                      <Text type="secondary"> 请先点击下文「推进 1 回合」或执行各杀伤链阶段，再点「刷新联动数据」。</Text>
                    </span>
                  }
                />
              ) : (
                <div className="x-plan-grid sim-plan-grid">
                  {PLAN_META.map((m) => {
                    const p = plans.find((x) => x.id === m.id);
                    return (
                      <PlanCard
                        key={m.id}
                        meta={m}
                        plan={p}
                        selected={selectedPlanId === m.id}
                        recommended={p?.recommended}
                        onSelect={() => {
                          setSelectedPlanId(m.id);
                          setMapFlash(Date.now());
                          message.info(`已选中方案：${m.title}，地图已更新预览轴线`);
                        }}
                      />
                    );
                  })}
                </div>
              )}

              {selectedPlan ? (
                <div className="sim-plan-compare" style={{ marginTop: 12 }}>
                  <Text strong>方案指标对比（预计胜率）</Text>
                  {plans.map((p) => (
                    <div key={p.id} style={{ marginTop: 8 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12 }}>
                        <span className={p.id === selectedPlanId ? "text-sky-300" : "muted"}>
                          {PLAN_META.find((x) => x.id === p.id)?.title || p.id}
                          {p.recommended ? " · 推荐" : ""}
                        </span>
                        <span>{(p.projectedWinRate * 100).toFixed(1)}%</span>
                      </div>
                      <Progress
                        percent={Math.min(100, Math.max(4, p.projectedWinRate * 100))}
                        size="small"
                        showInfo={false}
                        strokeColor={p.id === selectedPlanId ? "#38bdf8" : "#334155"}
                      />
                    </div>
                  ))}
                </div>
              ) : null}

              <Divider style={{ margin: "14px 0" }} />

              {plansReady ? (
                <div className="sim-selected-banner">
                  <CheckCircleOutlined style={{ color: "#38bdf8" }} />
                  <div>
                    <Text strong>已选中方案</Text>
                    <div className="muted" style={{ fontSize: 12 }}>
                      {PLAN_META.find((x) => x.id === selectedPlanId)?.title} — {selectedPlan?.strategyName || "—"}
                    </div>
                  </div>
                </div>
              ) : (
                <Alert type="warning" showIcon message="暂无选中方案" description="生成方案列表后可在此查看时序与地图预览。" />
              )}

              <Timeline items={selectedPlan?.timeline} emptyText="生成方案后可查看机动时序" />

              <div className="sim-execute-cta">
                <Button
                  type="primary"
                  size="large"
                  block
                  icon={<ThunderboltOutlined />}
                  loading={running}
                  disabled={!scenarioReady}
                  onClick={runKillChainRound}
                >
                  推进 1 回合（杀伤链 Kill-Chain）
                </Button>
                <Text type="secondary" style={{ fontSize: 11, display: "block", marginTop: 8, textAlign: "center" }}>
                  核心操作位于流程终点；执行后六阶段快照与地图单位位置将刷新。
                  {!scenarioReady ? " 需先激活想定。" : ""}
                </Text>
              </div>
            </Card>

            <Card size="small" className="commander-panel-card" title="执行与控制（对应杀伤链节点）">
              <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 10 }}>
                下列按钮与左侧六阶段一一对应，点击后地图与快照联动刷新。
              </Paragraph>
              <div className="sim-stage-actions">
                {KILL_CHAIN_STAGES.map((def) => (
                  <Tooltip
                    key={def.actionPath}
                    title={
                      !scenarioReady
                        ? "请先激活想定"
                        : `${def.label}（${def.en}）：${def.desc}`
                    }
                  >
                    <Button
                      block
                      size="small"
                      style={{ marginBottom: 8 }}
                      disabled={!scenarioReady}
                      loading={running && stageRunningPath === def.actionPath}
                      onClick={() => runStage(def.actionPath)}
                      icon={<PlayCircleOutlined />}
                    >
                      {def.actionLabel}（{def.en}）
                    </Button>
                  </Tooltip>
                ))}
              </div>
            </Card>

            <Card size="small" className="commander-panel-card" title="复盘摘要（Replay）">
              <div className="kv">
                <span>战术评估（Assessment）</span>
                <strong>{assessment?.status || assessment?.level || "—"}</strong>
              </div>
              <div className="kv">
                <span>胜负判定</span>
                <strong>{state.winner || "未决"}</strong>
              </div>
              <Divider style={{ margin: "10px 0" }} />
              <div className="log" style={{ maxHeight: 160, overflow: "auto", fontSize: 12 }}>
                {!events.length ? (
                  <div className="muted">暂无事件。点击「推进 1 回合」生成战报条目。</div>
                ) : (
                  events.map((e, i) => (
                    <div key={i}>
                      [{i + 1}] {e.message || `${e.action || ""} ${e.source || ""}`}
                    </div>
                  ))
                )}
              </div>
            </Card>
          </div>
        </aside>
      </div>
    </div>
  );
}
