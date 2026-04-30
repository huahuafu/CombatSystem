# CombatSystem 论文/答辩用 UML 图建议与源码

本文档说明**建议绘制哪些 UML 图**（与论文常见结构对应），并给出 **Mermaid** 源码。可直接在 VS Code / Typora / GitHub 预览，或用 [Mermaid Live Editor](https://mermaid.live) 导出 **PNG/SVG** 插入 Word/PPT。

---

## 建议生成的 UML 一览

| 图类型 | 用途 | 是否建议 |
|--------|------|----------|
| **用例图** | 说明系统与用户/外部角色的功能边界，对应任务书「三大模块」 | **强烈建议** |
| **系统上下文 / 部署图** | 浏览器、Spring Boot、MongoDB 关系，论文「部署环境」 | 建议 |
| **包图 / 分层结构图** | `controller` → `service` → `repository` → `entity`，体现分层架构 | 建议 |
| **核心领域类图（简图）** | `ScenarioData`、`CombatUnit`、`CombatActivity`、`InteractionRule` 及关联 | **强烈建议**（宜简不宜全） |
| **活动图** | 回合推演管线 `runTurnPipeline` 各阶段顺序 | **强烈建议**（主线清晰） |
| **序列图** | 「激活想定」「模拟一回合」两条关键路径 | **强烈建议** |
| **状态图** | `CombatActivity` 生命周期（计划中→执行中→完成等） | 可选 |
| **组件图** | 三大模块与引擎的依赖，可与包图二选一 | 可选 |

**不建议**：把全部 60+ 个类画在一张类图里——论文里通常用 **2～3 张简图** 分「领域模型」「推演引擎协作」即可。

---

## 1. 用例图（任务书对齐）

```mermaid
flowchart LR
  subgraph actors[参与者]
    U((用户/建模人员))
  end

  subgraph uc[用例]
    UC1[想定数据建模]
    UC2[想定保存与加载]
    UC3[想定激活]
    UC4[作战活动建模]
    UC5[交互规则建模]
    UC6[回合推演]
    UC7[战术评估与日志]
    UC8[AI 辅助建议]
  end

  U --> UC1
  U --> UC2
  U --> UC3
  U --> UC4
  U --> UC5
  U --> UC6
  U --> UC7
  U --> UC8

  UC3 -.->|依赖| UC1
  UC4 -.->|挂接想定| UC3
  UC5 -.->|挂接想定| UC3
  UC6 -.->|使用| UC4
  UC6 -.->|使用| UC5
```

> 说明：严格 UML 用例图在 Mermaid 里表达有限，答辩可用 **PlantUML** 重画；上图为 **流程图风格** 的等价表达。若需经典椭圆用例图，可在 draw.io / StarUML 中照此列表绘制。

---

## 2. 部署图（逻辑部署）

```mermaid
flowchart TB
  subgraph client[客户端]
    B[浏览器\n静态页面 index.html]
  end

  subgraph server[应用服务器]
    APP[Spring Boot 应用\nREST API]
  end

  subgraph data[数据层]
    M[(MongoDB)]
  end

  B -->|HTTP JSON| APP
  APP -->|Spring Data MongoDB| M
```

---

## 3. 分层 / 包结构（示意）

```mermaid
flowchart TB
  subgraph presentation[表现层]
    C1[CombatController]
    C2[ScenarioWorkflowController]
    C3[SimulationController]
  end

  subgraph application[应用层 Service]
    S1[ScenarioService\nScenarioActivationService]
    S2[CombatActivityService]
    S3[InteractionRuleService]
    S4[CombatEngineService]
    S5[SimulationFacadeService]
  end

  subgraph persistence[持久化]
    R1[ScenarioDataRepository]
    R2[CombatActivityRepository]
    R3[InteractionRuleRepository]
    R4[CombatUnitRepository]
  end

  subgraph domain[领域实体 entity]
    E1[ScenarioData]
    E2[CombatActivity]
    E3[InteractionRule]
    E4[CombatUnit]
  end

  C1 --> S1
  C1 --> S2
  C1 --> S3
  C1 --> S4
  C2 --> S1
  C3 --> S5
  S4 --> S2
  S4 --> S3
  S1 --> R1
  S2 --> R2
  S3 --> R3
  S1 --> E1
  S2 --> E2
  S3 --> E3
  R1 --> E1
  R2 --> E2
  R3 --> E3
```

---

## 4. 核心领域类图（简图）

仅保留与三大模块和推演**最相关**的类与关联；属性已大幅省略。

```mermaid
classDiagram
  class ScenarioData {
    +String id
    +String name
    +List~CombatUnit~ units
    +List~CombatObjective~ objectives
    +Campaign campaign
    +String campaignId
  }

  class CombatUnit {
    +String id
    +String side
    +String name
    +Double latitude
    +Double longitude
  }

  class CombatObjective {
    +String id
    +String name
    +String type
  }

  class CombatActivity {
    +String id
    +String scenarioId
    +String campaignId
    +List~String~ unitIds
    +List~ActivityStep~ steps
    +int startRound
    +int endRound
    +String status
  }

  class InteractionRule {
    +String id
    +String scenarioId
    +String campaignId
    +String effectType
    +double effectValue
  }

  class InteractionEvent {
    +String ruleId
    +int round
  }

  ScenarioData "1" *-- "many" CombatUnit : 内嵌部署
  ScenarioData "1" *-- "many" CombatObjective : 目标
  CombatActivity "many" --> "1" ScenarioData : scenarioId
  InteractionRule "many" --> "1" ScenarioData : scenarioId
  CombatActivity ..> CombatObjective : objectiveId
  InteractionEvent ..> InteractionRule : 记录触发
```

---

## 5. 活动图：回合推演管线 `runTurnPipeline`

```mermaid
flowchart TD
  A([开始本回合]) --> B[阶段1: 战役检查\nstageCampaignCheck]
  B --> C[阶段2: 活动执行\nstageActivityExecution]
  C --> D[阶段3: 协同作战\nstageCoordinationExecution]
  D --> E[阶段4: 交互过程脚本\nstageInteractionProcess]
  E --> F[阶段5: 自主交战\nstageAutonomousCombat\n含交互规则加成]
  F --> G[阶段6: 回合统计\nstageRecordStat]
  G --> H([回合结束\n当前回合+1])
```

---

## 6. 序列图：激活想定

```mermaid
sequenceDiagram
  participant U as 用户/前端
  participant C as ScenarioWorkflowController
  participant A as ScenarioActivationService
  participant Sc as ScenarioService
  participant Uu as CombatUnitService
  participant Ca as CampaignService

  U->>C: POST /v2/scenario/activate?id=
  C->>A: activateScenario(scenarioId)
  A->>Sc: setActiveScenarioId(id)
  A->>Sc: getScenarioDataById(id)
  Sc-->>A: ScenarioData
  A->>Uu: applyScenarioDeploymentToLive(sd)
  alt 内嵌战役存在
    A->>Ca: applyCampaignToSession(campaign)
  else 无战役
    A->>Ca: clearSessionCampaign()
  end
  A-->>C: ok
  C-->>U: activeScenarioId + mainLine
```

---

## 7. 序列图：模拟一回合（概要）

```mermaid
sequenceDiagram
  participant E as CombatEngineService
  participant Sc as ScenarioService
  participant Act as CombatActivityService
  participant Rule as InteractionRuleService
  participant U as CombatUnitService

  E->>U: getUnitsForBattleEngine()
  U-->>E: 存活单位列表
  E->>E: runTurnPipeline
  E->>Sc: getCurrentRound
  E->>Act: getActiveActivities(round)
  loop 每个活动
    E->>Act: executeActivityStep(activity)
  end
  E->>E: stageAutonomousCombat\n攻击时 ruleAppliesToPair / applyMatchedRule
  E->>Sc: setCurrentRound(round+1)
  E-->>E: BattleEvent 列表
```

---

## 8. 状态图：作战活动 `CombatActivity`（简化）

```mermaid
stateDiagram-v2
  [*] --> PLANNED: 创建
  PLANNED --> READY: prepare
  READY --> EXECUTING: start
  EXECUTING --> PAUSED: pause
  PAUSED --> EXECUTING: resume
  EXECUTING --> COMPLETED: 步骤完成
  EXECUTING --> FAILED: 失败
  PLANNED --> CANCELLED: cancel
  EXECUTING --> CANCELLED: cancel
```

---

## 导出为图片的步骤

1. 打开 [https://mermaid.live](https://mermaid.live)  
2. 将某一节 **```mermaid … ```** 中的代码（不含外层 markdown 围栏）粘贴到左侧  
3. **Actions → Export PNG/SVG**  
4. 插入 Word：论文插图需编号与图题；PPT 可直接贴 PNG  

---

## 与论文章节的对应建议

| 论文章节 | 推荐使用 |
|----------|----------|
| 需求分析 | 用例图 §1 |
| 总体设计 | 分层图 §3、部署图 §2 |
| 详细设计-数据模型 | 类图 §4 |
| 详细设计-推演引擎 | 活动图 §5、序列图 §6～7 |
| 可选：活动状态 | 状态图 §8 |

---

*文档随项目演进可增删类名；若类图与代码不一致，以 `entity` 包为准。*
