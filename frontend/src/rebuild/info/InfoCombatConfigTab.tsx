import React, { useEffect } from "react";
import { Card, Select, Space, Switch, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { DeployedUnit, ForceCamp } from "../force/forceTypes";
import { getUnitTypeLabel } from "../force/forceCatalog";
import { useInfoStore } from "../../store/infoStore";
import type { EwIntensityLevel } from "./infoTypes";

const { Text } = Typography;

export interface InfoCombatConfigTabProps {
  commanderSide: ForceCamp;
  deployedUnits: DeployedUnit[];
}

export default function InfoCombatConfigTab({ commanderSide, deployedUnits }: InfoCombatConfigTabProps) {
  const environment = useInfoStore((s) => s.environment);
  const setEnvironment = useInfoStore((s) => s.setEnvironment);
  const unitInfoCombat = useInfoStore((s) => s.unitInfoCombat);
  const setUnitInfoCombat = useInfoStore((s) => s.setUnitInfoCombat);
  const ensureUnitsHaveDefaults = useInfoStore((s) => s.ensureUnitsHaveDefaults);

  const rows = deployedUnits.filter((u) => u.side === commanderSide);

  useEffect(() => {
    ensureUnitsHaveDefaults(rows.map((r) => r.id));
  }, [rows, ensureUnitsHaveDefaults]);

  const columns: ColumnsType<DeployedUnit> = [
    {
      title: "单位",
      key: "name",
      render: (_, r) => (
        <Space direction="vertical" size={0}>
          <Text strong style={{ color: "#e2e8f0" }}>
            {r.name}
          </Text>
          <Tag>{getUnitTypeLabel(r.type)}</Tag>
        </Space>
      )
    },
    {
      title: "雷达",
      key: "radar",
      width: 88,
      render: (_, r) => {
        const st = unitInfoCombat[r.id];
        return (
          <Switch
            size="small"
            checked={st?.radarEmitting !== false}
            onChange={(v) => setUnitInfoCombat(r.id, { radarEmitting: v })}
          />
        );
      }
    },
    {
      title: "无线电静默",
      key: "rs",
      width: 110,
      render: (_, r) => (
        <Switch
          size="small"
          checked={Boolean(unitInfoCombat[r.id]?.radioSilent)}
          onChange={(v) => setUnitInfoCombat(r.id, { radioSilent: v })}
        />
      )
    },
    {
      title: "电子战强度",
      key: "ew",
      width: 140,
      render: (_, r) => (
        <Select
          size="small"
          style={{ width: 120 }}
          value={unitInfoCombat[r.id]?.ewIntensity ?? 0}
          onChange={(v: EwIntensityLevel) => setUnitInfoCombat(r.id, { ewIntensity: v })}
          options={[
            { label: "关", value: 0 },
            { label: "低", value: 1 },
            { label: "中", value: 2 },
            { label: "高", value: 3 }
          ]}
        />
      )
    }
  ];

  return (
    <Space direction="vertical" style={{ width: "100%" }} size={12}>
      <Card size="small" bordered={false} className="commander-panel-card" title="战场环境与衰减">
        <Space direction="vertical" style={{ width: "100%" }}>
          <div className="kv text-sm">
            <span>海况 (1–5)</span>
            <Select
              size="small"
              style={{ width: 120 }}
              value={environment.seaState}
              onChange={(v) => setEnvironment({ seaState: v })}
              options={[1, 2, 3, 4, 5].map((n) => ({ label: `${n} 级`, value: n }))}
            />
          </div>
          <div className="kv text-sm">
            <span>气象衰减系数</span>
            <Select
              size="small"
              style={{ width: 120 }}
              value={environment.weatherAttenuation}
              onChange={(v) => setEnvironment({ weatherAttenuation: Number(v) })}
              options={[
                { label: "良好 1.0", value: 1 },
                { label: "一般 0.92", value: 0.92 },
                { label: "较差 0.82", value: 0.82 },
                { label: "恶劣 0.72", value: 0.72 }
              ]}
            />
          </div>
          <Text type="secondary" style={{ fontSize: 11 }}>
            海况与气象参与探测距离折算；电子战强度档位控制干扰压制圈范围（歼击电战/电子侦察舰）。
          </Text>
        </Space>
      </Card>
      <Card size="small" bordered={false} className="commander-panel-card" title="当前阵营平台信息战开关">
        {!rows.length ? (
          <Text type="secondary">暂无已部署兵力</Text>
        ) : (
          <Table<DeployedUnit>
            size="small"
            pagination={false}
            rowKey="id"
            columns={columns}
            dataSource={rows}
            scroll={{ x: "max-content" }}
          />
        )}
      </Card>
    </Space>
  );
}
