# 被动任务系统 — 设计文档

> v61 (2026-07-26) — 从装配开发中提炼。与主动任务隔离，可并行运行多个。

## 动机

装配任务(v59)实现了 Pipeline 的所有基础设施：NBT 持久化、多阶段状态机、冷却节流、默认行为、三重消耗链。但这些只用于一个主动任务(`showInBar=true`)。

需要第二类任务 — **被动任务** — 不占 TLM 任务栏、可与主动任务并行、支持状态追踪和长期运行。

## 架构

```
主动任务 (lma_flow_task)              被动任务 (lma_passive_{taskType})
─────────────────────────            ──────────────────────────────
单任务独占                             多任务并行
showInBar=true                        showInBar=false
TLM GUI 选择触发                       代码/事件/规则 触发
Brain ~100tick 驱动 + GameTick 驱动    仅 GameTick 驱动
```

### NBT 隔离

```
maid.getPersistentData():
  lma_flow_task = "craft_chain"         ← 主动
  lma_flow_state = "in_progress"
  lma_passive_monster_log = "in_progress"  ← 被动1
  lma_passive_cultivation = "in_progress"  ← 被动2
  lma_task_enabled_weather_rain = false    ← 开关
```

## Pipeline 写作模板

### 最小模板

```java
public class MyPassivePipeline implements TaskPipeline {
    private static final String KEY = "lma_passive_my_task";

    @Override public String taskType() { return "my_task"; }
    @Override public boolean needsGameTick() { return true; }  // 走每tick
    @Override public boolean isLongRunning() { return true; }   // 心跳防超时
    @Override public boolean isTargetBlock(...) { return false; } // 原地

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        // 冷却检查
        CompoundTag d = maid.getPersistentData().getCompound(KEY);
        int cd = d.getInt("Cd");
        if (cd > 0) { d.putInt("Cd", cd - 1); maid.getPersistentData().put(KEY, d); return; }
        d.putInt("Cd", 200); // 10秒一次

        // 核心逻辑
        doSomething(world, maid);

        maid.getPersistentData().put(KEY, d);
    }

    public IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                tick(w, m); return TaskResult.CONTINUE;
            }
        };
    }
}

// 注册
var pl = new MyPassivePipeline();
TaskRegistry.register("my_task", pl, pl.executor(), false);
```

### 带状态持久化

```java
// 参考 MaidAssemblyPipeline:
//   NBT 键: maid PersistentData → "my_task"
//   子键: State/Cd/Timer/Data
//   读写: d.putInt/d.getString, 最后 maid.getPersistentData().put(KEY, d)
//   Auto-save: setStackInSlot 自动调 saveToNBT (MaidAssemblyInventory 模式)
```

### 可复用基础设施

| 功能 | 来源 | 用法 |
|------|------|------|
| NBT 持久化 | MaidAssemblyInventory | `setStackInSlot` 自动 save |
| 冷却节流 | Pipeline TryCd/AdvCd | `d.getInt("Cd") > 0` |
| 多阶段 | advanceSlot→strike→reset | 状态机 S 枚举 + tick switch |
| 搜索方块 | NearbyContainerService | `scanItems(level, pos, radius, filter, blocks)` |
| 消耗物品 | Pipeline.consumeFromBackpack | `backpack → wireless → nearby` |
| 默认行为 | enableWorkEat/collectFilter/enableWireless | 自动吃/收集 |
| 开关 | TaskToggle | `isEnabled(taskType)` + NBT per-maid |

## 被动 vs 环境感知

| 场景 | 用 |
|------|-----|
| 检测温度/天气/时间/结构变化 | EnvSense (已有, 注册感知器) |
| 检测后触发一次性动作 | EnvSense callback |
| 累计击杀数/修炼值/相处时间 | 被动任务 Pipeline |
| 多阶段任务链 | 被动任务 Pipeline |
| 空闲行为 | 被动任务 Pipeline |

**EnvSense 是触发器，Pipeline 是执行器。** EnvSense 的 callback 只适合简单的一次性动作。只要需要"记住状态"（累计、进度、冷却），就必须走 Pipeline。

## 任务链模式

```java
// 被动任务 A 完成后, 自动提交被动任务 B:
if (isComplete(d)) {
    TaskDispatcher.submitPassive(maid, "quest_step_2");
}
// 或触发主动任务:
TaskDispatcher.submit(maid, "craft_chain", "minecraft:emerald", 1);
```

## 注册与开关

```java
// 注册 (showInBar=false)
TaskRegistry.register("my_task", pl, pl.executor(), false);

// 触发
TaskDispatcher.submitPassive(maid, "my_task");

// 全局开关 (config/littlemaidmoreaction/task_toggles.json)
{"disabled": ["my_task"]}

// 单女仆开关 (NBT)
maid.getPersistentData().putBoolean("lma_task_enabled_my_task", true);
```
