# 阶段 B：战后简报（规则 + 可选 LLM）

## 目标

在阶段 A 数据闭环之上，提供 **服务端统一简报**：结构化要点 + 可选一段话综述（与前端快照规则解耦，便于后续替换模型）。

## 接口

- `POST /combat/v2/simulation/debrief`
- 请求体：`DebriefRequest`（`commanderSide`、`winner`、`winReason`、压缩后的 `rounds[]`）
- 响应体：`DebriefResponse`（`bullets[]`、`narrative`、`source`：`RULES` | `HYBRID`）

## 配置

- `combat.debrief.llm.enabled=true` 时，在规则要点生成后调用 **OpenAI 兼容**接口（与 `spring.ai.openai.base-url`、`spring.ai.openai.api-key`、`spring.ai.openai.chat.options.model` 共用，适用于 DashScope 兼容模式）。
- 关闭或调用失败时：`narrative` 为要点拼接，`source=RULES`。

## 前端

- 「运行回放中心」→ 卡片 **战后简报（阶段 B · 服务端）** → **生成战后简报**。

## 状态

- 已实现（后端 `DebriefService` + `DebriefPanel`）。
