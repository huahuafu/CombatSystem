import React from "react";
import { Progress, Tag, Tooltip, Typography } from "antd";
import { CheckCircleOutlined, LoadingOutlined, WarningOutlined, ClockCircleOutlined } from "@ant-design/icons";
import { KILL_CHAIN_STAGES } from "./killChainMeta";

const { Text } = Typography;

function stageVisual({ def, snapshot, alertCount, runningPath, currentPhaseKey }) {
  const pathPrefix = def.actionPath.split("/")[0];
  const isRunning = runningPath && runningPath.startsWith(pathPrefix);
  if (isRunning) {
    return { tone: "active", label: "进行中", color: "processing", icon: LoadingOutlined };
  }
  if (alertCount > 0) {
    return { tone: "err", label: "异常", color: "error", icon: WarningOutlined };
  }
  const lv = String(snapshot?.readinessLevel || "").toUpperCase();
  if (lv === "HIGH" || snapshot?.status === "COMPLETE" || snapshot?.phaseStatus === "DONE") {
    return { tone: "ok", label: "已完成", color: "success", icon: CheckCircleOutlined };
  }
  if (currentPhaseKey === def.key) {
    return { tone: "active", label: "当前阶段", color: "processing", icon: ClockCircleOutlined };
  }
  return { tone: "idle", label: "待触发", color: "default", icon: ClockCircleOutlined };
}

export default function KillChainStagePanel({
  state,
  snapshots,
  stageAlertCount,
  stageRunningPath,
  roundTrail = []
}) {
  const phase = String(state?.operationalPhase || "").toLowerCase();

  return (
    <div className="kill-chain-stage-panel">
      <div className="kill-chain-round-trend">
        <Text type="secondary" style={{ fontSize: 12 }}>
          回合变化趋势（最近）
        </Text>
        <div className="kill-chain-round-trail">
          {roundTrail.length ? (
            <Text code style={{ fontSize: 11, color: "#93c5fd" }}>
              {roundTrail.join(" → ")}
            </Text>
          ) : (
            <Text type="secondary" style={{ fontSize: 12 }}>
              推进回合后在此显示
            </Text>
          )}
        </div>
        {typeof state?.round === "number" ? (
          <Progress
            size="small"
            percent={Math.min(100, Math.round((state.round / 50) * 100))}
            format={() => `当前第 ${state.round} 回合`}
            strokeColor={{ from: "#1d4ed8", to: "#38bdf8" }}
          />
        ) : null}
      </div>
      <div className="kill-chain-grid">
        {KILL_CHAIN_STAGES.map((def) => {
          const snap = snapshots[def.key] || {};
          const alerts = stageAlertCount(def.key);
          const vis = stageVisual({
            def,
            snapshot: snap,
            alertCount: alerts,
            runningPath: stageRunningPath,
            currentPhaseKey: phase
          });
          const Icon = vis.icon;
          return (
            <Tooltip
              key={def.key}
              title={
                <div>
                  <div>
                    <strong>{def.label}</strong>（{def.en}）
                  </div>
                  <div style={{ marginTop: 4 }}>{def.desc}</div>
                  <div className="muted" style={{ marginTop: 4, fontSize: 11 }}>
                    与「执行与控制」中「{def.actionLabel}」操作对应同一杀伤链节点。
                  </div>
                </div>
              }
            >
              <div className={`kill-chain-cell kill-chain-cell--${vis.tone}`}>
                <div className="kill-chain-cell-head">
                  <span className="kill-chain-en">{def.en}</span>
                  <Tag color={vis.color} icon={vis.label === "进行中" ? <LoadingOutlined spin /> : <Icon />}>
                    {vis.label}
                  </Tag>
                </div>
                <div className="kill-chain-cell-title">{def.label}</div>
                <div className="kill-chain-cell-meta">
                  <span>告警 {alerts}</span>
                  <span>回合 {snap.round ?? state?.round ?? "—"}</span>
                </div>
              </div>
            </Tooltip>
          );
        })}
      </div>
    </div>
  );
}
