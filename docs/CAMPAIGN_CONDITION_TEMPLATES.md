# 战役目标与事件条件模板（通用）

用于“历史战役复盘”与“未发生战役推演”的统一条件表达式库。  
这些表达式可用于：

- `CampaignEvent.triggerCondition`
- `CampaignObjective.targetId`（当你采用表达式驱动判定时）

---

## 1. 表达式语法（当前引擎支持）

- `SIDE_ALIVE_LE:{SIDE}:{N}`  
阵营存活单位数 <= N
- `SIDE_ALIVE_GE:{SIDE}:{N}`  
阵营存活单位数 >= N
- `UNIT_HP_BELOW:{selectorType}:{selectorValue}:{HP}`  
指定单位血量低于阈值
- `UNIT_EXISTS:{selectorType}:{selectorValue}`  
指定单位仍存活
- `UNIT_ELIMINATED:{selectorType}:{selectorValue}`  
指定单位已被歼灭
- `UNIT_IN_AREA:{SIDE}:{minLat}:{maxLat}:{minLon}:{maxLon}`  
阵营存在单位进入矩形区域

`selectorType` 目前建议使用：`id` / `name` / `side`

---

## 2. 10 条可直接复用模板

1. 红方在场（作战能力未崩溃）
  - `SIDE_ALIVE_GE:RED:3`
2. 蓝方减员到临界（阶段推进）
  - `SIDE_ALIVE_LE:BLUE:5`
3. 红方关键突击群受损（触发预警事件）
  - `UNIT_HP_BELOW:name:红方突击群:60`
4. 蓝方防御核心仍在（防御目标未失）
  - `UNIT_EXISTS:name:蓝方防御核心`
5. 蓝方防御核心被歼灭（突破成功）
  - `UNIT_ELIMINATED:name:蓝方防御核心`
6. 红方进入桥头堡区域（占领开始）
  - `UNIT_IN_AREA:RED:31.120:31.180:121.440:121.520`
7. 蓝方反突击进入争夺区（触发交火升级）
  - `UNIT_IN_AREA:BLUE:31.120:31.180:121.440:121.520`
8. 蓝方机动兵力低于阈值（转入追歼）
  - `SIDE_ALIVE_LE:BLUE:2`
9. 红方主力存活不足（作战失败条件）
  - `SIDE_ALIVE_LE:RED:1`
10. 指定编号单位被摧毁（精确目标）
  - `UNIT_ELIMINATED:id:unit-001`

---

## 3. 模板到战役语义映射建议

- `CAPTURE`（占领）  
推荐组合：`UNIT_IN_AREA` + `SIDE_ALIVE_GE`
- `DEFEND`（防守）  
推荐组合：`UNIT_EXISTS` 或 `SIDE_ALIVE_GE`
- `DESTROY`（歼灭）  
推荐组合：`UNIT_ELIMINATED` 或 `SIDE_ALIVE_LE`
- `DELAY`（迟滞）  
推荐组合：`SIDE_ALIVE_GE`（在若干回合窗口内）

---

## 4. 建模建议（避免脆弱表达式）

- 优先用 `id` 选择器，不要依赖中文名字模糊匹配。
- 区域判定先用较大矩形，避免因坐标误差导致“看起来到了但判定失败”。
- 每个阶段建议 1 条“达成条件”+ 1 条“失败条件”，保持可解释性。

---

## 5. 快速自测清单

1. 配置 2 条事件（一个区域触发、一个血量触发）。
2. 配置 2 条目标（一个歼灭、一个存活阈值）。
3. 连续跑 5 回合，检查 battle log 是否能解释触发/完成原因。