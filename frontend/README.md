# CombatSystem Frontend (Vite + React)

## 目标

- 将现有静态页面升级为工程化 React 架构
- 统一“部署 -> 决策 -> 推演 -> 复盘”指挥流程
- 支持四战术方案卡片、时间线、阶段态势、复盘事件流

## 本地开发

```bash
cd frontend
npm install
npm run dev
```

默认代理后端到 `http://localhost:8080`，`/combat/**` 可直接访问。

## 构建产物

```bash
cd frontend
npm run build
```

构建结果输出到：

- `src/main/resources/static/app`

后续可在后端增加入口页面，将路由转发到该目录（SPA fallback）。
