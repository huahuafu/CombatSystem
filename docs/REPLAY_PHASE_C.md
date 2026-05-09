# 阶段 C：AI 参谋 / 策略推荐集成（已实现）

## 目标

将后端真实 `/combat/v2/simulation/strategy/recommend` 集成到主仿真运行页（SimulationDashboardPage），取代原有的本地启发式计划卡片。

## 变更

- 新增「获取 AI 策略推荐（阶段 C · 后端）」按钮，调用真实 HybridStrategyRecommend API。
- 推荐结果映射为前端 PlanCard 格式（含 hypothesis、predictedWinRate、planJson）。
- 选中方案后出现「采纳此方案」按钮（当前为轻量记录 + 提示；完整落库可扩展 POST /strategy/adopt 调用 AiAutoModelingService.replanNextRound）。
- 与杀伤链逐步推进完全兼容：采纳后仍可继续「推进下一阶段」。

## 使用流程

1. 激活想定 → 仿真运行页刷新/推进至少 1 回合。
2. 点击「获取 AI 策略推荐」。
3. 选择方案 → 采纳 → 继续杀伤链推进。

## 验收

- 推荐数据来自后端（非本地 buildPlansBySnapshot）。
- 采纳不破坏现有会话状态和回合计数。

## 后续

- 扩展 `/strategy/adopt` 端点，真正调用 `createActivitiesFromPlan` + `applyReplan*` 落库。
- 支持用户自定义 goal 输入。
