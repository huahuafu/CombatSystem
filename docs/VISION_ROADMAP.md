# 愿景路线图：全球部署 · AI 计划 · 一键推演 · 多次寻优

面向目标：任意地域部署兵力 → AI 生成作战计划并写入系统 → 一键/批量推演 → 比较多次结果选出较优方案。

---

## 阶段划分

### 阶段 A — 批量推演与评分基座（**API、回放重置与想定工作室第 4 步面板已落地**）

| 交付项 | 说明 |
|--------|------|
| 回合与统计重置（保留激活想定） | 多次蒙特卡洛运行时不丢失 `activeScenarioId`，回合与战报曲线归零 |
| 想定目标「完成」标记回放重置 | 避免上一轮推演污染下一轮 Mongo 中的目标状态 |
| 作战活动回放重置 | 每轮开始前将绑定该想定的活动恢复为 `PLANNED`、步骤归零 |
| **POST `/combat/v2/simulation/batch-run`** | 请求体指定 `runs`、`roundsPerRun`、可选 `scenarioId`、可选 `replaceAiArtifacts`；为 true 时**在第一次重放前**删除本想定名称以 `[AI]` 开头的活动与规则，与「替换旧 AI 条目」策略一致；多次：激活→重置→连续 `simulateRound`→按战损差分打分→返回排名与 `purgeSummary`（若已清理） |
| 评分公式 v1 | 以双方剩余战力与战损为主的标量分数；后续可接目标达成、时间代价 |

**限制（v1）**：随机种子与引擎内 `Random` 未完全统一时，多次运行仍存在微差；交互规则/架次等侧效以当前实现为准。

### 阶段 B — AI 计划与「可执行结构」对齐（**进行中 / 首期已合并**）

| 交付项 | 说明 |
|--------|------|
| LLM JSON 扩展 | `generateStructuredOperationalPlan` 要求模型输出 `activities[]`、`interactionRules[]`，并与 `MOVE/FIRE/RECON`、`DAMAGE_BOOST`、双方阵营等字段对齐 |
| 上下文绑定 | `buildAutoModelContext()` 注入 `unitsForBinding`、`objectivesForBinding`（含 id），便于模型填写真实 `target` / `objectiveId` |
| 落库 | `AiAutoModelingService` 在部署编组之后调用 `createActivitiesFromPlan`、`createInteractionRulesFromPlan`，写入 `CombatActivity` 与 `InteractionRule`（名称前缀 `[AI]`） |
| 默认模板 | JSON 解析失败时的 `defaultPlan` 已含示例活动与规则 |

**草稿预览**：`AiAutoModelRequest.dryRun=true` 时仅解析 JSON 并填充 `dryRunSummary` / `actions`，**不**部署编组、不写库、不推演；前端指挥控制区提供「草稿预览」勾选。

**替换旧 AI 条目**：`replaceAiArtifacts=true`（且非 dryRun）时，在落库前删除当前想定下**名称以 `[AI]` 开头**的作战活动与交互规则，再写入新计划，避免重复堆积；架次/指挥令/对抗动作为历史设计，仍可能累加，可后续再按前缀清理。

**待办（阶段 B 后续）**：想定整本版本化/快照；可选「新建作战目标」从 AI 写入；再规划流中同步活动/规则；对架次/命令等统一 `[AI]` 清理策略。

### 阶段 C — 寻优与对比产品化

- 批量 API 上层增加：**计划变体 ID**（多方案并行跑）、**目标函数配置**（权重：歼敌/保己/时长/弹药）。
- 结果看板：同屏对比表格、最优计划「应用回当前工作想定」按钮。

### 阶段 D — 规模化与可信度

- 异步任务队列（长批量）、进度轮询。
- 可复现性：集中随机源或每 run 注入 `long seed`。
- 可选：并行多进程（多实例）仅只读想定快照 + 内存沙箱（工作量大，放后期）。

---

## 已落地 API（阶段 A）

- **`POST /combat/v2/simulation/batch-run`**  
  - Body 示例：`{ "scenarioId": "可选", "runs": 5, "roundsPerRun": 20 }`  
  - 缺省 `scenarioId` 时使用当前服务端激活想定。

---

## 文档维护

随功能合并更新本文件中的阶段状态与接口说明。
