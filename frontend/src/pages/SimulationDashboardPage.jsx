import React, { useEffect, useMemo, useState } from "react";
import PlanCard from "../components/PlanCard";
import StageStatusGrid from "../components/StageStatusGrid";
import Timeline from "../components/Timeline";
import ToastHost from "../components/ToastHost";
import { httpJson } from "../lib/api";

const STAGES = ["find", "fix", "track", "target", "engage", "assess"];
const PLAN_META = [
  { id: "WIN_MAX", title: "胜率最高方案", cls: "x-win" },
  { id: "LOSS_MIN", title: "兵力损耗最小方案", cls: "x-loss" },
  { id: "SPEED_MAX", title: "推进/坚守最快方案", cls: "x-speed" },
  { id: "BALANCED", title: "均衡综合方案", cls: "x-balance" }
];

export default function SimulationDashboardPage() {
  const [toasts, setToasts] = useState([]);
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

  const selectedPlan = useMemo(() => plans.find((p) => p.id === selectedPlanId) || null, [plans, selectedPlanId]);
  const pushToast = (title, message, level = "info") => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [{ id, title, message: String(message), level }, ...prev].slice(0, 4));
    setTimeout(() => setToasts((prev) => prev.filter((x) => x.id !== id)), 4200);
  };
  const stageAlertCount = (k) => {
    const s = snapshots[k] || {};
    const alerts = s.alerts || s.warnings || s.anomalies || (s.metrics && (s.metrics.alerts || s.metrics.warnings)) || [];
    if (Array.isArray(alerts)) return alerts.length;
    if (alerts && typeof alerts === "object") return Object.keys(alerts).length;
    if (typeof alerts === "number") return alerts;
    return 0;
  };
  const overallAlertCount = useMemo(() => STAGES.reduce((sum, k) => sum + stageAlertCount(k), 0), [snapshots]);

  const buildPlansBySnapshot = (snap) => {
    const engage = snap.engage || {};
    const assess = snap.assess || {};
    const baseWin = Number(engage.winRate || assess.winRate || 0.62);
    const baseLoss = Number(engage.lossRate || assess.lossRate || 0.28);
    return [
      { id: "WIN_MAX", strategyName: "高压夺控", projectedWinRate: baseWin + 0.08, expectedLoss: Math.max(0.05, baseLoss + 0.06), missionSuccessRate: baseWin + 0.05, timeline: ["T+5m 侦察压制", "T+10m 主攻突进", "T+15m 侧翼包抄", "T+20m 固控评估"] },
      { id: "LOSS_MIN", strategyName: "低损耗拒止", projectedWinRate: baseWin - 0.03, expectedLoss: Math.max(0.03, baseLoss - 0.12), missionSuccessRate: baseWin - 0.01, timeline: ["T+5m 远距侦察", "T+10m 精确火力", "T+15m 机动换位", "T+20m 战损复核"] },
      { id: "SPEED_MAX", strategyName: "极速穿插", projectedWinRate: baseWin + 0.02, expectedLoss: baseLoss + 0.08, missionSuccessRate: baseWin + 0.04, timeline: ["T+3m 快速集结", "T+6m 前突穿插", "T+9m 节点夺控", "T+12m 纵深推进"] },
      { id: "BALANCED", strategyName: "均衡联动", projectedWinRate: baseWin + 0.01, expectedLoss: baseLoss - 0.02, missionSuccessRate: baseWin + 0.03, timeline: ["T+5m 情报融合", "T+10m 分层行动", "T+15m 弹性调度", "T+20m 稳态评估"] }
    ];
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
      setPlans(buildPlansBySnapshot(fresh));
    } catch (e) {
      pushToast("刷新失败", e.message || e, "err");
    } finally {
      setLoading(false);
    }
  };

  const runKillChainRound = async () => {
    setRunning(true);
    try {
      const out = await httpJson("/combat/v2/simulation/start-kill-chain", { method: "POST" });
      setEvents((out && out.battleEvents) ? out.battleEvents : []);
      await refreshAll();
      pushToast("推进完成", "已完成 1 回合 kill-chain 推演。", "ok");
    } catch (e) {
      pushToast("推进失败", e.message || e, "err");
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
      pushToast("执行成功", "已执行阶段：" + path, "ok");
    } catch (e) {
      pushToast("执行失败", e.message || e, "err");
    } finally {
      setRunning(false);
      setStageRunningPath("");
    }
  };

  useEffect(() => { refreshAll(); }, []);

  return (
    <div className="container">
      <ToastHost toasts={toasts} />
      <section className="panel">
        <div className="title">仿真看板（工程化）</div>
        <div className="x-flow"><span className="pill active">部署</span><span className={`pill ${plans.length ? "active" : ""}`}>AI决策</span><span className={`pill ${running || events.length ? "active" : ""}`}>推演</span><span className={`pill ${events.length ? "active" : ""}`}>复盘</span></div>
      </section>
      <section className="sim-statusbar">
        <div className="sim-status-item"><span className="k">当前回合</span><strong className="v">{state.round ?? "—"}</strong></div>
        <div className="sim-status-item"><span className="k">当前阶段</span><strong className="v">{state.phaseName || state.phase || "—"}</strong></div>
        <div className={`sim-status-item ${overallAlertCount > 0 ? "is-alert" : ""}`}><span className="k">整体告警</span><strong className="v">{overallAlertCount > 0 ? "告警中" : "正常"}</strong></div>
        <div className="sim-status-item"><span className="k">readiness</span><strong className="v">{readiness.readinessLevel || "—"}</strong></div>
      </section>
      <div className="grid cols2">
        <section className="panel">
          <div className="title">AI 四战术方案</div>
          <div className="x-plan-grid">{PLAN_META.map((m) => <PlanCard key={m.id} meta={m} plan={plans.find((p) => p.id === m.id)} selected={selectedPlanId === m.id} onSelect={() => setSelectedPlanId(m.id)} />)}</div>
        </section>
        <section className="panel">
          <div className="title">执行与控制</div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button className="warn" onClick={refreshAll} disabled={loading}>{loading ? "刷新中..." : "刷新全部快照"}</button>
            <button className="primary" onClick={runKillChainRound} disabled={running}>{running ? "执行中..." : "推进 1 回合（kill-chain）"}</button>
          </div>
          <div className="grid cols2" style={{ marginTop: 10 }}>{["find/scan", "fix/fuse", "track/run", "target/plan", "engage/run", "assess/run"].map((path) => <button key={path} onClick={() => runStage(path)} disabled={running}>{stageRunningPath === path ? "执行中..." : path}</button>)}</div>
          <Timeline items={selectedPlan?.timeline} emptyText="选中战术后展示行动时序" />
        </section>
      </div>
      <div className="grid cols2">
        <section className="panel">
          <div className="title">六阶段态势</div>
          <StageStatusGrid stages={STAGES} state={state} snapshots={snapshots} stageAlertCount={stageAlertCount} />
        </section>
        <section className="panel">
          <div className="title">复盘摘要</div>
          <div className="kv"><span>Assessment</span><strong>{assessment.status || assessment.level || "—"}</strong></div>
          <div className="kv"><span>胜负判定</span><strong>{state.winner || "未决"}</strong></div>
          <div className="log">{!events.length ? <div className="muted">暂无事件。点击“推进 1 回合（kill-chain）”。</div> : events.map((e, i) => <div key={i}>[{i + 1}] {e.message || (e.action + " " + (e.source || ""))}</div>)}</div>
        </section>
      </div>
    </div>
  );
}
