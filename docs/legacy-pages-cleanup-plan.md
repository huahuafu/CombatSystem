# 旧页面清理提案（分批）

## 目标
- 在不影响当前主链路（`/app/commander-next`）的前提下，逐步下线历史静态页面。
- 保留可回退能力，避免一次性删除导致运维风险。

## 当前旧页面清单
- **已移除（历史 URL 由 `RootRedirectController` 302，无静态文件）**
  - `commander.html`、`/commander` → `/app/commander-next`（旧 SPA 已下线）
  - `simulation-dashboard.html` → `/app/simulation-dashboard`
  - `portal.html`、`scenario-studio.html`、`activity-rule-center.html`、`batch-lab.html` → `/app/commander-next`
- **仍保留的独立静态 HTML**：无（原 `run-center` / `campaign-center` 已并入 `frontend` SPA）。

## 引用关系结论
- 后端入口已优先走 `/app/*`，且兼容路径会重定向到 `/app/*`。
- 前端导航已切到 `commander-next` 主入口。
- 静态公共导航（`assets/combat-ui.js`）已改为优先导向 `/app/*`，旧入口仅保留回退标识。

## 分批策略

### 批次A / B（已完成演进）
- 批次 A/B 的策略（导航收口、提示页）已由批次 C 收口：**兼容 URL 全部改由控制器 302**，静态 HTML 已移除的文件路径不再落盘。

### 批次C（稳定14天后）— 已执行
- 目标：真正删除未使用旧页面。
- 后续：`run-center` / `campaign-center` 已再迁入 SPA（`/app/run-center`、`/app/campaign-center`），见 `frontend/src/pages/RunCenterPage.jsx`、`CampaignCenterPage.jsx`。
- 动作：
  - 已为 `portal.html`、`scenario-studio.html`、`activity-rule-center.html`、`batch-lab.html` 增加控制器 302；删除对应静态 HTML。
  - 已删除 `commander.html`、`simulation-dashboard.html`（控制器原已有 `/commander.html`、`/simulation-dashboard.html` 映射，行为不变）。
  - 更新 `assets/combat-ui.js` 导航与本文档。

## 不建议立即删除的项
- （已收敛）回放与战役能力均在 `/app/run-center`、`/app/campaign-center` 提供。

## 回滚预案
- 默认页入口已不再分支到旧版指挥官；如需恢复历史静态页或旧前端路由，从版本库检出并在 `RootRedirectController` 中调整 302 目标。
