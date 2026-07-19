# Changelog

## [v40] — 2026-07-19 — 女仆跑步发电

- **女仆跑步发电任务** — 女仆站在Create传送带上冲刺产生旋转动力(~96RPM应力输出)
- **MaidPowerBelt** — 自研发电皮带方块 (extends HorizontalKineticBlock + GeneratingKineticBlockEntity)
- **皮带渲染** — BER复用Create BeltRenderer模型, UV滚动动画
- **食物消耗** — 每5秒消耗1背包食物, 无食物不启动
- **智能恢复** — 停跑/切换任务/无食物→自动恢复普通皮带
- **注册系统** — LMA首个DeferredRegister<Block> + <BlockEntityType>

## [v38.2] — 2026-07-18 — 机械臂任务重写 + 预设默认禁用

- **机械臂任务重写** — 木棍(stick)标记容器替机械臂, TAKE↔DEPOSIT两状态, 任务栏注册, 空源无限等待, 不拦截Create事件
- **预设默认禁用** — RuleDef.full()/simple() 工厂 enabled=false, 所有预设新安装默认关闭
- **连锁采集无限重试** — 挖矿镐破损/砍斧耐久不足不fail, 无限扫描重试
- **compat对齐** — CreateCompat改为标准门控模式 (init+isInstalled), 事件独立 CreateEventListener

## [v38.1] — 2026-07-17 — FakePlayer + 机械臂搬运 + 架构重构

- **女仆模拟玩家左右键** — 参考 Create Deployer: LmaFakePlayer, 4模式 (右键/左键 × 一次性/持续), 方块破坏+收集掉落
- **机械臂搬运任务** — 参考 Create Mechanical Arm: 右键容器标记取物/放物点 → 右键女仆启动 → 自导航搬运循环
- **架构重构** — compat/vanilla (~110文件) 拆回顶层包 api/task/adapter/vanilla
- **IO 标准化** — IReader/IWriter/IExecutor/TaskState 统一接口
- **TLM IO 扩展** — WorldOutput +maidDestroyBlock/maidPlaceBlock/sendThinkingBubble, CombatOutput +maidDoHurtTarget

## [v36.7] — 2026-07-17 — 删除祭坛合成任务

- 任务栏移除"祭坛合成"（有合成台不需要祭坛 — 用户决策）：TaskRegistry 注册 + AltarCraftPipeline 删除、协调行为 altar_craft 分支清理、AI 工具/关键词/图标/分组/语言键同步移除
- **保留**规则引擎祭坛生态：place_altar_item 动作 / AltarPresets 预设 / AltarOutput/AltarExecute IO — 玩家仍可用规则驱动祭坛
- 条件默认参数值 altar_craft → craft_chain（3 文件 6 处）

## [v36.6] — 2026-07-17 — 状态原子化 + 采集目标抽象 + TaskEngine 打架修复

- **P0 根因**: lma_flow_tick 无心跳 → TaskEngine 60 秒超时杀活任务 → auto-restart 无限 churn（"卡住"的真凶，日志实锤）
- **TaskStateService**（新）: lma_flow_* 状态写入单一所有者 — init/heartbeat/fail/complete/clearAll；协调行为全部委托；执行器 keepAlive 打心跳 → 活任务永不被超时杀
