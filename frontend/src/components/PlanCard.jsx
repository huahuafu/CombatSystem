import React from "react";
import { Tag } from "antd";
import { num, pct } from "../lib/api";

export default function PlanCard({ plan, meta, selected, onSelect, recommended }) {
  const invalid = plan?.invalidReason;
  return (
    <article className={`x-plan ${selected ? "selected" : ""}`} style={{ position: "relative" }}>
      {recommended ? (
        <Tag color="gold" style={{ position: "absolute", top: 8, right: 8, zIndex: 1, margin: 0 }}>
          推荐方案
        </Tag>
      ) : null}
      <span className={`x-badge ${meta.cls}`}>{meta.title}</span>
      <div className="kv"><span>战术名</span><strong>{plan?.strategyName || "待生成"}</strong></div>
      <div className="kv"><span>预计胜率</span><strong>{plan ? pct(plan.projectedWinRate) : "--"}</strong></div>
      <div className="x-track"><div className="x-fill" style={{ width: `${Math.min(100, Math.max(5, (plan?.projectedWinRate || 0) * 100))}%` }} /></div>
      <div className="kv"><span>预计战损</span><strong>{plan ? num(plan.expectedLoss) : "--"}</strong></div>
      <div className="kv"><span>任务达成</span><strong>{plan ? pct(plan.missionSuccessRate) : "--"}</strong></div>
      <div className="kv"><span>部署/路线/时序</span><strong>{plan ? `${plan.deploymentPlan?.length || 0}/${plan.routePlan?.length || 0}/${plan.tempoPlan?.length || 0}` : "--"}</strong></div>
      {invalid ? <div className="muted" style={{ color: "#ff9090" }}>{invalid}</div> : null}
      <button onClick={onSelect} disabled={!plan || !!invalid}>选择方案</button>
    </article>
  );
}
