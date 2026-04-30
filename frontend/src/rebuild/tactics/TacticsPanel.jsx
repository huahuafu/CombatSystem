import React, { useMemo, useState } from "react";
import { Alert, Button, Card, Col, Row, Space, Timeline } from "antd";
import { httpJson, pct } from "../../lib/api";

const PLAN_META = {
  WIN_MAX: "胜率最高",
  LOSS_MIN: "损耗最小",
  SPEED_MAX: "速度最快",
  BALANCED: "均衡最优"
};

export default function TacticsPanel({ activeScenarioId, goal, commanderSide, commanderRole, onRunEvents }) {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(false);
  const [running, setRunning] = useState(false);
  const [selected, setSelected] = useState("");

  const selectedPlan = useMemo(() => plans.find((x) => x.label === selected) || null, [plans, selected]);

  async function generatePlans() {
    if (!activeScenarioId) return;
    setLoading(true);
    try {
      const data = await httpJson("/combat/v3/commander/strategies/generate", {
        method: "POST",
        body: JSON.stringify({ scenarioId: activeScenarioId, goal, commanderSide, commanderRole, scorePerspective: commanderSide })
      });
      setPlans(Array.isArray(data.strategies) ? data.strategies : []);
      setSelected("WIN_MAX");
    } finally {
      setLoading(false);
    }
  }

  async function executePlan() {
    if (!selectedPlan) return;
    setRunning(true);
    try {
      const result = await httpJson("/combat/v3/commander/strategies/execute", {
        method: "POST",
        body: JSON.stringify({
          scenarioId: activeScenarioId,
          goal,
          label: selectedPlan.label,
          strategyId: selectedPlan.strategyId,
          strategy: selectedPlan,
          rounds: 15
        })
      });
      const phases = Array.isArray(result?.report?.phaseReports) ? result.report.phaseReports : [];
      const events = phases.flatMap((p) => (Array.isArray(p.events) ? p.events : []));
      onRunEvents(events);
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="space-y-3">
      {!activeScenarioId ? <Alert type="warning" showIcon message="请先激活想定后再生成策略" /> : null}
      <Space>
        <Button type="primary" loading={loading} onClick={generatePlans} disabled={!activeScenarioId}>
          生成四类策略
        </Button>
        <Button loading={running} onClick={executePlan} disabled={!selectedPlan}>
          执行所选策略
        </Button>
      </Space>
      <Row gutter={12}>
        {Object.keys(PLAN_META).map((label) => {
          const p = plans.find((x) => x.label === label);
          return (
            <Col span={6} key={label}>
              <Card
                hoverable
                onClick={() => p && setSelected(label)}
                style={{ borderColor: selected === label ? "#1677ff" : undefined }}
                title={PLAN_META[label]}
              >
                {p ? (
                  <>
                    <div>策略：{p.strategyName}</div>
                    <div>胜率：{pct(p.predictedWinRate)}</div>
                    <div>战损：{p.predictedExpectedLoss?.toFixed?.(2) ?? p.predictedExpectedLoss}</div>
                    <div>达成：{pct(p.predictedMissionSuccessRate)}</div>
                  </>
                ) : (
                  <div className="text-slate-400">待生成</div>
                )}
              </Card>
            </Col>
          );
        })}
      </Row>

      <Card title="策略时序">
        {selectedPlan?.tempoPlan?.length ? (
          <Timeline items={selectedPlan.tempoPlan.map((x) => ({ children: `T+${x.tPlusMin}m ${x.milestone} - ${x.expectedOutcome}` }))} />
        ) : (
          <div className="text-slate-400">暂无时序数据</div>
        )}
      </Card>
    </div>
  );
}
