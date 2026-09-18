# LMA 任务系统

> **各层作用速查** (判据/依赖方向/修改注意): [api](api/README.md) · [pipeline](pipeline/README.md) · [pipeline/sense](pipeline/sense/README.md) · [runtime](runtime/README.md) · [data](data/README.md) · [service](service/README.md) · [behavior](behavior/README.md); vanilla 侧: [execute](../vanilla/execute/README.md) · [input](../vanilla/input/README.md) · [output](../vanilla/output/README.md) · 门面: [api](../api/README.md)

> v79.61x — 终版形态: TaskPipeline **11 方法** + 按需接口 (TaskConfigurable/TaskSignalListener/BlockTargetNavigation/PassiveSignalSkeleton) /
> 数据门面 (MaidData) / 无 executor / 无 RetryPolicy (TLM 自动重启 + 信号重触发) /
> GMPM 单一驱动 (needsGameTick 字段已删) / 全局能力 (好感度乘区/节流工具/右键门面)。
>
> **分层判据 (两轴表, v79.61x 定级修正)**: 先问「跨 tick 吗」 —
> 是 → execute 协调器 (vanilla/execute); 否 → 再问「有业务语义吗」 —
> 有 → service 单拍 (task/service); 无 → io 原语 (vanilla/in|out)。
> behavior (brain 习惯) 独立一轴, 不参与两轴。
> 注意: io 判据是**通用性**不是粒度 (HandSwap 是复合但仍是无业务语义的通用工具);
> service 单拍会碰世界 (PressService/FurnaceService.collectResult) — 碰不碰世界不是判据。

## 如何读这个包

```
task/
├── api/         写新Pipeline? → 看这里 (接口 + 注册 + 配置GUI工厂)
├── runtime/      调度/驱动/FSM/卸载清理  → 看这里
├── data/         键表/数据门面/开关      → 看这里
├── sense/        环境信号 (扫描/边沿/常量/缓存; 与 pipeline/sense 被动配对)
├── gui/          任务树 + 配置屏 (无独立 README, 见下方「写一个新Pipeline」与 api/)
├── behavior/     Brain 默认行为 (吃/收集/修/可爱闲置)
├── passive/      脱管线被动骨架: PassiveTask + PassiveDispatcher (四道闸) + PassiveConfigUtil; impl/ = 纯触发型 3 (结构/节日/稀有群系)
├── quest/        任务树数据层: QuestNode + QuestTreeLoader + QuestTreeLayout + QuestProgressReader (占位未接线)
├── pipeline/     任务实现 (16 顶层主动管线 + sense/ 7 被动管线)
└── service/      业务算法服务 (配方/名单/工具/乘区/执行细节) + [harvest/](service/harvest/README.md) 采集/导航/自救/空置域四域协调器 (V5 自 vanilla/execute 迁入)
```

> 数量口径: **一律以 `docs/ARCHITECTURE.md` 顶部实测基线表为准** (本 README 不抄数字)。
> 任务清单: `docs/design/pipeline-inventory.md` (主动 21 + 被动 5 管线 + 纯触发 3)。

## 子包索引 (131 类, 逐包 `find` 实测 2026-09-13 — 改结构必须同步此表)

| 子包 | 类数 | 一句话职责 | 明细 README |
|---|---|---|---|
| **顶层** `TaskRegistryManifest.java` (145 行) | 1 | **任务注册唯一事实源**: 5 个列表 (ALWAYS 13 / NUMEN 1 / CREATE 6 / CBC 2 / PASSIVE 5) + **启动期 fail-fast 三连** (被动注册缺失 / 被动驱动漂移 / 纯触发注册缺失) + `Drive` 声明 (谁 tick 我); **加任务只改这里** | (本节) |
| `api/` | 7 | **接口契约** (管线/可配置/注册中心/信号/屏工厂/UID) — 改它影响全部管线 | [api/README.md](api/README.md) |
| `pipeline/` | 23 | **流程判定**: 21 主动管线 + 被动 sense 子包 (14 类中部分) | [pipeline/README.md](pipeline/README.md) |
| `service/` | 31 | **业务算法 + 单拍编排** (含 harvest 子包 13 类: 采集/导航安全/自救/空置域) | [service/README.md](service/README.md) |
| `runtime/` | 11 | **运行态**: GMPM 驱动 / Dispatcher 生命周期 / FSM 基类 / 看门狗 / 卸载清理 | [runtime/README.md](runtime/README.md) |
| `data/` | 10 | **NBT 读写唯一入口** (键常量/缓存纪律/清理) | [data/README.md](data/README.md) |
| `gui/` | 20 | **per-task 配置屏** (屏↔任务↔配置键 + 配置读写链路) | [gui/README.md](gui/README.md) |
| `sense/` | 14 | **环境感知层 (信号生产)**: 扫描/规则/边沿/预算/缓存/结构感知/广播 — ⚠ **与 `pipeline/sense` 是两个包** (那边是信号消费的 7 个被动管线) | [sense/README.md](sense/README.md) |
| `behavior/` | 5 | **女仆行为** (吃/修/等 brain 行为补充) | (见包内注释) |
| `passive/` | 6 | **纯触发型被动** (structure_sense/festival/rare_biome — 无 pipeline, PassiveDispatcher 驱动) | [passive/README.md](passive/README.md) |
| `quest/` | 4 | **任务/成就树** 数据/加载/布局 | [quest/README.md](quest/README.md) |

