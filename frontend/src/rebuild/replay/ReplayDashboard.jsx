import React, { useEffect, useMemo, useState } from "react";
import { Button, Card, Col, Row, Slider, Space, Statistic, Timeline } from "antd";
import ReactECharts from "echarts-for-react";

export default function ReplayDashboard({ events }) {
  const [cursor, setCursor] = useState(0);
  const [playing, setPlaying] = useState(false);

  useEffect(() => {
    if (!playing) return;
    const timer = setInterval(() => {
      setCursor((c) => {
        const end = Math.max((events?.length || 1) - 1, 0);
        if (c >= end) {
          setPlaying(false);
          return end;
        }
        return c + 1;
      });
    }, 600);
    return () => clearInterval(timer);
  }, [events, playing]);

  const metrics = useMemo(() => {
    const total = events?.length || 0;
    const engage = (events || []).filter((x) => String(x.action || "").includes("ENGAGE")).length;
    const assess = (events || []).filter((x) => String(x.action || "").includes("ASSESS")).length;
    return { total, engage, assess };
  }, [events]);

  const cumulativeSeries = useMemo(() => {
    const total = events?.length || 0;
    const labels = Array.from({ length: total }, (_, i) => `${i + 1}`);
    const data = labels.map((_, i) => i + 1);
    return { labels, data };
  }, [events]);

  const chartOption = useMemo(
    () => ({
      tooltip: { trigger: "axis" },
      xAxis: { type: "category", data: ["总事件", "交战阶段", "评估阶段"] },
      yAxis: { type: "value" },
      series: [{ type: "bar", data: [metrics.total, metrics.engage, metrics.assess] }]
    }),
    [metrics]
  );

  const timelineOption = useMemo(
    () => ({
      tooltip: { trigger: "axis" },
      xAxis: { type: "category", data: cumulativeSeries.labels },
      yAxis: { type: "value" },
      series: [{ type: "line", smooth: true, data: cumulativeSeries.data, areaStyle: {} }]
    }),
    [cumulativeSeries]
  );

  const visibleEvents = useMemo(() => (events || []).slice(0, cursor + 1), [events, cursor]);

  return (
    <Row gutter={12}>
      <Col span={8}>
        <Card><Statistic title="事件总数" value={metrics.total} /></Card>
      </Col>
      <Col span={8}>
        <Card><Statistic title="交战事件" value={metrics.engage} /></Card>
      </Col>
      <Col span={8}>
        <Card><Statistic title="评估事件" value={metrics.assess} /></Card>
      </Col>
      <Col span={24} style={{ marginTop: 12 }}>
        <Card title="推演复盘态势图">
          <ReactECharts option={chartOption} style={{ height: 360 }} />
        </Card>
      </Col>
      <Col span={24} style={{ marginTop: 12 }}>
        <Card title="回放控制">
          <Space>
            <Button type="primary" onClick={() => setPlaying((x) => !x)} disabled={!events?.length}>
              {playing ? "暂停" : "播放"}
            </Button>
            <Button onClick={() => { setPlaying(false); setCursor(0); }} disabled={!events?.length}>
              复位
            </Button>
          </Space>
          <div style={{ marginTop: 12 }}>
            <Slider
              min={0}
              max={Math.max((events?.length || 1) - 1, 0)}
              value={cursor}
              onChange={setCursor}
              disabled={!events?.length}
            />
          </div>
        </Card>
      </Col>
      <Col span={14} style={{ marginTop: 12 }}>
        <Card title="事件累计曲线">
          <ReactECharts option={timelineOption} style={{ height: 280 }} />
        </Card>
      </Col>
      <Col span={10} style={{ marginTop: 12 }}>
        <Card title="事件时间线">
          {visibleEvents.length ? (
            <Timeline items={visibleEvents.map((e, i) => ({ children: `#${i + 1} ${e.message || e.action || "阶段动作"}` }))} />
          ) : (
            <div className="text-slate-400">暂无事件</div>
          )}
        </Card>
      </Col>
    </Row>
  );
}
