# LMA 任务系统

> v67.1 — 引擎上提: onPlayerTrigger 按键触发 / 通用配置动作 / TaskConfigs / 配置GUI

## 如何读这个包

```
task/
├── api/         写新Pipeline? → 看这里 (接口 + 注册)
├── runtime/      调度/状态/tick/FSM      → 看这里
├── data/         数据读写/NBT key/开关    → 看这里
├── gui/          任务树展示
├── behavior/     默认行为 (吃/收集)
├── pipeline/     9个Pipeline实现
└── service/      共享静态服务
```

## 快速导航

### api/ — 开发者写新Pipeline只看这个
| 文件 | 什么 |
|------|------|
| `TaskPipeline.java` | Pipeline接口。21个方法只有 `taskType()` 抽象。默认行为：`enableWorkEat()` `collectFilter()`。数据：`pipelineData()` `pipelineConfig()`。引擎链路：`onPlayerTrigger()` 按键触发、`handleConfigAction()` 配置GUI动作(通用 ACTION_TOGGLE/SET_INT/REMOVE)、`getConfigGuiProvider()` 配置界面 |
| `TaskRegistry.java` | 注册入口：`register(name, pipeline, executor, showInBar)`。15个已注册 |

### runtime/ — 任务怎么跑起来的
| 文件 | 什么 |
|------|------|
| `TaskDispatcher.java` | 中央调度：`submit()` → `validate()` → `init()`。`cancel()` `complete()` `fail()` `timeout()` |
| `TaskStateManager.java` | NBT状态写入：`init()` `heartbeat()` `clearAll()` |
| `TaskStateMachine.java` | 泛型状态机引擎：子类定义 `S` 枚举 → `tick(S, world, maid)` 返回下一个状态。自动管理状态NBT + 转换验证 |
| `TaskEngine.java` | 超时看门狗：检测 `isLongRunning()` 任务超时 |
| `TaskTickHandler.java` | 统一tick入口：`onServerTick` → 主动任务(`needsGameTick`) + 被动任务(`passiveTasks`) |

### data/ — 数据和配置
| 文件 | 什么 |
|------|------|
| `TaskKeys.java` | 所有 `lma_flow_*` / `lma_task_*` NBT key常量 + 状态值 |
| `FlowTaskData.java` | `lma_flow_*` 读写：`getTask/getState/getTick` + `start/initFull/clearAll` |
| `TaskConfigs.java` | 静态上下文取管线配置：`get(maid, taskType)` → pipelineConfig (v67.1 替代各 Pipeline 静态 config()) |
| `TaskMetaData.java` | `lma_task_*` 读写：`getTarget/setTarget` `getInput/setInput` + adapter标记 |
| `TaskExtraData.java` | 动画/唱片机/开关/被动/重试 读写 |
| `PipelineContext.java` | validate输入：target + targetCount + taskId |
| `PipelineResult.java` | validate输出：`ok("msg")` / `failed("msg")` + completed |
| `TaskToggle.java` | 双开关：`isEnabled()` / `isVisible()` + per-maid NBT |
| `RetryPolicy.java` | 重试策略：`NEVER` / `fixed(3)` / 计数器 |

## 数据存储三层模型

| 存储层 | API | NBT键 | 清理 | 用途 |
|--------|-----|-------|------|------|
| 全局共享 | `FlowTaskData` `TaskMetaData` | `lma_flow_*` `lma_task_*` | 调度层 | 任务类型/状态/目标 |
| 管线临时 | `pipelineData(maid)` | `lma_pl_<task>` | `onCleanup` 自动 | 计时器/槽位/进度 |
| 管线持久 | `pipelineConfig(maid)` | `lma_cfg_<task>` | 手动 | 材料锁定/配方缓存/任务配置 |

## 引擎链路 (v67.1)

### 按键触发 — onPlayerTrigger

```
客户端按键 (BlockInteractKeyMapping)
  → InteractTriggerPacket (C→S, 通用)
  → 服务端扫描 10 格内 owned 女仆
  → TaskRegistry.get(当前任务).pipeline().onPlayerTrigger(maid, player)
```

