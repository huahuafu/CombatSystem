import React from "react";
import { Collapse, Space, Tag, Typography } from "antd";
import {
  AimOutlined,
  ApartmentOutlined,
  BankOutlined,
  CloudOutlined,
  ColumnHeightOutlined,
  ControlOutlined,
  PartitionOutlined,
  RadarChartOutlined,
  RocketOutlined,
  SecurityScanOutlined,
  ContainerOutlined,
  ThunderboltOutlined
} from "@ant-design/icons";
import { useDrag } from "react-dnd";
import type { DeployedUnit, ForceCamp, ForceIconKey, ForceTemplate } from "../force/forceTypes";
import { FORCE_CATEGORY_LABELS, remainForTemplate, templatesByCategory } from "../force/forceCatalog";
import { useDeploymentStore } from "../../store/deploymentStore";

const { Text } = Typography;

const ICON_MAP: Record<ForceIconKey, React.ReactNode> = {
  ship: <ContainerOutlined />,
  carrier: <RocketOutlined />,
  amphib: <PartitionOutlined />,
  supply: <ContainerOutlined />,
  minesweeper: <AimOutlined />,
  destroyer: <RocketOutlined />,
  frigate: <SecurityScanOutlined />,
  submarine: <ColumnHeightOutlined />,
  uuv: <ColumnHeightOutlined />,
  uav: <RadarChartOutlined />,
  awacs: <CloudOutlined />,
  ew: <ThunderboltOutlined />,
  helo: <ControlOutlined />,
  shoreMissile: <BankOutlined />,
  shoreAd: <ApartmentOutlined />
};

function PaletteRow({
  tpl,
  remain,
  disabled,
  onSelectType,
  onQuickDeploy
}: {
  tpl: ForceTemplate;
  remain: number;
  disabled: boolean;
  onSelectType: (type: string) => void;
  onQuickDeploy: () => void;
}) {
  const iconNode = ICON_MAP[tpl.icon] ?? <ContainerOutlined />;
  const [{ isDragging }, drag] = useDrag(
    () => ({
      type: "UNIT_TEMPLATE",
      item: { unitType: tpl.type, camp: tpl.camp },
      canDrag: !disabled,
      collect: (monitor) => ({ isDragging: monitor.isDragging() })
    }),
    [tpl.type, tpl.camp, disabled]
  );

  return (
    <div
      ref={disabled ? undefined : drag}
      role="button"
      tabIndex={0}
      onClick={() => !disabled && onSelectType(tpl.type)}
      onKeyDown={(e) => {
        if (disabled) return;
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSelectType(tpl.type);
        }
      }}
      onDoubleClick={() => {
        if (!disabled) onQuickDeploy();
      }}
      style={{
        opacity: disabled ? 0.45 : isDragging ? 0.45 : 1,
        cursor: disabled ? "not-allowed" : "grab",
        border: "1px solid #334155",
        borderRadius: 10,
        padding: "8px 10px",
        background: disabled ? "rgba(15,23,42,0.55)" : "rgba(30,41,59,0.7)",
        minWidth: 148,
        outline: "none"
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8 }}>
        <Space size={6}>
          <Tag
            color={tpl.camp === "RED" ? "red" : "blue"}
            style={{ marginInlineEnd: 0, display: "inline-flex", alignItems: "center", gap: 4, maxWidth: 200 }}
          >
            {iconNode}
            <span style={{ overflow: "hidden", textOverflow: "ellipsis" }}>{tpl.name}</span>
          </Tag>
        </Space>
        <Tag color={remain > 0 ? "processing" : "default"}>余 {remain}</Tag>
      </div>
      <Text type="secondary" style={{ fontSize: 11, display: "block", marginTop: 4 }}>
        {tpl.modelLabel}
      </Text>
    </div>
  );
}

export interface ForceDeployPaletteProps {
  commanderSide: ForceCamp;
  deployedUnits: DeployedUnit[];
  onQuickDeployAtCenter: (unitType: string, camp: ForceCamp) => void;
}

export default function ForceDeployPalette({ commanderSide, deployedUnits, onQuickDeployAtCenter }: ForceDeployPaletteProps) {
  const setLastSelectedType = useDeploymentStore((s) => s.setLastSelectedType);
  const grouped = templatesByCategory(commanderSide);

  const items = (["SURFACE", "AIR", "UNDERWATER", "SHORE"] as const).map((cat) => ({
    key: cat,
    label: FORCE_CATEGORY_LABELS[cat],
    children: (
      <Space wrap size={[8, 8]}>
        {grouped[cat].map((tpl) => {
          const remain = remainForTemplate(deployedUnits, tpl);
          const disabled = remain <= 0;
          return (
            <PaletteRow
              key={tpl.id}
              tpl={tpl}
              remain={remain}
              disabled={disabled}
              onSelectType={(type) => setLastSelectedType(type)}
              onQuickDeploy={() => {
                setLastSelectedType(tpl.type);
                onQuickDeployAtCenter(tpl.type, tpl.camp);
              }}
            />
          );
        })}
      </Space>
    )
  }));

  return (
    <div className="force-deploy-palette">
      <Collapse size="small" defaultActiveKey={["SURFACE", "AIR", "UNDERWATER", "SHORE"]} items={items} />
      <Text type="secondary" style={{ fontSize: 11, display: "block", marginTop: 8 }}>
        拖拽至地图部署；单击选中用于双击地图快捷部署；双击条目在视图中心部署。
      </Text>
    </div>
  );
}
