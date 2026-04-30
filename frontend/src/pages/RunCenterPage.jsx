import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Card, Collapse, Input, InputNumber, Space, Table, Tag, Typography, message } from "antd";
import { httpJson, downloadJson } from "../lib/api";

function labelTip(lab) {
  const v = String(lab || "").toUpperCase();
  if (!v) return "该标签用于标识该运行记录的统计特征。";
  if (v.includes("B WINRATE MAX")) return "该运行记录为蓝方胜率最高的结果。";
  if (v.includes("R WINRATE MAX")) return "该运行记录为红方胜率最高的结果。";
  if (v.includes("SCORE MAX")) return "该运行记录为评分最高的结果。";
  if (v.includes("SCORE MIN")) return "该运行记录为评分最低的结果。";
  return `标签含义：${lab}`;
}

function fmtTime(ms) {
  try {
    return ms ? new Date(ms).toLocaleString() : "—";
  } catch {
    return "—";
  }
}

export default function RunCenterPage() {
  const [loading, setLoading] = useState(false);
  const [replaying, setReplaying] = useState(false);
  const [replayingRequestId, setReplayingRequestId] = useState("");
  const [scenarioId, setScenarioId] = useState("");
  const [limit, setLimit] = useState(20);
  const [runs, setRuns] = useState([]);
  const [selectedRequestId, setSelectedRequestId] = useState("");
  const [visibleCount, setVisibleCount] = useState(12);
  const [detail, setDetail] = useState({});
  const [replaySeed, setReplaySeed] = useState(null);
  const [replayRounds, setReplayRounds] = useState(null);
  const [replayResult, setReplayResult] = useState({});

  const visibleRuns = useMemo(() => (runs || []).slice(0, visibleCount), [runs, visibleCount]);

  const copyText = async (value, label) => {
    if (value == null || value === "") {
      message.warning(`${label || "内容"}为空`);
      return;
    }
    try {
      await navigator.clipboard.writeText(String(value));
      message.success(`已复制 ${label || ""}`);
    } catch {
      message.error("复制失败");
    }
  };

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const qs = new URLSearchParams();
      if (scenarioId) qs.set("scenarioId", scenarioId);
      qs.set("limit", String(limit || 20));
      const list = await httpJson(`/combat/v3/commander/runs?${qs.toString()}`);
      setRuns(Array.isArray(list) ? list : []);
      setVisibleCount(12);
    } catch (e) {
      message.error(e.message || "刷新失败");
    } finally {
      setLoading(false);
    }
  }, [scenarioId, limit]);

  useEffect(() => {
    refresh().catch(() => {});
  }, []);

  const loadActive = async () => {
    try {
      const a = await httpJson("/combat/scenario-data/active");
      const id = a?.activeScenarioId ? String(a.activeScenarioId) : "";
      setScenarioId(id);
      message.info(`已读取激活想定：${id || "—"}`);
    } catch (e) {
      message.error(e.message || "读取失败");
    }
  };

  const openRun = async (requestId) => {
    if (!requestId) return;
    try {
      const d = await httpJson(`/combat/v3/commander/runs/${encodeURIComponent(requestId)}`);
      setDetail(d || {});
      setSelectedRequestId(requestId);
      setReplaySeed(null);
      setReplayRounds(null);
    } catch (e) {
      message.error(e.message || "读取失败");
    }
  };

  const replay = async (requestId) => {
    if (!requestId) return;
    setReplaying(true);
    setReplayingRequestId(requestId);
    try {
      const body = {
        seed: replaySeed != null && replaySeed !== "" ? Number(replaySeed) : null,
        rounds: replayRounds != null && replayRounds !== "" ? Number(replayRounds) : null,
        persist: false
      };
      const out = await httpJson(`/combat/v3/commander/runs/${encodeURIComponent(requestId)}/replay`, {
        method: "POST",
        body: JSON.stringify(body)
      });
      setReplayResult(out || {});
      message.success("回放完成（未落库）");
    } catch (e) {
      message.error(e.message || "回放失败");
    } finally {
      setReplaying(false);
      setReplayingRequestId("");
    }
  };

  const columns = [
    { title: "createdAt", dataIndex: "createdAt", key: "createdAt", render: (v) => <span className="text-slate-400">{fmtTime(v)}</span> },
    {
      title: "requestId",
      dataIndex: "requestId",
      key: "requestId",
      render: (v) => (
        <Space>
          <Typography.Text strong>{v}</Typography.Text>
          <Button size="small" onClick={() => copyText(v, "requestId")}>
            复制
          </Button>
        </Space>
      )
    },
    {
      title: "scenario",
      dataIndex: "scenarioId",
      key: "scenarioId",
      render: (v) => (
        <Space>
          <span className="text-slate-400">{v || "—"}</span>
          {v ? (
            <Button size="small" onClick={() => copyText(v, "scenarioId")}>
              复制
            </Button>
          ) : null}
        </Space>
      )
    },
    {
      title: "label",
      dataIndex: "label",
      key: "label",
      render: (v) => (
        <Tag color="success" title={labelTip(v)}>
          {v || "—"}
        </Tag>
      )
    },
    {
      title: "操作",
      key: "actions",
      render: (_, r) => (
        <Space onClick={(e) => e.stopPropagation()}>
          <Button size="small" onClick={() => openRun(r.requestId)}>
            详情
          </Button>
          <Button
            type="primary"
            size="small"
            loading={replaying && replayingRequestId === r.requestId}
            onClick={() => replay(r.requestId)}
          >
            回放
          </Button>
        </Space>
      )
    }
  ];

  return (
    <div className="container" style={{ padding: 16, maxWidth: 1400, margin: "0 auto" }}>
      <Typography.Title level={4} style={{ marginTop: 0, color: "#e7edf7" }}>
        运行回放中心
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ color: "#9fb0cc" }}>
        运行列表 <code>/combat/v3/commander/runs</code> · 详情与回放与旧静态页等价。
      </Typography.Paragraph>

      <div style={{ display: "grid", gap: 14, gridTemplateColumns: "1fr 1fr" }}>
        <Card title="运行列表" size="small">
          <Space wrap style={{ marginBottom: 12 }}>
            <span className="text-slate-400">scenario</span>
            <Typography.Text strong style={{ color: "#e7edf7" }}>
              {scenarioId || "全部"}
            </Typography.Text>
          </Space>
          <Space wrap style={{ marginBottom: 12 }}>
            <div>
              <div className="text-xs text-slate-400 mb-1">scenarioId（可选）</div>
              <Input
                value={scenarioId}
                onChange={(e) => setScenarioId(e.target.value)}
                placeholder="留空=全部"
                style={{ width: 220 }}
              />
            </div>
            <div>
              <div className="text-xs text-slate-400 mb-1">limit</div>
              <InputNumber min={1} max={100} value={limit} onChange={(v) => setLimit(v ?? 20)} />
            </div>
          </Space>
          <Space wrap style={{ marginBottom: 12 }}>
            <Button onClick={loadActive}>读取激活想定</Button>
            <Button type="primary" loading={loading} onClick={() => refresh()}>
              刷新列表
            </Button>
          </Space>
          <Table
            size="small"
            rowKey="requestId"
            columns={columns}
            dataSource={visibleRuns}
            pagination={false}
            locale={{ emptyText: "暂无运行记录" }}
            onRow={(record) => ({
              onClick: () => openRun(record.requestId),
              style: {
                cursor: "pointer",
                background: selectedRequestId === record.requestId ? "rgba(49, 205, 123, 0.12)" : undefined
              }
            })}
          />
          {runs.length > visibleCount ? (
            <Button block style={{ marginTop: 8 }} onClick={() => setVisibleCount((c) => c + 12)}>
              加载更多（{visibleCount}/{runs.length}）
            </Button>
          ) : null}
        </Card>

        <Card title="运行详情" size="small">
          {!detail.requestId ? (
            <Typography.Text type="secondary">请从左侧选择一条运行记录。</Typography.Text>
          ) : (
            <Space direction="vertical" style={{ width: "100%" }} size="middle">
              <Space wrap>
                <div>
                  <div className="text-xs text-slate-400">requestId</div>
                  <Input value={detail.requestId} readOnly />
                </div>
                <div>
                  <div className="text-xs text-slate-400">seed / engineVersion</div>
                  <Input
                    readOnly
                    value={`${detail.seed != null ? detail.seed : "—"} / ${detail.engineVersion || "—"}`}
                  />
                </div>
              </Space>
              <Space wrap>
                <div>
                  <div className="text-xs text-slate-400">label / rounds</div>
                  <Input
                    readOnly
                    value={`${detail.label || "—"} / ${detail.roundsRequested || (detail.rounds?.length ?? 0)}`}
                  />
                </div>
                <div>
                  <div className="text-xs text-slate-400">目标（goal）</div>
                  <Input value={detail.goal || ""} readOnly />
                </div>
              </Space>
              <Space wrap>
                <Button type="primary" loading={replaying} onClick={() => replay(detail.requestId)}>
                  按记录回放复现
                </Button>
                <Button
                  onClick={() => {
                    downloadJson(`commander-run-${detail.requestId}.json`, detail);
                    message.success("已导出");
                  }}
                >
                  导出 run 记录
                </Button>
                <Button onClick={() => copyText(detail.requestId, "requestId")}>复制 requestId</Button>
              </Space>
              <div>
                <div className="text-xs text-slate-400 mb-1">战报摘要（report）</div>
                <Collapse
                  size="small"
                  items={[
                    {
                      key: "rep",
                      label: "JSON",
                      children: (
                        <pre
                          style={{
                            maxHeight: 320,
                            overflow: "auto",
                            margin: 0,
                            fontSize: 12,
                            color: "#cfe0ff"
                          }}
                        >
                          {JSON.stringify(detail.report, null, 2)}
                        </pre>
                      )
                    }
                  ]}
                />
              </div>
            </Space>
          )}
        </Card>
      </div>

      <Card title="回放结果（/runs/{id}/replay）" size="small" style={{ marginTop: 14 }}>
        <Typography.Paragraph type="secondary" style={{ color: "#9fb0cc", fontSize: 12 }}>
          默认不落库，仅用于验证同 seed 同输入可复现。
        </Typography.Paragraph>
        <Space wrap style={{ marginBottom: 12 }}>
          <div>
            <div className="text-xs text-slate-400 mb-1">回放 seed（可覆盖）</div>
            <InputNumber
              value={replaySeed}
              onChange={setReplaySeed}
              placeholder="留空则使用记录 seed"
              style={{ width: 200 }}
            />
          </div>
          <div>
            <div className="text-xs text-slate-400 mb-1">rounds（可覆盖）</div>
            <InputNumber min={1} max={60} value={replayRounds} onChange={setReplayRounds} placeholder="留空则使用记录" />
          </div>
        </Space>
        <Collapse
          size="small"
          defaultActiveKey={["res"]}
          items={[
            {
              key: "res",
              label: "回放结果 JSON",
              extra: (
                <Button
                  size="small"
                  type="link"
                  onClick={(e) => {
                    e.stopPropagation();
                    copyText(JSON.stringify(replayResult, null, 2), "回放结果");
                  }}
                >
                  复制
                </Button>
              ),
              children: (
                <pre style={{ maxHeight: 400, overflow: "auto", margin: 0, fontSize: 12, color: "#cfe0ff" }}>
                  {JSON.stringify(replayResult, null, 2)}
                </pre>
              )
            }
          ]}
        />
      </Card>
    </div>
  );
}