任务支持按键触发只需覆写 `onPlayerTrigger` — 包/扫描/分发全部引擎提供。

### 配置GUI动作 — TaskConfigActionPacket + handleConfigAction

```
客户端屏幕按钮
  → TaskConfigActionPacket (C→S, 通用, ID 3)
  → 服务端 isOwnedBy 校验 → TaskRegistry.get(taskType).pipeline().handleConfigAction(maid, action, payload)
```

引擎通用动作 (payload 带 "key"): `ACTION_TOGGLE` (boolean取反) / `ACTION_SET_INT` (key+value) /
`ACTION_REMOVE` (删key) / `ACTION_SET_LIST` (v67.3: key+逗号分隔value → ListTag)。
自定义动作从 16 起, 覆写 handleConfigAction。
黑白名单 (v67.3): `ItemFilters` (task/service) — 黑名单优先, 白名单非空限定, `modid:*` 通配,
per-maid 名单 (pipelineConfig blacklist/whitelist) 非空覆盖全局 (Cloth Config)。

### 配置数据同步

```
屏幕 initAdditionWidgets → RequestTaskConfigPacket (C→S)
  → 服务端 pipeline.getConfigNbt(maid) (默认 = pipelineConfig)
  → ReplyTaskConfigPacket (S→C) → menu.updateConfig → 屏幕读取
```

## 生命周期

```
submit() ──→ TaskRegistry.validate() ──→ TaskStateManager.init() ──→ STATE_IN_PROGRESS
                                                                         │
                        ┌────────── 执行 ──────────┐                    │
                        │ Brain ~100tick: executor.execute()             │
                        │ TaskTickHandler 每tick: tick() [needsGameTick] │
                        └──────────────────────────┘                    │
                                                                         │
    ┌────────────────────────────────────────────────────────────────────┘
    ▼                        ▼                        ▼
complete()              cancel()              fail() / timeout()
    │                        │                        │
    ▼                        ▼                        ▼
executor.onComplete()   interrupt()             onTimeout()
    │                        │                        │
    ▼                        ▼                        ▼
onCleanup() ← 统一 ── onCleanup() ← 统一 ── interrupt()→onCleanup()
    │                        │                        │
    ▼                        ▼                        ▼
clearPipelineData()    clearPipelineData()    clearPipelineData()
    │                        │                        │
    ▼                        ▼                        ▼
clearAll()              clearAll()             clearAll() → retry?
```

## 写一个新Pipeline

### 标准 Pipeline (Brain ~100tick)

```java
public final class MyPipeline implements TaskPipeline {
    @Override public String taskType() { return "my_task"; }

    @Override
    public PipelineResult validate(ServerLevel w, EntityMaid m, PipelineContext c) {
        return hasMaterials(m) ? PipelineResult.ok("ready")
                               : PipelineResult.failed("no materials");
    }

    public static IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m,
                                                 BlockPos p, CompoundTag d) {
                doWork(w, m);
                return TaskResult.SUCCESS;
            }
        };
    }
}
```

### 状态机 Pipeline (每tick, extends TaskStateMachine)

```java
public final class MyStateMachine extends TaskStateMachine<MyStateMachine.S> {
    enum S { IDLE, WORKING }

    @Override protected Class<S> stateClass()  { return S.class; }
    @Override protected S initialState()       { return S.IDLE; }
    @Override public String taskType()          { return "my_stateful"; }
    @Override public boolean needsGameTick()    { return true; }

    @Override protected Map<S, Set<S>> transitions() {
        return Map.of(S.IDLE, Set.of(S.WORKING), S.WORKING, Set.of(S.IDLE));
    }

    @Override protected void onEnter(S state, ServerLevel w, EntityMaid m) {
        if (state == S.WORKING) pipelineData(m).putInt("timer", 100);
    }

    @Override protected S tick(S s, ServerLevel w, EntityMaid m) {
        return switch (s) {
            case IDLE    -> canStart(m) ? S.WORKING : null;
            case WORKING -> {
                int t = pipelineData(m).getInt("timer");
                pipelineData(m).putInt("timer", t - 1);
                yield t <= 0 ? S.IDLE : null;
            }
        };
    }
}
```
