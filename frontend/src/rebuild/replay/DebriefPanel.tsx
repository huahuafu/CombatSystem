import React, { useState } from "react";
import { Alert, Button, Card, Space, Tag, Typography, message } from "antd";
import { httpJson } from "../../lib/api";
import { useInfoStore } from "../../store/infoStore";
import { useSimulationStore } from "../../store/simulationStore";
import { buildDebriefRequestPayload } from "./buildDebriefRequest";

const { Paragraph, Text } = Typography;

interface DebriefResponse {
  bullets?: string[];
  narrative?: string;
  source?: string;
}

export default function DebriefPanel() {
  const roundArchive = useInfoStore((s) => s.roundArchive);
  const boundScenarioId = useInfoStore((s) => s.boundScenarioId);
  const commanderSide = useSimulationStore((s) => s.commanderSide);

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<DebriefResponse | null>(null);

  const runDebrief = async () => {
    if (!boundScenarioId) {
      message.warning("未绑定想定：请先「读取激活想定」或在仿真页刷新。");
      return;
    }
    if (!roundArchive.length) {
      message.warning("无回合归档：请先在「仿真运行」页刷新或推进杀伤链。");
      return;
    }
    setLoading(true);
    try {
      const st = await httpJson("/combat/v2/simulation/state");
      const payload = buildDebriefRequestPayload(
        roundArchive,
        commanderSide,
        st?.winner,
        st?.winReason
      );
      const out = await httpJson("/combat/v2/simulation/debrief", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      setResult(out || {});
      message.success("战后简报已生成");
    } catch (e) {
      message.error(e instanceof Error ? e.message : "简报生成失败");
      setResult(null);
    } finally {
      setLoading(false);
    }
  };

  const src = String(result?.source || "").toUpperCase();
  const tagColor = src === "HYBRID" ? "processing" : "default";

  return (
    <Card size="small" title="战后简报（阶段 B · 服务端）" style={{ marginBottom: 14 }}>
      <Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 10 }}>
        调用 <Text code>/combat/v2/simulation/debrief</Text>
        ：服务端规则要点 + 可选 LLM 叙事（需在{" "}
        <Text code>application.properties</Text> 设置{" "}
        <Text code>combat.debrief.llm.enabled=true</Text>，并与 DashScope/OpenAI 兼容配置共用）。
      </Paragraph>
      <Space wrap style={{ marginBottom: result ? 12 : 0 }}>
        <Button type="primary" loading={loading} onClick={runDebrief}>
          生成战后简报
        </Button>
        {result?.source ? (
          <Tag color={tagColor}>来源：{result.source}</Tag>
        ) : null}
      </Space>
      {result?.bullets?.length ? (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 10 }}
          message="要点"
          description={
            <ul style={{ margin: "6px 0 0 18px", padding: 0, fontSize: 12 }}>
              {result.bullets.map((b, i) => (
                <li key={i} style={{ marginBottom: 4 }}>
                  {b}
                </li>
              ))}
            </ul>
          }
        />
      ) : null}
      {result?.narrative ? (
        <Paragraph style={{ fontSize: 13, marginBottom: 0, whiteSpace: "pre-wrap" }}>
          <Text strong>综述：</Text>
          {result.narrative}
        </Paragraph>
      ) : null}
    </Card>
  );
}
