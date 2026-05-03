/** 杀伤链六阶段：英文键与中文作战含义（与 /combat/v2/simulation/* 快照一致） */
export const KILL_CHAIN_STAGES = [
  {
    key: "find",
    en: "FIND",
    label: "发现阶段",
    desc: "传感器扫描与目标初现",
    actionPath: "find/scan",
    actionLabel: "扫描"
  },
  {
    key: "fix",
    en: "FIX",
    label: "定位阶段",
    desc: "多源融合与航迹稳定",
    actionPath: "fix/fuse",
    actionLabel: "融合"
  },
  {
    key: "track",
    en: "TRACK",
    label: "跟踪阶段",
    desc: "持续跟踪与预测",
    actionPath: "track/run",
    actionLabel: "跟踪"
  },
  {
    key: "target",
    en: "TARGET",
    label: "瞄准阶段",
    desc: "武器分配与交战许可",
    actionPath: "target/plan",
    actionLabel: "瞄准"
  },
  {
    key: "engage",
    en: "ENGAGE",
    label: "交战阶段",
    desc: "火力打击与效果评估入口",
    actionPath: "engage/run",
    actionLabel: "交战"
  },
  {
    key: "assess",
    en: "ASSESS",
    label: "评估阶段",
    desc: "战果判定与再规划",
    actionPath: "assess/run",
    actionLabel: "评估"
  }
];

export const KILL_CHAIN_STAGE_KEYS = KILL_CHAIN_STAGES.map((s) => s.key);