> **改结构必读**: 本表类数由 `find <子包> -name '*.java' | wc -l` 实测; `docs/ARCHITECTURE.md` 顶部基线表是**唯一数字真相源**, 改完同步两处。
> **子包 README 义务**: 新增类必须写进对应子包 README (守护测试 `ReadmeDriftGuardTest.folderReadmesCoverTheirOwnClasses` 会让"只加代码不写文档"变红)。

## 快速导航

### api/ — 开发者写新Pipeline只看这个
| 文件 | 什么 |
|------|------|
| `TaskPipeline.java` | 核心接口 **11 方法**, 仅 `taskType()` 抽象: `tick(w,m)` (GMPM 每 tick 驱动) / `executeInterval()` (工作站节拍, WorkStationPipeline 覆写 30) / `workPointTask()` (骑乘调度) / 生命周期 `interrupt()` `onCleanup()` `isLongRunning()` `priority()`; 验证/展示 `validate()` `steps()` `isTargetBlock()`。全默认安全空 |
| `TaskConfigurable.java` | 配置维度 (按需 implements, 全默认)。配置GUI/`handleConfigAction`/`pipelineData`(PL 内存态)/`pipelineConfig`/`collectFilter`/`enableWorkEat`/**`switchScope()` 开关归属声明** (GLOBAL/PER_MAID — 提交门/运行判定/清理三处同源, v79.63) |
| `TaskSignalListener.java` | 信号维度 (按需 implements)。`onSignal()` 环境信号、`onPlayerTrigger()` 按键触发 |
| `TaskRegistry.java` | 注册入口: `register(type, pipeline[, drive])` (主动) / `registerPassive(type, pipeline[, drive])` (被动) / `registerPassive(type)` (纯触发占位)。**`drive` = 驱动模式声明, 与 `TaskRegistryManifest` 同源**; 2 参重载默认主动 ACTIVE / 被动 GMPM_PASSIVE; 注册面传错驱动/与规格表漂移 → **启动即炸** (v79.63)。可见性由任务树 TaskToggle 管理 |
| `TaskConfigGuiFactory.java` | 配置 GUI 工厂 (黑白名单/自定义屏) |

### runtime/ — 任务怎么跑起来的
| 文件 | 什么 |
|------|------|
| `TaskDispatcher.java` | 生命周期门面: `submit()` `cancel()` `complete()` `fail()` `timeout()` `submitPassive()` `cancelPassive()`。**无重试** — 主动任务 TLM 任务栏自动重启, 被动靠信号重触发 |
| `GameTickPipelineManager.java` | **单一驱动源**: 每 tick 驱动所有 in_progress 主动管线 tick; 心跳 20t (仅 isLongRunning) / 看门狗 / 被动位掩码节流 / PL flush |
| `TaskStateMachine.java` | FSM 基类 (10 = 9 common + CannonLoad (CBC 门控, 双平台)): 子类定义 `S` 枚举 → `tick(S, w, m)` 返回下状态; 状态内存化 (MaidData.pl); 转换图校验 + onEnter/onExit 钩子; workStationGated 复用 WorkStationPipeline.gate (furnace/jukebox) |
| `TaskTickHandler.java` | 事件入口: ServerTick → 驱动; ServerStopping → PL flush + 广播节流归零 |
| `TaskStateManager.java` | 状态写入: `init()` `heartbeat()` `clearAll()` |
| `MaidUnloadRegistry.java` + `EntityCleanupListener.java` | 卸载统一清理 (13 静态缓存 + PL flush, 幂等) |
| `WatchdogMath.java` | 纯函数 (超时判定)。注: 原并列的 `PassiveRotation.java` 已随被动系统脱管线改造 (2026-08-16) **删除** — 被动不再用轮转预算 |

### data/ — 数据和配置
| 文件 | 什么 |
|------|------|
| `TaskKeys.java` / `DataKey.java` | 键表: TaskKeys 字符串常量 + DataKey 类型化键 (~40, 引 TaskKeys) + CLEAR_ALL_KEYS 终结清理集合 |
| `MaidData.java` | 数据门面: get/put/has/remove (类型化) + PL 内存态 (pl/flushPl) + CFG 直读 |
| `FlowTaskData.java` / `TaskMetaData.java` | 便捷门面 (lma_flow_* / lma_task_*, 内部走 MaidData) |
| `TaskToggle.java` | 启停/可视 (task_toggles.json, Gson) |
| `PipelineContext.java` / `PipelineResult.java` | 验证输入/输出 (record) |

> **数据管理完整约定**: `docs/conventions/data-management.md` — MaidData 门面 + DataKey + MaidUnloadRegistry + 写代码规则。**新增 DataKey 键必须声明清理归属** (DataKeyConsistencyTest 守护)。

### service/ — 共享静态服务
| 文件 | 什么 |
|------|------|
| `MaidFavorability.java` | 好感度双乘区: `workSpeedMultiplier` (效率) / `costMultiplier` (消耗) / `workTicks(maid, base)` (效率计时, v79.61x 收敛 4 处) — 管线自己乘, 每级可配 |
| `ItemFilters.java` | 黑白名单过滤 + `effectivePair(cfg, gBlack, gWhite)` (v79.61x 收敛 pair 解析) |
| `TaskConfigs.java` | 管线配置读取 (get(maid, taskType)) |
| `HarvestTarget.java` | 采集目标定义 (含 TOOL_RESERVE_DURABILITY) |
| `AiControlGate.java` | AI 操控权限 |
| `ArmTransferService.java` | 搬运 (读源/取货/放货/物品标识 itemId/findMaidItem) |
| `FurnaceService.java` | 熔炉配方扫描/原料解析/生效名单 (validateSmelt 返回失败文案) |
| `HaqiService.java` | 哈气执行细节 (挥击 doHit/音效 playSound + 音频清单常量) |
| `BlockInteractService.java` | 全局右键门面 (距离+交互) |
| `RecipeResolver.java` | 配方链解析 |
| `MaterialChecker.java` / `MaterialReport.java` | 材料充足性检查 (required vs available → 缺口 report; CraftChainPipeline.validate 消费) |

### 全局工具 (vanilla/input/maid/)
| 工具 | 什么 |
|------|------|
| `ThrottleUtil.java` | 节流/CD: `shouldFire(maid, key, interval)` — 当 CD 间隔用一个数字 |
| `MaidStateReader.java` / `MaidInventorySpace.java` 等 | 女仆状态/背包读取 |

## 数据存储三层模型

| 存储层 | API | NBT键 | 清理 | 用途 |
|--------|-----|-------|------|------|
| 全局共享 | `FlowTaskData` `TaskMetaData` | `lma_flow_*` `lma_task_*` | 调度层 clearAll | 任务类型/状态/目标 |
| 管线临时 | `pipelineData(maid)` → **MaidData.pl (内存态)** | `lma_pl_<task>` | onCleanup 自动 | tick 零 NBT; flush (心跳 20t/离开/ServerStopping/终结) |
| FSM 状态 | `MaidData.pl(<type>.fsm)` | `lma_pl_<type>.fsm` | clearState | 状态机状态 (内存态) |
| 管线持久 | `pipelineConfig(maid)` → MaidData.cfg | `lma_cfg_<task>` | 手动 | 材料锁定/任务配置 |
| 节流键 | `ThrottleUtil` | `lma_throttle_<key>` | 自过期 | CD/防刷屏 |

## 生命周期

```
submit() → validate() → TaskStateManager.init() → STATE_IN_PROGRESS
                                        │
              ┌──── 执行 (单一驱动) ─────┤
              │ GMPM.tickActive: 每 tick → pipeline.tick() (所有 in_progress 主动管线)
              │ 工作站: 到达 TARGET_POS → executeInterval(30) 节拍 → 工作单元 → SUCCESS 计数
              └──────────────────────────┘
                                        │
   complete() / cancel() / fail() / timeout()
                                        ▼
      interrupt() → onCleanup() → clearPipelineData() → clearAll()
                                        │
    主动任务失败 → TLM 任务栏自动重启 (~30t, 天然无限)
    被动任务失败 → cancelPassive → 信号重触发 (天然节流)
```

## 写一个新Pipeline (v79.46b 终版)

```java
// 1. 最小管线 (继承 LMAT.Task 即可)
class MyTask extends LMAT.Task {
    MyTask() { super("my_task"); }
    @Override public void tick(ServerLevel w, EntityMaid m) { doWork(w, m); }
}
LMAT.register("my_task", new MyTask());   // 一行注册

// 2. 直接实现 (GMPM 每 tick 驱动)
public final class MyPipeline implements TaskPipeline, TaskConfigurable {
    @Override public String taskType() { return "my_task"; }
    @Override public boolean isLongRunning() { return true; }  // 心跳续命 (1200t 看门狗)
    @Override public void tick(ServerLevel w, EntityMaid m) {
        if (TaskKeys.STATE_CANCELLED.equals(FlowTaskData.getState(m))) return;
        // 每 tick 逻辑 (高频临时状态用 pipelineData 内存态)
    }
    @Override public void onCleanup(EntityMaid m) {
        // 清理静态缓存 (如有) + TaskPipeline.super.onCleanup(m); // 默认清 pipelineData
    }
    @Override public PipelineResult validate(ServerLevel w, EntityMaid m, PipelineContext c) { ... }
}

// 3. 工作站类任务 (合成/敲钟) — 继承 WorkStationPipeline (furnace/jukebox 相位机已迁 FSM, 见下)
public final class MyStation extends WorkStationPipeline {
    @Override public String taskType() { return "my_station"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos pos, BlockState state, EntityMaid m) {
        return state.is(Blocks.FURNACE);   // 抽象方法 — 必覆写 (防默认 false 死循环)
    }
    @Override protected TaskResult executeOne(ServerLevel w, EntityMaid m, BlockPos pos) {
        return doWork(w, m, pos) ? TaskResult.SUCCESS : TaskResult.CONTINUE;  // executeInterval(30) 节拍调用
    }
}

// 4. 状态机 (真状态机才用 — 9 common: ArmTransfer/BlockInteract/Crank/Power/Press/Mix/MaidAssembly/Furnace/Jukebox; forge 另加 CannonLoad)
public final class MyStateMachine extends TaskStateMachine<MyStateMachine.S> {
    enum S { IDLE, WORKING }
    @Override protected Class<S> stateClass() { return S.class; }
    @Override protected S initialState() { return S.IDLE; }
    @Override public String taskType() { return "my_stateful"; }
    @Override protected Map<S, Set<S>> transitions() { return Map.of(S.IDLE, Set.of(S.WORKING), S.WORKING, Set.of(S.IDLE)); }
    @Override protected S tick(S s, ServerLevel w, EntityMaid m) { ... }
}
LMAT.register(new MyStateMachine());
```

## 引擎链路

- **按键触发**: KeyMapping (key.lma.trigger, 选项→控制 可重绑) → InteractTriggerPacket(keyId) → KeyTriggerRegistry → 服务端扫描 owned 女仆 → `pipeline.onPlayerTrigger(maid, player)` (TaskSignalListener; 首个消费者 block_interact, v79.51)
- **配置动作**: TaskConfigActionPacket → `pipeline.handleConfigAction(maid, action, payload)` (TaskConfigurable; 通用动作 0-15, 自定义 16+)
- **环境信号**: EnvSenseBroadcaster → `pipeline.onSignal(maid, snap, signalId)` (TaskSignalListener; 被动管线)
- **右键交互**: BlockInteractService.interact (全局门面: 距离 + FakePlayerInteract) — 管线/ AI 工具/未来功能共用

## 全局能力清单 (新功能优先复用)

MaidData (数据) / (工具判断 → vanilla/input/item/ToolJudge) / PathingApi (导航) / MaidChatBubbleApi (气泡) / MaidEmojiApi (表情) /
SenseApi (扫描) / MaidFavorability (好感度乘区) / ThrottleUtil (节流 CD) / BlockInteractService (右键门面) /
MaidUnloadRegistry (卸载清理) / CombatOutput (输出原语) / (容器 → vanilla/input/container/NearbyContainerScanner 扫 + vanilla/output/container/ContainerOutput 写)
