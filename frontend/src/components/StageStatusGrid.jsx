import React from "react";

export default function StageStatusGrid({ stages, state, snapshots, stageAlertCount }) {
  return (
    <div className="cards">
      {stages.map((k) => {
        const s = snapshots[k] || {};
        const isLow = String(s.readinessLevel || "").toUpperCase() === "LOW" || stageAlertCount(k) > 0;
        const lv = String(s.readinessLevel || "").toUpperCase();
        return (
          <div key={k} className={`card sim-stage-card ${isLow ? "is-low" : ""}`}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <strong>{k.toUpperCase()}</strong>
              <span className={`pill ${lv === "HIGH" ? "ok" : lv === "LOW" ? "err" : "warn"}`}>{s.readinessLevel || "—"}</span>
            </div>
            <div className="kv"><span>回合</span><strong>{s.round ?? state.round ?? "—"}</strong></div>
            <div className="kv"><span>状态</span><strong>{s.status || s.phaseStatus || "—"}</strong></div>
            <div className="kv"><span>告警</span><strong>{stageAlertCount(k)}</strong></div>
          </div>
        );
      })}
    </div>
  );
}
