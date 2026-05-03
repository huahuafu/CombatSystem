# OpenLayers重构切流与回滚手册

说明：**默认 HTTP 入口已固定为 `/app/commander-next`**（旧版 `/app/commander` SPA 已移除）。`/combat/ops/migration/*` 仍可查询运维状态；`nextRouteEnabled` 现为兼容字段（恒为 true）。

## 1. 启动前检查

- 启动 Java 服务，确认 `/combat/ops/migration/status` 可访问。
- 启动 FastAPI 服务，确认 `GET /healthz` 正常。
- 将 `combat.ai.fastapi.enabled=true` 后重启 Java，确认 `fastApiHealthy=true`。

## 2. 灰度与观测（能力巡检）

1. 访问 `/app/commander-next` 做主链路巡检（部署、策略、复盘）。
2. 观察核心指标：
  - 策略生成成功率
  - 推演执行成功率
  - 页面错误率（前端 console / HTTP 5xx）

## 3. 回滚（策略生成链路）

1. 可选关闭 FastAPI 链路：`combat.ai.fastapi.enabled=false` 并重启（Java 侧回退内置策略路径，依实现而定）。
2. 验证 `/`、`/commander`、`/commander.html` 等仍 **302 → `/app/commander-next`**。

## 4. 演练脚本示例

```powershell
Invoke-RestMethod -Method Get "http://localhost:8080/combat/ops/migration/status"
Invoke-RestMethod -Method Post "http://localhost:8080/combat/ops/migration/mode?value=next"
Invoke-RestMethod -Method Get "http://localhost:8080/combat/ops/migration/status"
```

