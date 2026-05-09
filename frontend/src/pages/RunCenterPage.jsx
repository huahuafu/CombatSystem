import React, { useEffect, useState } from "react";
import { Button, Space, Typography, message } from "antd";
import { httpJson, downloadJson } from "../lib/api";
import { buildReplayExportPayload } from "../rebuild/replay/buildReplayExport";
import DebriefPanel from "../rebuild/replay/DebriefPanel";
import InfoReplaySection from "../rebuild/replay/InfoReplaySection";
import { useInfoStore } from "../store/infoStore";
import { useSimulationStore } from "../store/simulationStore";

const { Paragraph, Title } = Typography;

export default function RunCenterPage() {
  const [loading, setLoading] = useState(false);
  const [exportingReplay, setExportingReplay] = useState(false);

  const exportReplayBundle = async () => {
    const sid = String(useInfoStore.getState().boundScenarioId || "").trim();
    const roundArchive = useInfoStore.getState().roundArchive || [];
    if (!sid) {
      message.warning("未绑定想定：请先点击「读取激活想定」同步当前仿真想定。");
      return;
    }
    if (!roundArchive.length) {
      message.warning("当前会话无回合归档：请先在「仿真运行」页刷新态势，或点击下方「读取激活想定」后重试。");
      return;
    }
    setExportingReplay(true);
    try {
      const commanderSide = useSimulationStore.getState().commanderSide;
      const payload = await buildReplayExportPayload({
        scenarioId: sid,
        commanderSide,
        roundArchive
      });
      downloadJson(`replay-bundle-${sid.slice(0, 8)}-${Date.now()}.json`, payload);
      message.success("已导出综合复盘包（含 roundArchive、stats、交互事件、仿真状态）");
    } catch (e) {
      message.error(e?.message || "导出失败");
    } finally {
      setExportingReplay(false);
    }
  };

  const loadActive = async () => {
    setLoading(true);
    try {
      const a = await httpJson("/combat/scenario-data/active");
      const id = a?.activeScenarioId ? String(a.activeScenarioId) : "";
      if (id) {
        useInfoStore.getState().setBoundScenarioId(id);
        useInfoStore.getState().loadArchiveFromSession(id);
      }
      message.success(`已同步激活想定：${id || "—"}（复盘数据已从会话恢复）`);
    } catch (e) {
      message.error(e.message || "读取失败");
    } finally {
      setLoading(false);
    }
  };

  // 进入页面时自动尝试加载当前激活想定，避免“无数据”导致生成简报无反应
  useEffect(() => {
    loadActive().catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="page-container">
      <Title level={4} style={{ marginTop: 0, color: "#e7edf7" }}>
        当前想定复盘回放
      </Title>
      <Paragraph type="secondary" style={{ color: "#9fb0cc" }}>
        针对当前激活想定的会话复盘数据（roundArchive）。进入本页或点击「读取激活想定」即可同步仿真运行后的态势归档。
      </Paragraph>

      <Space wrap style={{ marginBottom: 14 }}>
        <Button type="primary" loading={loading} onClick={loadActive}>
          读取激活想定（同步复盘归档）
        </Button>
        <Button loading={exportingReplay} onClick={exportReplayBundle}>
          导出综合复盘包（JSON）
        </Button>
      </Space>

      <DebriefPanel />

      <InfoReplaySection />
    </div>
  );
}
