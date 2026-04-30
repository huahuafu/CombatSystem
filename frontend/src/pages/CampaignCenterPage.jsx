import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Alert, Button, Card, Collapse, Input, Progress, Space, Typography, message } from "antd";
import { httpJson } from "../lib/api";

const SAMPLE = {
  _comment: "示例字段：name/phases/objectives/events 为常见结构",
  name: "自定义战役（示例）",
  phases: ["PHASE_1", "PHASE_2"],
  objectives: [
    { name: "红方保持在场", phase: "PHASE_1", targetId: "SIDE_ALIVE_GE:RED:1", completed: false }
  ],
  events: [
    {
      name: "蓝方减员触发",
      triggerCondition: "SIDE_ALIVE_LE:BLUE:2",
      fired: false,
      message: "蓝方减员到临界，战役态势升级。"
    }
  ]
};

function phaseCurrent(phase) {
  if (!phase) return "";
  if (typeof phase === "string") return phase;
  return phase.currentPhase || phase.phase || phase.name || phase.current || "";
}

function completeSummary(complete) {
  if (typeof complete === "boolean") return complete ? "已完成" : "未完成";
  if (complete && typeof complete === "object") {
    if (typeof complete.complete === "boolean") return complete.complete ? "已完成" : "未完成";
    if (typeof complete.isComplete === "boolean") return complete.isComplete ? "已完成" : "未完成";
  }
  return "—";
}

function phaseProgressVal(completeStr, currentPhase, hasCampaign) {
  if (completeStr === "已完成") return 100;
  const p = currentPhase || "";
  const m = String(p).match(/(\d+)/);
  if (m) {
    const val = Number(m[1]);
    if (Number.isFinite(val) && val > 0) return Math.min(95, Math.max(10, val * 20));
  }
  return hasCampaign ? 35 : 0;
}

