# commander-next 验收清单

## A. 基础可用性

- 可访问 `http://localhost:8080/app/commander-next`
- 根路径 `/` **302 到** `/app/commander-next`（与旧版 `/app/commander` 已下线一致）
- 页面无阻断性白屏/崩溃

## B. 部署与标绘

- 可拖拽或双击添加单位
- 可输入目标并地图点击落点
- 点击“校验部署”可返回明确结果
- 点击“创建并激活新想定”成功并显示 activeScenarioId

## C. AI 决策

- 可生成四类策略（WIN_MAX/LOSS_MIN/SPEED_MAX/BALANCED）
- 策略卡可选择并触发执行
- 执行后可返回战报事件

## D. 推演复盘

- ECharts 图表正常渲染
- 回放控制（播放/暂停/复位/滑条）可用
- 事件时间线能随回放进度更新

## E. 运维 API（切流已收口）

- `GET /combat/ops/migration/status` 可用（`nextRouteEnabled` 可为兼容恒 true）
- `POST /combat/ops/migration/mode` 仍可调用（不再改变默认入口页；入口固定 commander-next）

## F. 环境一致性

- `java -version` 为 17.x
- `.\mvnw.cmd -v` 显示 Java 17
- 前端构建产物 hash 已更新（`index-*.js`）