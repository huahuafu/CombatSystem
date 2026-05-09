import React, { useMemo } from "react";
import { Card, Col, Row, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import ReactECharts from "echarts-for-react";
import { useInfoStore } from "../../store/infoStore";
import { useSimulationStore, type CombatSide } from "../../store/simulationStore";
import type { InfoReplaySlice, RoundInfoDigest } from "../info/infoTypes";
import ReplayInsightPanel from "./ReplayInsightPanel";

const { Text } = Typography;

function digestToReplaySlice(r: RoundInfoDigest, commanderSide: CombatSide): InfoReplaySlice {
  const b = r.battle;
  const red = b?.redCombatPower ?? 0;
  const blue = b?.blueCombatPower ?? 0;
  const sum = red + blue;
  const ownCombatShare01 =
    sum <= 0 ? 0.5 : commanderSide === "RED" ? red / sum : blue / sum;

  const rs = r.roundStatSnapshot;
  const hpSum = (rs?.redTotalHp ?? 0) + (rs?.blueTotalHp ?? 0);
  const serverOwnHpShare01 =
    rs && hpSum > 0
      ? commanderSide === "RED"
        ? rs.redTotalHp / hpSum
        : rs.blueTotalHp / hpSum
      : 0.5;

  return {
    round: r.round,
    detectionCount: r.detections.length,
    jamEventsNote: `探测效率×${r.ewSummary.avgDetectionEfficiency.toFixed(2)} · 干扰压制圈内单位 ${r.ewSummary.unitsInEnemyJam}`,
    datalinkUpRatio: r.datalinkIntegrity,
    infoAdvantage: r.infoAdvantage,
    ownCombatShare01,
    redCombatPower: red,
    blueCombatPower: blue,
    serverOwnHpShare01,
    statRedHp: rs?.redTotalHp ?? 0,
    statBlueHp: rs?.blueTotalHp ?? 0,
    interactionCount: rs?.interactionEventCount ?? 0
  };
}

export default function InfoReplaySection() {
  /** 必须订阅稳定引用；勿在 selector 中调用 getReplaySlices()，否则每次返回新数组会触发无限重渲染（React #185）。 */
  const roundArchive = useInfoStore((s) => s.roundArchive);
  const boundScenarioId = useInfoStore((s) => s.boundScenarioId);
  const commanderSide = useSimulationStore((s) => s.commanderSide);

  const slices = useMemo(
    () => roundArchive.map((d) => digestToReplaySlice(d, commanderSide)),
    [roundArchive, commanderSide]
  );

  const maxTotalBattlePower = useMemo(() => {
    let m = 1;
    for (const s of slices) {
      const t = s.redCombatPower + s.blueCombatPower;
      if (t > m) m = t;
    }
    return m;
  }, [slices]);

  const maxInteractionCount = useMemo(() => {
    let m = 1;
    for (const s of slices) {
      if (s.interactionCount > m) m = s.interactionCount;
    }
    return m;
  }, [slices]);

  const chartOption = useMemo(
    () => ({
      backgroundColor: "transparent",
      textStyle: { color: "#cbd5e1" },
      tooltip: { trigger: "axis" },
      legend: {
        type: "scroll" as const,
        textStyle: { color: "#94a3b8" },
        data: [
          "信息优势",
          "数据链通畅",
          "己方战力占比(兵力)",
          "后端己方血量占比",
          "战场总战力(相对峰值)",
          "探测强度(归一)",
          "交互事件(归一)"
        ]
      },
      xAxis: {
        type: "category",
        data: slices.map((s) => `R${s.round}`),
        axisLabel: { color: "#94a3b8" }
      },
      yAxis: { type: "value", min: 0, max: 1, axisLabel: { color: "#94a3b8" } },
      series: [
        {
          name: "信息优势",
          type: "line",
          smooth: true,
          data: slices.map((s) => s.infoAdvantage),
          itemStyle: { color: "#38bdf8" }
        },
        {
          name: "数据链通畅",
          type: "line",
          smooth: true,
          data: slices.map((s) => s.datalinkUpRatio),
          itemStyle: { color: "#a78bfa" }
        },
        {
          name: "己方战力占比(兵力)",
          type: "line",
          smooth: true,
          data: slices.map((s) => s.ownCombatShare01),
          itemStyle: { color: "#4ade80" }
        },
        {
          name: "后端己方血量占比",
          type: "line",
          smooth: true,
          data: slices.map((s) => s.serverOwnHpShare01),
          lineStyle: { type: "dashed" as const, width: 1.5 },
          itemStyle: { color: "#22c55e" }
        },
        {
          name: "战场总战力(相对峰值)",
          type: "line",
          smooth: true,
          data: slices.map((s) =>
            maxTotalBattlePower > 0
              ? (s.redCombatPower + s.blueCombatPower) / maxTotalBattlePower
              : 0
          ),
          lineStyle: { type: "dashed" as const, width: 1.2 },
          itemStyle: { color: "#f97316" }
        },
        {
          name: "探测强度(归一)",
          type: "bar",
          data: slices.map((s) => Math.min(1, s.detectionCount / 10)),
          itemStyle: { color: "#fbbf24", opacity: 0.42 }
        },
        {
          name: "交互事件(归一)",
          type: "bar",
          data: slices.map((s) =>
            maxInteractionCount > 0 ? Math.min(1, s.interactionCount / maxInteractionCount) : 0
          ),
          itemStyle: { color: "#f472b6", opacity: 0.38 }
        }
      ]
    }),
    [slices, maxTotalBattlePower, maxInteractionCount]
  );

  const columns: ColumnsType<InfoReplaySlice> = [
    { title: "回合", dataIndex: "round", key: "round", width: 52 },
    { title: "探测", dataIndex: "detectionCount", key: "detectionCount", width: 44 },
    {
      title: "数据链",
      dataIndex: "datalinkUpRatio",
      key: "dl",
      width: 56,
      render: (v: number) => `${(v * 100).toFixed(0)}%`
    },
    {
      title: "信息优势",
      dataIndex: "infoAdvantage",
      key: "ia",
      width: 68,
      render: (v: number) => <Tag color="cyan">{(v * 100).toFixed(0)}%</Tag>
    },
    {
      title: "己方战力%",
      dataIndex: "ownCombatShare01",
      key: "own",
      width: 76,
      render: (v: number) => `${(v * 100).toFixed(0)}%`
    },
    {
      title: "后端血量%",
      dataIndex: "serverOwnHpShare01",
      key: "srvhp",
      width: 82,
      render: (v: number) => `${(v * 100).toFixed(0)}%`
    },
    {
      title: "交互",
      dataIndex: "interactionCount",
      key: "intr",
      width: 44,
      render: (v: number) => <span style={{ fontSize: 11 }}>{v}</span>
    },
    {
      title: "红/蓝战力",
      key: "rb",
      width: 96,
      render: (_, row) => (
        <span style={{ fontSize: 11 }}>
          {Math.round(row.redCombatPower)} / {Math.round(row.blueCombatPower)}
        </span>
      )
    },
    {
      title: "后端HP",
      key: "hp",
      width: 96,
      render: (_, row) => (
        <span style={{ fontSize: 11 }}>
          {Math.round(row.statRedHp)} / {Math.round(row.statBlueHp)}
        </span>
      )
    },
    { title: "电磁对抗摘要", dataIndex: "jamEventsNote", key: "jam", ellipsis: true }
  ];

  return (
    <Card
      size="small"
      title="综合评估 · 信息与战报（会话内）"
      style={{ marginTop: 14 }}
      extra={boundScenarioId ? <Tag color="blue">想定 {boundScenarioId.slice(0, 8)}…</Tag> : null}
    >
      <Typography.Paragraph type="secondary" style={{ fontSize: 12, color: "#9fb0cc" }}>
        信息域来自前端摘要；兵力战力来自当前兵力 combatPower；后端曲线来自{" "}
        <Text code>/combat/stats</Text>（RoundStat）与{" "}
        <Text code>/combat/interaction-event/list</Text> 按回合聚合。交互事件条数与引擎内{" "}
        <Text code>event.round === digest.round</Text> 对齐；RoundStat 优先匹配{" "}
        <Text code>digest.round - 1</Text>。指挥侧：{commanderSide}。旧 session 存档可能缺后端字段。
      </Typography.Paragraph>
      <ReplayInsightPanel />
      {!slices.length ? (
        <Text type="secondary">暂无复盘数据：请先激活想定并在「仿真运行」页刷新或推进回合。</Text>
      ) : (
        <Row gutter={12}>
          <Col span={24}>
            <ReactECharts option={chartOption} style={{ height: 400 }} />
          </Col>
          <Col span={24} style={{ marginTop: 8 }}>
            <Table<InfoReplaySlice>
              size="small"
              rowKey={(r) => String(r.round)}
              columns={columns}
              dataSource={slices}
              pagination={{ pageSize: 8, showSizeChanger: false }}
              scroll={{ x: 980 }}
            />
          </Col>
        </Row>
      )}
    </Card>
  );
}
