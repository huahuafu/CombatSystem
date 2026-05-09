import React, { useMemo } from "react";
import { Alert, Space } from "antd";
import { useInfoStore } from "../../store/infoStore";
import { useSimulationStore } from "../../store/simulationStore";
import { buildReplayInsights } from "./replayInsightRules";

const levelToType = (level: string): "info" | "warning" | "success" => {
  if (level === "warning") return "warning";
  if (level === "success") return "success";
  return "info";
};

export default function ReplayInsightPanel() {
  const roundArchive = useInfoStore((s) => s.roundArchive);
  const commanderSide = useSimulationStore((s) => s.commanderSide);

  const insights = useMemo(
    () => buildReplayInsights(roundArchive, commanderSide),
    [roundArchive, commanderSide]
  );

  return (
    <Space direction="vertical" size={8} style={{ width: "100%", marginBottom: 12 }}>
      {insights.map((ins, i) => (
        <Alert
          key={i}
          type={levelToType(ins.level)}
          showIcon
          message={ins.text}
          style={{ fontSize: 12 }}
        />
      ))}
    </Space>
  );
}
