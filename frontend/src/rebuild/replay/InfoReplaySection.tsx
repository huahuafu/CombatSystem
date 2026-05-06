import React, { useMemo } from "react";
import { Card, Col, Row, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import ReactECharts from "echarts-for-react";
import { useInfoStore } from "../../store/infoStore";
import type { InfoReplaySlice, RoundInfoDigest } from "../info/infoTypes";

const { Text } = Typography;

function digestToReplaySlice(r: RoundInfoDigest): InfoReplaySlice {
  return {
    round: r.round,
    detectionCount: r.detections.length,
    jamEventsNote: `探测效率×${r.ewSummary.avgDetectionEfficiency.toFixed(2)} · 干扰压制圈内单位 ${r.ewSummary.unitsInEnemyJam}`,
    datalinkUpRatio: r.datalinkIntegrity,
    infoAdvantage: r.infoAdvantage
  };
}

export default function InfoReplaySection() {
  /** 必须订阅稳定引用；勿在 selector 中调用 getReplaySlices()，否则每次返回新数组会触发无限重渲染（React #185）。 */
  const roundArchive = useInfoStore((s) => s.roundArchive);
  const boundScenarioId = useInfoStore((s) => s.boundScenarioId);
  const slices = useMemo(() => roundArchive.map(digestToReplaySlice), [roundArchive]);

  const chartOption = useMemo(
    () => ({
      backgroundColor: "transparent",
      textStyle: { color: "#cbd5e1" },
      tooltip: { trigger: "axis" },
      legend: { textStyle: { color: "#94a3b8" }, data: ["信息优势", "数据链通畅", "探测批数"] },
      xAxis: {
        type: "category",
        data: slices.map((s) => `R${s.round}`),
        axisLabel: { color: "#94a3b8" }
      },
      yAxis: { type: "value", max: 1, axisLabel: { color: "#94a3b8" } },
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
          name: "探测批数",
          type: "bar",
          yAxisIndex: 0,
          data: slices.map((s) => Math.min(1, s.detectionCount / 10)),
          itemStyle: { color: "#fbbf24", opacity: 0.45 }
        }
      ]
    }),
    [slices]
  );

  const columns: ColumnsType<InfoReplaySlice> = [
    { title: "回合", dataIndex: "round", key: "round", width: 72 },
    { title: "探测批数", dataIndex: "detectionCount", key: "detectionCount", width: 92 },
    {
      title: "数据链",
      dataIndex: "datalinkUpRatio",
      key: "dl",
      render: (v: number) => `${(v * 100).toFixed(0)}%`
    },
    {
      title: "信息优势",
      dataIndex: "infoAdvantage",
      key: "ia",
      render: (v: number) => <Tag color="cyan">{(v * 100).toFixed(0)}%</Tag>
    },
    { title: "电磁对抗摘要", dataIndex: "jamEventsNote", key: "jam", ellipsis: true }
  ];

  return (
    <Card
      size="small"
      title="信息维度复盘（会话内）"
      style={{ marginTop: 14 }}
      extra={boundScenarioId ? <Tag color="blue">想定 {boundScenarioId.slice(0, 8)}…</Tag> : null}
    >
      <Typography.Paragraph type="secondary" style={{ fontSize: 12, color: "#9fb0cc" }}>
        数据来自仿真页刷新与回合推进时写入的摘要（sessionStorage 按想定分桶）。用于查看逐回合探测、干扰影响与数据链趋势。
      </Typography.Paragraph>
      {!slices.length ? (
        <Text type="secondary">暂无信息复盘数据：请先激活想定并在「仿真运行」页刷新或推进回合。</Text>
      ) : (
        <Row gutter={12}>
          <Col span={24}>
            <ReactECharts option={chartOption} style={{ height: 280 }} />
          </Col>
          <Col span={24} style={{ marginTop: 8 }}>
            <Table<InfoReplaySlice>
              size="small"
              rowKey={(r) => String(r.round)}
              columns={columns}
              dataSource={slices}
              pagination={{ pageSize: 8, showSizeChanger: false }}
            />
          </Col>
        </Row>
      )}
    </Card>
  );
}
