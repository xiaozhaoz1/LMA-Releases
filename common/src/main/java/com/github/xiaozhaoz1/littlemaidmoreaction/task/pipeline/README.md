# task/pipeline — 流程判定层 (主动管线)

**作用**: 每个任务一个管线, 只写「什么状态下干什么」的判定。三种形态: 直连 tick / FSM 状态机 / 工作站节拍。
**v79.61x S1**: 相位机统一进 TaskStateMachine — furnace/jukebox 已从 WorkStationPipeline 迁移为 FSM (workStationGated 复用 WorkStationPipeline.gate 门); WorkStationPipeline 现仅剩 craft_chain/bell_ring 两个工作站子类。

**判据 (什么进这里)**:
- 任务流程: 状态枚举 / transitions / 节拍 / 守卫链
- 业务算法 → task/service; 跨 tick 世界操作 → vanilla/execute; 通用交换 → vanilla/io

**依赖方向**: task/api + task/data + task/service + vanilla/*; 管线之间互不依赖。
**已裁定反向例外 (2026-08-15)**: 主动侧 WorkStationPipeline→TaskDispatcher.complete / ArmTransferPipeline→fail (容器消失终态), 被动侧 submitPassive/cancelPassive 自闭环 (v79.58 用户裁定) — 仅限这个 Dispatcher 面, 不扩散其他 runtime 面。

> 数量口径: 以 `docs/ARCHITECTURE.md` 基线表 + `docs/design/pipeline-inventory.md` 为准 (本 README 不抄数字)。

---

## 一、全部管线明细 (任务 / 职责 / 界面 / 配置键 / 测试)

> **维护约定**: 新增/删除管线必须同步本表; 改 `taskType()`、配置键名、界面类也必须改这里。
> 表的易漂字段 (taskType/类/基类/界面/配置键/测试) 由 `_audit/extractPipelineFacts.js` 机械提取后人工补"职责"列 —— 不要手抄。

| 任务 | 管线类 | 形态/基类 | 职责 (干什么) | 界面 (GUI/Screen) | 配置键 (pipelineConfig) | gametest |
|---|---|---|---|---|---|---|
| `maid_assembly` | MaidAssemblyPipeline | FSM (`TaskStateMachine`) | Create 便携装配: 玩家往 GUI 槽塞机器+材料 → 相位机加工 → 产物槽; **硬门控: 女仆须有食物** | 便携装配屏 (MaidAssemblyScreen/Menu, CreateCompatClient 注册) | — | ✓ |
| `crank` / `mix` / `power` / `press` | Crank/Mix/Power/PressPipeline | FSM (`MoveToBlockStateMachine`) | Create 机械任务 (曲柄/搅拌/发电/压块): 找机器 → 走近 → 按配方作业 | TLM 任务设置页 (无独立屏) | — | ✓ |
| `running_belt` | RunningBeltPipeline | 直连 tick (`implements`) | 跑步带: 在带上跑步驱动机械网络 | TLM 任务设置页 | — | ✓ |
| `cannon_load` | CannonLoadPipeline | FSM (`TaskStateMachine`) | CBC 火炮装填: 找炮架→开闩→装弹→合闩 (可配导航模式) | TLM 任务设置页 | `load_step` `mount_pos` `cd_mount` `loaded_mounts` `clear_step` `nav_mode` `last_clear_all` `last_close_tick` | ✓ |
| `ai_control` | AiControlPipeline | 直连 tick, `isLongRunning` | AI 操控: Numen 对话指挥世界操作; **注册级门控 (需 Numen) + AiControlGate 权限闭环** | AI 操控设置屏 (AiControlConfigMenu) | `llm_provider` `voice` | ✓ |
| `arm_transfer` | ArmTransferPipeline | FSM 四态 (`TaskStateMachine`) | 搬运: 去取→取→去放→放 (容器消失 → fail 终态) | TLM 任务设置页 | — | ✓ |
| `bell_ring` | BellRingPipeline | **工作站** (`WorkStationPipeline`) | 敲钟: 走到钟 → 按**间隔**敲 (节流在 executeOne) | 敲钟设置屏 (BellRingConfigScreen, 经 TaskConfigGuiFactory 生成) | **`ring_interval`** (空则全局 `active.ring_interval_ticks`, 默认 **30t**) | ✓ |
| `block_interact` | BlockInteractPipeline | FSM (`TaskStateMachine`, 单态自环) | 方块交互: 对**绑定方块**按定时器交互 (绑定存 `pos`) | 方块交互设置屏 (BlockInteractConfigMenu) | `pos` `timer` `interval` | ✓ |
| `brush` | BrushPipeline | 直连 tick (`implements`) | 刷扫可疑方块 (考古式) | TLM 任务设置页 | — | ✓ |
| `campfire` | CampfirePipeline | 直连 tick, `workPointTask` | 篝火烤食物 (30t 节拍; 可烤判定走 CAMPFIRE_COOKING 配方) | TLM 任务设置页 | — | ✓ |
| `collect_wood` / `collect_ore` | ChainHarvestPipeline | 直连 tick, **Mode 参数化变体** (一个类两任务) | 连锁采集: 找目标→走近→蓄力→连锁破块 | TLM 任务设置页 | — | ✓ |
| `craft_chain` | CraftChainPipeline | **工作站** (`WorkStationPipeline`) | 合成链: 解析配方→收集材料→合成→交付 | 合成链设置屏 (CraftChainConfigScreen) | `max_products` 等 (见屏幕) | ✓ |
| `dam_fill` | DamFillPipeline | FSM (`TaskStateMachine`) | 填坝 / 排水 (两模式) | 填坝设置屏 (DamFillConfigMenu) | `input` `drain_enabled` | ✓ (⚠ **换手必须保全旧物**: 排水 `ensureBucket` 把空桶换到主手时, 原主手物品**必须**交 `HandSwap.stashOrDrop` 回包/落地 — 直接 `setItemInHand` 覆盖 = 物品永久消失, 错题 #162 族 / lessons #354 ✓) |
| `farm` | FarmPipeline | 直连 tick | 区域制收割+播种 (委托 `FarmExecute`; 区域/箱子绑定) | 农田设置屏 + MaidListScreen | — | ✓ |
| `furnace` | FurnacePipeline | FSM (`TaskStateMachine`, `workStationGated`) | 熔炉: **收产物 → 加料 → 加燃料** 三相位轮转 (用户裁定口径) | 熔炉设置屏 (经 TaskConfigGuiFactory 生成) | — | ✓ |
| `jukebox` | JukeboxPipeline | FSM (`TaskStateMachine`, `workStationGated`) | 唱片机: 插盘→播放→弹出→取回 (四相位) | 唱片机设置屏 | — | ✓ |
| `explorer_map` | ExplorerMapPipeline | 直连 tick | 探险家地图: 已探明播报 (一次性) | TLM 任务设置页 | `explorer_announced` | ✓ |
| `jiuhu_milk` | JiuhuMilkPipeline | 直连 tick | 酒狐奶: 喂食/挤奶流程 | TLM 任务设置页 | `lma_milk_last_feed` | ✓ |
| `self_rescue` | SelfRescuePipeline | 直连 tick | 自救: 濒死时自救流程 | TLM 任务设置页 | — | ✓ |
| `void_excavation` | VoidExcavationPipeline | 直连 tick (**区块认领池**) | 挖空置域: 认领区块→传送/走到位→逐层挖空 (池/缓存见 `task/service/harvest/VoidExcavationPool`) · **传送前「第一层检查」**: 落点须 2 格净空 (先头位 `y+1` 后本体 `y`), 否则第一层头位是未挖地表 ⇒ 传进去头埋方块窒息掉血 ✗ (v79.65.1) | 挖空置域设置屏 (VoidExcavationConfigScreen) | `size` `destroy_list` `no_pathfind` `min_y` | ✓ |

**"界面"三态**: ① **独立屏** (有 Screen 类, 经 `CreateCompatClient`/`LmaForgeClientEntry` 注册) ② **工厂生成屏** (走 `getConfigGuiProvider` → `TaskConfigGuiFactory`) ③ **TLM 任务设置页** (只有全局配置, 无 per-maid 屏)。

---

## 二、怎么连接 (链路细节 — 改代码前必读)

### 1. 驱动链 (谁 tick 我)
```
玩家在 TLM 任务栏选任务 → TLM 设 maid TLM 任务 (LmaTypedFlowTask)
   → LMA 提交: TaskDispatcher.submit(maid, taskType, target, count)
        ├─ TaskRegistry.validate(...)        ← 失败即拒 (见 §3 陷阱 A)
        └─ TaskStateManager.init → FlowTaskData.setTask + maid.setTask(TLM 任务)
   → GameTickPipelineManager (GMPM) 每 tick 调 pipeline().tick(...)      ← 主体驱动
   → LmaFlowCoordinationBehavior (Brain, 继承 TLM MaidMoveToBlockTask)  ← 只负责**导航**
```
- **GMPM** 是唯一 tick 驱动 (心跳 20t + 看门狗, 仅 `isLongRunning=true` 生效)。
- **Brain 行为**只做导航 (搜索目标 + 写 `WALK_TARGET`), 不做业务。

### 2. 工作站门 (`WorkStationPipeline.gate` — 三种形态共用)
```
TARGET_POS 空
  → 节流 1s 自搜 (searchWorkStation: 有界 ±12/±4, 尊重 home 模式半径, 未加载区块跳过)
  → 命中即 setMemory(TARGET_POS, BlockPosTracker) + BehaviorUtils.setWalkAndLookTargetMemories  ← 起走
未到达 → 每 tick 续写 WALK_TARGET (TLM MoveToTargetSink 靠它推进)
已到达 → 节拍 (executeInterval, 默认 30t) + 目标失效检测 (isTargetBlock 假 → 擦记忆重搜)
```
**覆盖面**: `FurnacePipeline`/`JukeboxPipeline` 虽是 FSM, 但经 **`TaskStateMachine.workStationGated`** 复用**同一个 gate** ⇒ **改 gate 影响全部工作站任务** (furnace/jukebox/bell_ring/craft_chain)。

### 3. 界面 ↔ 配置链路 (per-task 配置怎么落到管线)
```
TLM 任务设置页 → pipeline.getConfigGuiProvider(maid) → TaskConfigGuiFactory.forTask(...)
  → XConfigScreen/XConfigMenu (task/gui/*)
      → LmaTaskConfigScreen.sendSetInt(key,value) / sendAction(action,payload)
      → TaskConfigActionPacket.send(maidId, taskType, action, payload)      ← 客户端→服务端
  → 服务端 TaskConfigurable.handleConfigAction(maid, action, payload)
      → pipelineConfig(maid) = MaidData.cfgOrCreate(maid, taskType())       ← NBT: lma_cfg_<taskType>
  → 管线运行期读同一个 pipelineConfig(maid) (例: BellRingPipeline 读 ring_interval)
```
**无独立屏的任务**只有**全局配置** (`config/ActiveTaskConfig`), 改全局影响所有女仆。

### 4. 导航 (TLM 侧机制 — 为何需要自搜)
```
原版: WALK_TARGET (记忆) → MoveToTargetSink (原版 CORE 活动) → maid.getNavigation().moveTo() → 原版 A* 寻路
TLM : MaidMoveToBlockTask 负责"找目标": searchForDestination + MaidPathFindingBFS (可达性图)
      + getHorizontalSearchRange (半径) + shouldMoveTo (子类谓词) → 选一个**可达**目标块 → 写 WALK_TARGET
LMA : LmaFlowCoordinationBehavior extends MaidMoveToBlockTask + 覆写 shouldMoveTo=(pipeline.isTargetBlock)
      ⇒ TLM 的 BFS 预筛对某些地形会失败 (被堵/水里) ⇒ gate 里**自搜兜底** (见 §2; 先例 VoidExcavation)
```
**给坐标仍不走 ≠ 搜索问题**: 若 `TARGET_POS`+`WALK_TARGET` 都写了却不动, 问题在原版 `MoveToTargetSink`/`PathNavigation` (路径不可达/被堵), 不要再改搜索代码。

---

## 三、已知陷阱 / 改动须知 (每条都有实战教训)

| # | 陷阱 | 规则 |
|---|---|---|
**A** | **`validate` 与运行时不一致** | `validate` 允许的状态, 运行时**必须**也能处理。反例: `validateSmelt` 支持"空 target = 烧任何可烧的", 而 `resolveSmeltIngredient` 遇空 target 直接 `return ""` ⇒ 女仆走到炉子前站着不烧 (实机实证)。**改任一侧必须同时看另一侧**。|
**B** | **per-task 配置写不落盘** | `MaidData.cfg(...)` 对**不存在的 key 返回临时 tag (非引用)** ⇒ 写它 = 静默丢。**写入/统一入口必须用 `cfgOrCreate`** (`TaskConfigurable.pipelineConfig` 已改)。反例: 敲钟间隔改了不生效, 退回全局默认 30t。|
**C** | **`steps()` 与状态枚举是两层** | `steps()` = **用户可见粗粒度** (任务树/命令/AI 上下文); 枚举 = **内部细粒度** (含 IDLE/EAT_RESET)。**改相位/状态必须手动同步 `steps()`** — 二者无自动校验 (#291)。|
**D** | **工作站导航只走共用门** | 改导航 (void)/节拍/到达判定**只能改 `gate`**, 不要在单个管线里再写一套 (furnace/jukebox 也吃它)。|
**E** | **gametest 夹具会掩盖整条链路** | **不要注入 `TARGET_POS`** — 那等于"女仆已在目标旁", 搜索+导航整段没测 (既有 furnace/campfire 测试如此 ⇒ 96 用例全绿而游戏坏了)。移动类: 目标 **>10 格** + 独立 `batch` + 固定清单清场 (#293/#294/#300)。|
**F** | **注册级门控** | 需外部依赖的任务 (ai_control 需 Numen) 在 `TaskRegistry` **注册期**门控; `validate` 只是冗余防御。任务在但依赖缺 ⇒ 提交必须失败 (**不得静默放行**)。|
**G** | **长任务与看门狗** | `isLongRunning=true` 才有心跳续命 + 超时兜底 (工作站族恒 true)。新增"持续作业"任务必须显式 true, 否则被判无进展而中断。|

---

## 四、新增/修改管线流程 (checklist)

1. **抄最像的模板**: 工作站 → `BellRingPipeline`; FSM → `FurnacePipeline`; 直连 → `BrushPipeline`; 变体 → `ChainHarvestPipeline(Mode)`。
2. **四段式长相**: 身份 (`taskType`/`steps`/`isLongRunning`) → 行为 (`tick`) → 配置 (可选) → 私有业务。
3. **FSM**: `tick` 用 switch 分派 — 短状态内联, 长状态拆顶层方法; 状态键保持原样 (行为零变化 > 形状洁癖)。
4. **注册**: `TaskRegistryManifest` 相应列表加一行 — 否则 `TaskRegistryDriftTest` 会红。
5. **同步文档**: 本 README 明细表 + `docs/ARCHITECTURE.md` 数字。
6. **同步测试**: 至少一条 gametest (移动类必须 >10 格真导航) + 含 config GUI 的补配置键断言。

**代表**: FurnacePipeline (FSM, workStationGated) / JukeboxPipeline (FSM) / ArmTransferPipeline (FSM 四态) / ChainHarvestPipeline (Mode 变体) / PressPipeline (FSM 薄壳) / FarmPipeline (委托 FarmExecute) / DamFillPipeline (双模式) / BrushPipeline / CampfirePipeline (workPoint 30t) / VoidExcavationPipeline (区块认领池)。
