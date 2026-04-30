## CombatSystem 已实现功能清单（基线）

本文档用于“现状盘点 + 后续改造对照”。内容以代码为准（截至当前仓库状态）。

### 1) 前端（工程化 SPA + 少量遗留静态页）

- **主入口**：React 应用挂载于 `src/main/resources/static/app/`，典型路由 `/app/commander-next`；`GET /`、`GET /index.html` 及历史 `/commander`、`/commander.html` 均 **302 到 `/app/commander-next`**（旧版 `/app/commander` 页面已移除）。
- **历史 URL**：`commander.html` 与根路径类入口 302 到 `/app/commander-next`；`simulation-dashboard.html` → `/app/simulation-dashboard`；其余已删静态路径见 `RootRedirectController`。
- **运行回放 / 战役中心**：已迁入工程化前端路由 `/app/run-center`、`/app/campaign-center`（顶栏见 `frontend/src/components/TopNav.jsx`）；历史 `*.html` URL 由 `RootRedirectController` 302。

#### 1.1 想定（ScenarioData）工作流

- **创建并激活（含部署）**
  - 前端：地图点选 → 草稿单位/目标清单 → 创建
  - 后端：`POST /combat/scenario-data/create-full`
- **列表/激活/编辑/删除**
  - `GET /combat/scenario-data/list`
  - `GET /combat/scenario-data/active`
  - `POST /combat/scenario-data/set-active?id=...`
  - `POST /combat/scenario-data/update-basic`
  - `POST /combat/scenario-data/delete?id=...`

#### 1.2 地图态势（百度地图）

- **配置读取**：`GET /combat/map/config`（下发 `baiduMapKey`）
- **图层**
  - 兵力：`GET /combat/v2/simulation/units`
  - 目标：`GET /combat/objective/list?scenarioId=...`
  - 接触/跟踪（Find 快照最新 contacts）：`GET /combat/v2/simulation/find`
  - 威胁热力：当前为“敌方单位圈层”简化展示（非引擎真实威胁场栅格/概率模型）

#### 1.3 指挥官 A/B/C 策略与战报

- **生成策略卡**：`POST /combat/v3/commander/strategies/generate`
  - 语义约束：A=损失最小，B=胜率最高，C=均衡稳健（由后端合并/挑选）
- **执行策略并生成战报**：`POST /combat/v3/commander/strategies/execute`
  - 前端展示：winner、评分、解释、ENGAGE 阶段事件流、六阶段看板（取阶段 snapshot.metrics）

---

### 2) 后端接口（Controller 边界）

#### 2.1 `/combat`（综合业务：建模/管理/推演一体）

文件：`src/main/java/com/military/combat/controller/CombatController.java`

- **单位 CRUD**
  - `POST /combat/add|update|delete`
  - `GET /combat/list`
- **推演推进（旧入口）**
  - `POST /combat/start` → `CombatEngineService.simulateRound()`
- **会话快照聚合（推荐前端只读入口）**
  - `GET /combat/simulation/state` → `SimulationFacadeService.getState()`
- **想定（ScenarioData）**
  - `POST /combat/scenario-data/create-full|save|delete|restore-progress|update-basic|set-active`
  - `GET /combat/scenario-data/list|{id}|active`
- **活动/规则/过程/协同/对抗/命令链/架次/战役**：均挂在 `/combat/*` 下（详见 Controller 内各方法）

#### 2.2 `/combat/v2/simulation`（仿真流水线/快照/批量与策略优化）

文件：`src/main/java/com/military/combat/controller/v2/SimulationController.java`

- **杀伤链单回合**
  - `POST /combat/v2/simulation/start-kill-chain`（串行推进 Find→Fix→Track→Target→Engage→Assess）
  - `POST /combat/v2/simulation/start`（简化入口）
- **批量重放与评分**
  - `POST /combat/v2/simulation/batch-run`（多次 reset/activate → 连续 simulateRound → 打分排序）
- **混合策略**
  - `POST /combat/v2/simulation/strategy/recommend`
  - `POST /combat/v2/simulation/strategy/optimize`
- **只读快照**
  - `GET /combat/v2/simulation/state|readiness|assessment|kill-chain|operational-phase|naval-metrics`
  - `GET /combat/v2/simulation/find|fix|track|target|engage|assess`
  - `POST /combat/v2/simulation/find/scan|fix/fuse|track/run|target/plan|engage/run|assess/run`

#### 2.3 `/combat/v3/commander`（策略生成/执行/回放审计）

文件：`src/main/java/com/military/combat/controller/v3/CommanderController.java`

- `POST /combat/v3/commander/strategies/generate`
- `POST /combat/v3/commander/strategies/execute`
- `GET /combat/v3/commander/runs/{requestId}`
- `GET /combat/v3/commander/runs?scenarioId=&limit=`

---

### 3) 领域模型与 Mongo 集合（Repository ↔ Entity）

#### 3.1 想定主文档（第一层）

- `ScenarioData`（集合：`scenario_data`）
  - 内嵌：`units[]`、`objectives[]`、`terrains[]`、`campaign`、进度 `currentRound/stats` 等

#### 3.2 “挂接第二/三层”（按 scenarioId 关联）

典型：活动、交互规则/过程、协同、对抗等通过 `scenarioId` 关联当前想定。

> 说明：仓库中存在一部分实体未显式标注 `@Document(collection=...)`，此时 Spring Data 采用默认集合命名推导；这对“历史数据兼容/跨版本迁移”有风险（后续改进计划会处理）。

---

### 4) 推演主循环（回合管线与六阶段杀伤链）

#### 4.1 引擎单回合入口

- `CombatEngineService.simulateRound()`
  - 防重复：若已判胜则直接返回“已决出胜负”事件
  - 取活跃单位：`CombatUnitService.getUnitsForBattleEngine()`（过滤 `combatPower > 0`）
  - 核心：`runTurnPipeline(ctx)`，结束后 `currentRound + 1`

#### 4.2 回合管线（高层阶段顺序）

`runTurnPipeline()` 主干顺序（概念层面）：

- 宣告 doctrine/阶段开始事件
- **FIND**：对抗/指挥/架次/海上行为推进后，执行 Find 扫描
- **FIX**：融合定位
- **TRACK**：跟踪
- **TARGET**：分配与火力解
- 插入：战役检查、活动推进、协同推进、交互过程推进、未编入活动的单位自主行动
- **ENGAGE**：交战执行
- **ASSESS**：评估融合（闭环/偏差/建议）
- 判胜与统计记录

此设计意图：让六阶段不只是“展示看板”，而是驱动回合执行与指标输出。

