# compat/createbigcannons/task — 火炮装填 (cannon_load 任务)

**作用**: CBC (Create Big Cannons) 门控的主动任务 `cannon_load` — 女仆**找炮架 → 开闩 → 清膛 → 装弹 → 关闩** (6 态 FSM)。
**依赖方向**: `task/api` + `task/data` + `task/runtime` + `api/navigation` (`NavigationMemory`) + 根包。
**门控/注册**: **CBC 专属** ⇒ `TaskRegistryManifest.CBC` 的**条件化条目** (**双平台**: 1.20.1 与 1.21.1 各一条 `//? if 1.20.1 { } else { }` 分支, 用**数组初始化器**防尾随逗号 — 错题 #175): `new TaskSpec("cannon_load", CannonLoadPipeline::new)`。
**任务类型**: `cannon_load` (TLM 任务栏条目; 1.21.1 为 2026-08-16 移植)。

## 一、类明细 (2 类)

| 类 | 行数 | 是什么 | 谁在用 |
|---|---|---|---|
| `CannonLoadPipeline.java` | 483 | **FSM 管线** — `enum State { SEARCHING, MOVING, OPENING, CLEARING, LOADING, CLOSING }` + `transitions()` 转换图 + 每相位 tick 逻辑 | GMPM 每 tick (`TaskStateMachine` 引擎) |
| `CannonLoadService.java` | 430 | 领域服务 — 炮架/炮闩/弹药/清膛的 CBC 方块实体读写与动作细节 | Pipeline |

## 二、连接链

```
TLM 任务栏 cannon_load → TaskRegistryManifest.CBC (双平台条件条目, CBC 未装不注册)
   → CannonLoadPipeline (FSM 6 态: SEARCHING→MOVING→OPENING→{CLEARING|LOADING}→CLOSING→…)
        → CannonLoadService (炮架/炮闩/弹药/清膛) → CBC 方块实体
导航: MOVING 相位用 api/navigation (NavigationMemory) — 兜底: NavProgressGuard + 跳过集 (错题 #233)
守护: TaskStateMachine 转换图校验 (非法边/不可达状态另有 #290 全量审查)
```

## 三、已知陷阱

| # | 陷阱 | 规则 |
|---|---|---|
| **A** | **转换图是契约** | `transitions()` 与各 `tickXxx` 的 **yield 面必须逐边对齐** — 图-码矛盾**不报编译错**, 只在运行时 warn 刷屏 ✗ (错题 #233)。`CLEARING→OPENING` **悬空边已删** (v79.61x: 清膛恒回 SEARCHING, worm 一次), 别再加回 |
| **B** | **LOADING 滞留超时 = 600t** | 弹药耗尽无进展 ⇒ **30 秒提示一次** (原 200t 刷屏, 用户实测后放宽 — 见源码 `LOAD_STALL_TIMEOUT`); 改节流要同时看气泡频率, 别把"提示"改回每 tick ✗ |
| **C** | **MOVING 必须有导航兜底** | 炮架被堵/走不到 ⇒ `NavProgressGuard` + 跳过集 (错题 #233 三项之一); 新增相位**不要绕过兜底**直连导航 ✗ |
| **D** | **状态 → 步骤是多对一** | 6 状态 → 4 步骤 (`searching`+`moving` 合"寻找炮架"、`opening`+`clearing` 合…) ⇒ **改相位必须同步 `steps()`**; 机械守护方案 (方案 A: `TaskPipeline.stateToStep()` 默认 `Map.of()` = 不检查) **仍待用户裁定** (错题 #291) |
| **E** | **条件化条目写法** | 双平台同任务: `//? if 1.20.1 { } else { }` 必须**整行独占** + 用 `List.of(new TaskSpec[]{ ... })` 数组初始化器 — 条件块内尾随逗号 = 解析炸 (错题 #175) |
| **F** | **CBC 未装时不得加载** | 注册在 `CompatToggle` + `ModList` 门控内; **禁止非门控类静态引用 CBC 类** (NoClassDefFoundError) ✗ |

> **相关错题**: #175 (stonecutter 条件注释整行铁律) · #233 (Create+CBC 批次: 转换图/导航兜底/无目标气泡/3 项修复) · #290 (enum-FSM 全量审查) · #291 (状态→步骤守护待裁定) · #253 (双平台 hurtAndBreak 签名差异)
> **维护约定**: 改状态机/相位/超时阈值 ⇒ 必须同步本表 + `transitions()` 图 + `steps()` 三处 — README 是跨会话契约, 注释会被重构删掉。
