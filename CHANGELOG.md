# Changelog

## [v53] — 2026-07-22 — 重启自动恢复

- **重启自动恢复任务** — onEntityJoin 保留 FLOW_TASK 和配置键，写 GUI_INIT 自动重新提交。玩家无需手动重选任务。
- **配置持久化** — arm_transfer 的 lma_arm_take/deposit 坐标在任务完成/取消后保留。
- **木棍错误提示泛化** — "物品(木棍)不支持设置该任务({taskType})"。

## [v52] — 2026-07-22 — 注册简化 + 删除废弃execute()

- **register/registerSearch 合并** — 单一 register(name, pl, ex, showInBar)。参数从5减到3。方块过滤由 Pipeline.isTargetBlock() 自己决定。
- **TaskPipeline.execute() 删除** — 从 v18 废弃的 execute() 正式移除。接口从 2 必须方法 → 1 必须方法 (taskType)。FurnacePipeline 配方逻辑移入 IExecutor。
- **showInBar 参数** — register() 新增 boolean 控制 TLM 任务栏可见性。被动/环境任务传 false。
- **TaskHandler 精简** — 6 字段 → 4 字段 (移除 targetBlock/searchPredicate/isValid)。

## [v51] — 2026-07-21 — 注册文档

## [v50] — 2026-07-21 — interact 层合并

- **4→1 通用 SubmitTaskAction** — 删除 JukeboxInteractAction/FurnaceInteractAction/CraftingTableInteractAction/BellRingAction，合并为参数化 SubmitTaskAction。

## [v49] — 2026-07-21 — adapter↔task 解耦

- **adapter 零 TaskDispatcher 引用** — adapter 只写 NBT 标记(TLM_SWITCH/GUI_INIT)，TaskEngine 轮询决策。
- **NavigationMemory → api/navigation/** — Brain Memory 访问封装移至 api 层。
- **终态检测** — 修复 STATE_COMPLETED/FAILED 跌落 GUI-init 导致任务无限重启 Bug。

## [v48] — 2026-07-21 — Brain 导航迁移

- **TLM MaidMoveToBlockTask** — LmaFlowCoordinationBehavior 继承 TLM 螺旋 BFS 导航基类，删除手动导航代码 (~60行)。
- **shouldMoveTo → pipeline.isTargetBlock** — 导航过滤委托给 Pipeline。

## [v47] — 2026-07-21 — FSM 迁移

- **CrankPipeline + PowerPipeline → TaskStateMachine** — 显式 3 态循环 (SEARCHING→NAVIGATING→WORKING)。
- **消除 static ConcurrentHashMap** — PowerPipeline 目标位置改用 NBT 持久化。

## [v46] — 2026-07-21 — TaskStateMachine 引擎

- **泛型 FSM 基类** — TaskStateMachine\<S extends Enum\<S\>\> implements TaskPipeline。显式状态枚举 + 转换图验证 + onEnter/onExit 钩子 + 自动 executor。
- **ArmTransferPipeline 迁移** — 4 态循环 (TO_TAKE→TAKING→TO_DEPOSIT→DEPOSITING)。

## [v45] — 2026-07-21 — P2/P3 优化

- **RetryPolicy 独立类** — never()/always()/fixed(N) + shouldRetry(attemptCount)。
- **结构化日志** — TaskDispatcher 5 节点日志 ([LMA/Task] 前缀 + UUID + 任务类型)。
- **STATE_CANCELLED 常量** — 全局消除硬编码 "cancelled" (8处→0)。
- **生命周期对称化** — complete/fail/timeout 补全 executor.onStop/onComplete。

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
