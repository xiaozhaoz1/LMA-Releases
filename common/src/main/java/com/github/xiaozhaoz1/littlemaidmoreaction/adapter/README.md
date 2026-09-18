# adapter — TLM 桥接层

**作用**: LMA 任务系统 ↔ TLM 的翻译官 —— 任务注册进 TLM TaskManager、TLM 任务切换翻译成 LMA 提交、气泡/进度显示到 TLM 界面。

> **这是最容易改坏的一层**: 它是两套框架的接口面 (TLM `IMaidTask`/Brain/事件 ↔ LMA 管线/数据/运行时)。
> 本 README 的核心是 **§1 TLM 事件 → LMA handler 映射表** 与 **§2 注册点表** —— 改这一层前必须先看这两张表。

**依赖方向**: TLM API + task/* + data + config; **被** 平台入口 (forge/neoforge) 与 `init/LmaRegistrar` 引用。

---

## 一、TLM 事件 → LMA handler 映射表

### 1. 事件总线订阅 (GAME 总线, 自动注册)
| 事件 (TLM/Forge) | handler | 文件 | 干什么 |
|---|---|---|---|
`MaidTaskEnableEvent` | `onMaidTaskEnable` | TlmEventAdapter | ① 为 LMA 任务补 GUI 启用条件描述 ② **魂符/区块恢复**路径: 读 `FlowTaskData` 状态决定"续跑 or 重新 `TaskDispatcher.submit`" |
`EntityJoinLevelEvent` | `onEntityJoin` | TlmEventAdapter | 女仆上线时的恢复补齐 (与上方同一恢复逻辑的入口) |
`MaidTickEvent`(每 tick) | `onMaidTick` | TlmTaskMonitor | **TLM 任务切换监控** → 翻译为 LMA `TaskDispatcher.submit` (切到 LMA 任务) / `cancel` (切走) |
| (无 `@SubscribeEvent`, 被上面调用) | `LmaTaskGuiHandler.handle(MaidTaskEnableEvent)` | LmaTaskGuiHandler | 纯 GUI 面: `isSimple(taskType)` → 永远可执行; 复杂任务 → 需要 AI 预设 (`hasTaskData`) |

> ⚠ **`@EventBusSubscriber` 在 neoforge 的 GAME 总线曾实测失效** (错题: v79.18 `DefaultGeckoAnimationEvent` 收不到) ⇒ 关键监听**另在平台入口构造器手动注册** (见 §2)。新增 GAME 总线监听时**必须验证 neoforge 侧真的被调用** (加临时日志跑一轮)。

### 2. LMA ↔ TLM 类型映射 (翻译面)
| TLM 侧类型/接口 | LMA 实现 | 语义 |
|---|---|---|
`IMaidTask` | `LmaFlowTaskBase` (抽象) → `LmaTypedFlowTask` (每任务一个) / `LmaFlowTask` (旧 `lma:flow_task`) | 任务注册主体: UID = `lma:task/<taskType>`; `getName/getIcon` 走 `LmaTaskTypeRegistry`; `getTaskConfigGuiProvider` → `TaskConfigGuiFactory` |
| `MaidMoveToBlockTask` | `LmaFlowCoordinationBehavior` | **Brain 导航行为**: `shouldMoveTo` 委托 `pipeline.isTargetBlock`; `getHorizontalSearchRange` 覆写 (home 模式才受 restrict 限制); `searchForDestination` 是搜索入口 (**TLM BFS 可达性预筛对某些地形会失败** → 管线侧自搜兜底, 见 `task/pipeline/README.md` §2.4) |
| `IMagicCastingAnimationProvider` | `LmaMagicCastingProvider` (+ `LmaCastingState`) | TLM 施法动画 provider: 提供施法状态机 (`isCancelled` 等) |
| (自定义 Brain Memory) | `LmaMemoryModuleRegistry` | 注册 LMA 自有的 memory module (如 `NAV_TARGET`), 由 `init/LmaRegistrar` 在初始化期调用 |

### 3. taskType ↔ TLM 注册表
| 类 | 职责 |
|---|---|
`LmaTaskTypeRegistry` | ① `taskType` ↔ TLM task 实例 (含**变体工厂**: 一个类多任务, 如 `ChainHarvestPipeline(Mode)`) ② `isSimple(taskType)` (GUI 启用条件) ③ 图标/名称 ④ `extractTaskType(uidPath)` (从 `lma:task/<type>` 反解) |
`TaskTypeUid` (task/api) | UID 字符串规约 (`sanitize`) — 两侧共用的**唯一命名规则** |

---

## 二、注册点表 (谁把 LMA 挂进 TLM / 平台)

| 注册内容 | 位置 | 时机 |
|---|---|---|
TLM 任务 (每 taskType 一个 `LmaTypedFlowTask`) | `LmaTaskTypeRegistry` ← `TaskRegistry` 注册期 | 任务注册 (与 manifest 同源) |
Brain 行为 (`LmaFlowCoordinationBehavior`) | `LmaFlowTaskBase.createBrainTasks` / `createRideBrainTasks` (骑乘版) | maid brain 构建时 (TLM 调) |
GAME 总线关键监听 (neoforge 兜底) | `LmaNeoForgeEntry` / `LmaNeoForgeClientEntry` 构造器 (`NeoForge.EVENT_BUS.addListener`) | 入口构造 |
自定义 memory module | `init/LmaRegistrar` ← `api/LittleMaidMoreActionExtension` | 初始化 |
施法 provider | `api/LittleMaidMoreActionExtension` | 初始化 |
任务设置 GUI 工厂 | `TaskConfigGuiFactory` (被 `LmaTypedFlowTask.getTaskConfigGuiProvider` 调) | 打开设置页时 |

---

## 三、已知陷阱 (改前必读)

| # | 陷阱 | 规则 |
|---|---|---|
**A** | **GAME 总线自动订阅在 neoforge 不可靠** | 关键监听手动注册 (平台入口构造器); 新增监听**必须实测**双平台被调用。|
**B** | **Brain 行为会与管线抢 WALK_TARGET** | 原 `MaidMoveToBlockTask` 在蓄力/工作时会**重写 WALK_TARGET** (拽向它自己搜到的目标) — 连锁采集曾因此"破块落空" ⇒ `LmaTypedFlowTask` 对 `collect_wood/collect_ore` **刻意不注册 Brain** (LMA 自导航唯一化)。改这类任务时先确认**谁在写导航记忆**。|
**C** | **TLM 任务 UID 是跨层契约** | `lma:task/<sanitized taskType>` — 改 `taskType` 或 sanitize 规则 = **老存档任务丢失** (TLM 按 UID 存任务), 需迁移。|
**D** | **恢复路径有两个入口** | `MaidTaskEnableEvent` 与 `EntityJoinLevelEvent` 都走恢复逻辑 (魂符/区块加载/跨 session) ⇒ 改其一要同步另一, 否则"魂符收起→放出后任务没了/重复提交"。|
**E** | **GUI 启用条件 ≠ 运行门控** | `LmaTaskGuiHandler` 只影响"TLM 界面里能否选"; 真正的运行门控在 `TaskRegistry` 注册期 + `validate` (参 `task/pipeline/README.md` §3-F)。|

---

## 四、新增桥接的 checklist

1. **先问是哪一类**: ① 事件订阅 (→ §1.1, 记得双平台验证) ② TLM 类型实现 (→ §1.2) ③ 注册项 (→ §2)。
2. **命名/UID 别乱动**: 任何进 TLM 的标识 (UID/taskType) 都是**存档契约**。
3. **补本 README 两张表** (事件→handler、注册点)。
4. **双平台各跑一次**: 事件监听与注册是**平台相关**的, forge 通过 ≠ neoforge 通过 (反之亦然)。
5. **改 Brain 行为前**: 确认目标任务的导航写入方唯一 (参陷阱 B)。
