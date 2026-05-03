import React from "react";
import { Space, Tag } from "antd";
import {
  RocketOutlined,
  SecurityScanOutlined,
  ColumnHeightOutlined,
  RadarChartOutlined
} from "@ant-design/icons";
import { useDrag } from "react-dnd";
import { UNIT_TEMPLATES } from "./deployMeta";

const ICONS = {
  DESTROYER: RocketOutlined,
  FRIGATE: SecurityScanOutlined,
  SUBMARINE: ColumnHeightOutlined,
  UAV_RECON: RadarChartOutlined
};

export function PaletteUnit({ template, remain, onQuickDeploy }) {
  const Icon = ICONS[template.type] || RocketOutlined;
  const [{ isDragging }, drag] = useDrag(() => ({
    type: "UNIT_TEMPLATE",
    item: { unitType: template.type },
    collect: (monitor) => ({ isDragging: monitor.isDragging() })
  }));

  return (
    <div
      ref={drag}
      onDoubleClick={onQuickDeploy}
      style={{
        opacity: isDragging ? 0.45 : 1,
        cursor: remain > 0 ? "grab" : "not-allowed",
        border: "1px solid #334155",
        borderRadius: 10,
        padding: "8px 10px",
        background: "rgba(30,41,59,0.7)",
        minWidth: 132
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8 }}>
        <Space size={6}>
          <Tag color="blue" style={{ marginInlineEnd: 0, display: "inline-flex", alignItems: "center", gap: 4 }}>
            <Icon />
            {template.label}
          </Tag>
        </Space>
        <Tag color={remain > 0 ? "processing" : "default"}>余 {remain}</Tag>
      </div>
    </div>
  );
}

export { UNIT_TEMPLATES };