export default function CampaignCenterPage() {
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionLabel, setActionLabel] = useState("");
  const [actionProgress, setActionProgress] = useState(0);
  const actionTimerRef = useRef(null);
  const [submitting, setSubmitting] = useState(false);
  const [currentCampaign, setCurrentCampaign] = useState(null);
  const [phase, setPhase] = useState(null);
  const [complete, setComplete] = useState(null);
  const [customText, setCustomText] = useState("");

  const phaseSummary = useMemo(() => ({ current: phaseCurrent(phase) }), [phase]);
  const completeStr = useMemo(() => completeSummary(complete), [complete]);
  const barPct = useMemo(
    () => phaseProgressVal(completeStr, phaseSummary.current, !!currentCampaign),
    [completeStr, phaseSummary.current, currentCampaign]
  );

  const customError = useMemo(() => {
    if (!customText) return "";
    try {
      const obj = JSON.parse(customText);
      if (!obj || typeof obj !== "object") return "JSON 必须是对象结构。";
      if (!obj.name || typeof obj.name !== "string") return "缺少必填字段：name（字符串）。";
      if (!Array.isArray(obj.phases)) return "缺少必填字段：phases（数组）。";
      if (!Array.isArray(obj.objectives)) return "缺少必填字段：objectives（数组）。";
      if (!Array.isArray(obj.events)) return "缺少必填字段：events（数组）。";
      return "";
    } catch (e) {
      return `JSON 格式错误：${e?.message || "请检查逗号、引号与括号"}`;
    }
  }, [customText]);

  const stopActionProgress = useCallback((done = false) => {
    if (actionTimerRef.current) {
      clearInterval(actionTimerRef.current);
      actionTimerRef.current = null;
    }
    setActionProgress(done ? 100 : 0);
  }, []);

  const startActionProgress = useCallback(
    (label) => {
      stopActionProgress(false);
      setActionLabel(label || "处理中");
      setActionProgress(6);
      actionTimerRef.current = setInterval(() => {
        setActionProgress((p) => Math.min(92, p + 4));
      }, 220);
    },
    [stopActionProgress]
  );

  useEffect(() => {
    return () => {
      if (actionTimerRef.current) clearInterval(actionTimerRef.current);
    };
  }, []);

  const refreshAll = useCallback(async () => {
    setLoading(true);
    try {
      setCurrentCampaign(await httpJson("/combat/campaign/current"));
      setPhase(await httpJson("/combat/campaign/phase"));
      setComplete(await httpJson("/combat/campaign/is-complete"));
    } catch (e) {
      message.error(e.message || "刷新失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshAll().catch(() => {});
  }, [refreshAll]);

  const copyText = async (value, label) => {
    if (value == null || value === "") {
      message.warning(`${label || "内容"}为空`);
      return;
    }
    try {
      await navigator.clipboard.writeText(
        typeof value === "string" ? value : JSON.stringify(value, null, 2)
      );
      message.success("已复制");
    } catch {
      message.error("复制失败");
    }
  };

  const post = async (url, body, label) => {
    setActionLoading(true);
    startActionProgress(label || url);
    try {
      await httpJson(url, { method: "POST", body: JSON.stringify(body || {}) });
      stopActionProgress(true);
      message.success("操作成功");
      await refreshAll();
    } catch (e) {
      stopActionProgress(false);
      message.error(e.message || "操作失败");
    } finally {
      setActionLoading(false);
    }
  };

  const fillSample = () => {
    setCustomText(JSON.stringify(SAMPLE, null, 2));
  };

  const submitCustom = async () => {
    if (!customText) {
      message.warning("请先填写 JSON");
      return;
    }
    if (customError) {
      message.error(customError);
      return;
    }
    setSubmitting(true);
    try {
      const obj = JSON.parse(customText);
      await httpJson("/combat/campaign/custom", { method: "POST", body: JSON.stringify(obj) });
      message.success("自定义战役已写入会话");
      await refreshAll();
    } catch (e) {
      message.error(e.message || "提交失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="container" style={{ padding: 16, maxWidth: 1200, margin: "0 auto" }}>
      <Typography.Title level={4} style={{ marginTop: 0, color: "#e7edf7" }}>
        战役中心
      </Typography.Title>

      <Alert
        type="info"
        showIcon
        message="战役引导"
        description="先建立战役上下文，再进行阶段推进与会话管理。挂载前请先在重构工作台创建并激活想定。"
        style={{ marginBottom: 14 }}
      />

      <Card title="预设战役加载/挂载" size="small" style={{ marginBottom: 14 }}>
        <Space wrap>
          <Button
            type="primary"
            loading={actionLoading && actionLabel === "加载淮海战役"}
            onClick={() => post("/combat/campaign/load/huaihai", {}, "加载淮海战役")}
          >
            加载：淮海战役
          </Button>
          <Button
            type="primary"
            loading={actionLoading && actionLabel === "加载抗美援朝"}
            onClick={() => post("/combat/campaign/load/korean", {}, "加载抗美援朝")}
          >
            加载：抗美援朝
          </Button>
          <Button
            loading={actionLoading && actionLabel === "挂载淮海战役"}
            onClick={() => post("/combat/campaign/attach/huaihai", {}, "挂载淮海战役")}
          >
            挂载：淮海到当前想定
          </Button>
          <Button
            loading={actionLoading && actionLabel === "挂载抗美援朝"}
            onClick={() => post("/combat/campaign/attach/korean", {}, "挂载抗美援朝")}
          >
            挂载：抗美援朝到当前想定
          </Button>
        </Space>
        {(actionLoading || actionProgress === 100) && (
          <div style={{ marginTop: 12 }}>
            <Progress percent={actionProgress} size="small" status={actionLoading ? "active" : "success"} />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {actionLoading ? `执行中：${actionLabel}` : "操作完成"}
            </Typography.Text>
          </div>
        )}
      </Card>

      <div style={{ display: "grid", gap: 14, gridTemplateColumns: "1fr 1fr" }}>
        <Card title="当前战役详情" size="small">
          <Space size="large" style={{ marginBottom: 12 }}>
            <div>
              <div className="text-xs text-slate-400">当前阶段</div>
              <Typography.Text strong style={{ color: "#7dffb0", fontSize: 16 }}>
                {phaseSummary.current || "—"}
              </Typography.Text>
            </div>
            <div>
              <div className="text-xs text-slate-400">完成状态</div>
              <Typography.Text strong style={{ color: "#e7edf7", fontSize: 16 }}>
                {completeStr}
              </Typography.Text>
            </div>
          </Space>
          <Progress percent={barPct} size="small" style={{ marginBottom: 12 }} />
          <Collapse
            size="small"
            items={[
              {
                key: "c",
                label: "详情 JSON",
                extra: (
                  <Button
                    type="link"
                    size="small"
                    onClick={(e) => {
                      e.stopPropagation();
                      copyText(currentCampaign, "campaign");
                    }}
                  >
                    复制
                  </Button>
                ),
                children: (
                  <pre style={{ maxHeight: 240, overflow: "auto", margin: 0, fontSize: 12 }}>
                    {JSON.stringify(currentCampaign, null, 2)}
                  </pre>
                )
              }
            ]}
          />
        </Card>

        <Card title="会话控制" size="small">
          <Space wrap style={{ marginBottom: 12 }}>
            <Button loading={loading} onClick={() => refreshAll()} disabled={actionLoading}>
              刷新当前战役
            </Button>
            <Button
              type="primary"
              loading={actionLoading && actionLabel === "推进阶段"}
              onClick={() => post("/combat/campaign/phase/advance", {}, "推进阶段")}
            >
              推进阶段
            </Button>
            <Button
              loading={actionLoading && actionLabel === "检查事件/目标"}
              onClick={() => post("/combat/campaign/check-events", {}, "检查事件/目标")}
            >
              检查事件/目标
            </Button>
            <Button danger onClick={() => post("/combat/campaign/session/clear", {}, "清理会话")}>
              清理会话
            </Button>
          </Space>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <div>
              <div className="text-xs text-slate-400 mb-1">phase</div>
              <Collapse
                size="small"
                items={[
                  {
                    key: "p",
                    label: "JSON",
                    extra: (
                      <Button type="link" size="small" onClick={() => copyText(phase, "phase")}>
                        复制
                      </Button>
                    ),
                    children: (
                      <pre style={{ maxHeight: 160, overflow: "auto", margin: 0, fontSize: 12 }}>
                        {JSON.stringify(phase, null, 2)}
                      </pre>
                    )
                  }
                ]}
              />
            </div>
            <div>
              <div className="text-xs text-slate-400 mb-1">is-complete</div>
              <Collapse
                size="small"
                items={[
                  {
                    key: "ic",
                    label: "JSON",
                    extra: (
                      <Button type="link" size="small" onClick={() => copyText(complete, "complete")}>
                        复制
                      </Button>
                    ),
                    children: (
                      <pre style={{ maxHeight: 160, overflow: "auto", margin: 0, fontSize: 12 }}>
                        {JSON.stringify(complete, null, 2)}
                      </pre>
                    )
                  }
                ]}
              />
            </div>
          </div>
        </Card>
      </div>

      <Card title="自定义战役（写入会话）" size="small" style={{ marginTop: 14 }}>
        <Collapse
          style={{ marginBottom: 8 }}
          items={[
            {
              key: "s",
              label: "示例 JSON",
              children: (
                <pre style={{ margin: 0, fontSize: 12 }}>{JSON.stringify(SAMPLE, null, 2)}</pre>
              )
            }
          ]}
        />
        <Space style={{ marginBottom: 8 }}>
          <Button onClick={fillSample}>填充示例</Button>
        </Space>
        <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
          Campaign JSON
        </Typography.Paragraph>
        <Input.TextArea
          value={customText}
          onChange={(e) => setCustomText(e.target.value)}
          rows={10}
          placeholder='例如：{"name":"自定义战役","phases":["PHASE_1"],"objectives":[],"events":[]}'
          style={{ fontFamily: "monospace", fontSize: 12 }}
        />
        {customError ? (
          <Typography.Text type="danger" style={{ display: "block", marginTop: 8 }}>
            {customError}
          </Typography.Text>
        ) : customText ? (
          <Typography.Text type="success" style={{ display: "block", marginTop: 8 }}>
            JSON 校验通过，可提交。
          </Typography.Text>
        ) : null}
        <Space style={{ marginTop: 12 }}>
          <Button type="primary" loading={submitting} disabled={!!customError || !customText} onClick={submitCustom}>
            提交自定义战役
          </Button>
          <Button onClick={fillSample}>填充示例</Button>
        </Space>
      </Card>
    </div>
  );
}
