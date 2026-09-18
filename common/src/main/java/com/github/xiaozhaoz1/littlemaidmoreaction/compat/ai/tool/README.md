# compat/ai/tool — TLM AI 工具集 (LLM 可调用 / function calling)

**作用**: 经 TLM 官方扩展点 (`ILittleMaid.registerAITool`) 注册给 LLM 的**工具** — 让女仆 AI 能查状态/读世界/移动/挖掘/交互/攻击/切换 LMA 任务。
**依赖方向**: 只依赖 `api/*` + `task/api` + `task/runtime` + `vanilla/*` (**io 原语**) — 世界操作**委托原语, 不自己重写** (FakePlayer 交互/挖掘 · `CombatOutput` 伤害 · `MaidStateReader` 状态 · `PathingApi` 寻路 · `TaskDispatcher`/`TaskRegistry` 任务)。
**注册入口**: `compat/ai/AiToolRegistration.registerAll(ToolRegister)` (TLM 扩展点调用) — **新增工具 = 该类加一行 `register.register(new XxxTool())`**。

## 一、类明细 (15 类)

| 类 | 行数 | 是什么 | 门控 |
|---|---|---|---|
| `GatedMaidTool.java` | 19 | **门控骨架** — 接口 default 承载 `AiControlGate` 判定 (世界修改类统一入口) | (骨架) |
| `MoveToTool.java` | 63 | 走到指定坐标 (走路全 TLM 导航 — v79.23 弃自研 PathExecutor) | **门控** |
| `MineBlockTool.java` | 56 | 挖方块 (委托 `FakePlayerManager` `LEFT_CLICK_CONTINUOUS`, 破坏后自动停止) | **门控** |
| `InteractBlockTool.java` | 59 | 右键方块 (委托 `FakePlayerInteract.rightClick` — 完整右键管线: 事件后门 + useOn + 掉落入包) | **门控** |
| `InteractEntityTool.java` | 65 | 右键实体 (指定 `entity_id`, 用 `LmaFakePlayer` 执行 `entity.interact`) | **门控** |
| `MeleeAttackTool.java` | 67 | 近战 (设 `ATTACK_TARGET` 记忆让 AI 持续追击 + 立即一击 `CombatOutput.damage`) | **门控** |
| `ScanBlocksTool.java` | 94 | 指定 `block_id` + 半径扫方块 (vanilla 三重循环, 返回位置列表) | **门控** |
| `CollectItemsTool.java` | 56 | 收集掉落物 — **感知版** (返回数量+位置提示引导拾取; 实际拾取由 TLM pickup 能力负责) | **门控** |
| `SwitchLmaTaskTool.java` | 60 | 切换 LMA 任务 (参数枚举 = **`TaskRegistry.taskTypes()` 动态**, 同 SwitchWorkTaskTool 模式) | **门控** |
| `GetSelfStatusTool.java` | 55 | 自身状态 (聚合 `MaidStateReader`: 生命/饥饿/好感/经验/位置/维度/任务/背包) | 只读 |
| `GetWorldInfoTool.java` | 41 | 世界状态 (维度/tick/昼夜/天气 — `WorldStateReader`) | 只读 |
| `LookAroundTool.java` | 51 | **语义网格** (女仆周围字符地形图 `LookAroundGrid`, 每格一个方块 + 移动可行性语义, 供 LLM 空间推理) | 只读 |
| `ReadBlueprintTool.java` | 62 | 读蓝图 (尺寸/用料/分层, **不动世界一格**) | 只读 |
| `ScanNearbyEntitiesTool.java` | 63 | 附近实体清单 (`EntityScan`: id/类型/距离/hp/分类, 按距离排序) | 只读 |
| `WaitTicksTool.java` | 61 | 异步等待后回传 (`onCallAsync` + server 调度器, `LLMCallback.runOnServerThread` 回主线程) | 只读 |

## 二、连接链

```
LLM (TLM AI 环) → ITool.onCall(maid, 参数)
   → [8 个门控工具] GatedMaidTool default → AiControlGate (AI 操控任务开关)
   → 原语: FakePlayer* / CombatOutput / MaidStateReader / WorldStateReader / PathingApi / TaskDispatcher / TaskRegistry

注册: 平台入口 → compat/ai/AiToolRegistration.registerAll(TLM ToolRegister) → 工具进 LLM 可调用清单
上下文: AI 环上下文/扫描不在本包 (见 ai/ 与 task/sense), 本包只放"动作" ✓
```

## 三、已知陷阱

| # | 陷阱 | 规则 |
|---|---|---|
| **A** | **世界修改工具必须继承 `GatedMaidTool`** | 门控统一在接口 default (`AiControlGate`), **不要各自覆写判定** (v79.61 基站重写前的散落判定已收编)。漏继承 = AI 操控关着也能改世界 ✗。判定依据: 会改世界/切任务/动背包 ⇒ 门控; 纯读 ⇒ 不门控 ✓ |
| **B** | **FakePlayer 路径 ≠ 真玩家** | 交互/挖掘走模拟玩家 (事件后门 + `useOn` + 掉落入包), 语义与玩家不完全一致 ⇒ **别用"玩家会怎样"推断**; 工具里优先复用 `vanilla/fakeplayer` 原语, 不新写世界操作 ✗ |
| **C** | **异步工具必须回主线程** | 延迟类 (`WaitTicksTool`) 用 server 调度器 + `LLMCallback.runOnServerThread`; 在异步线程碰世界 = 线程安全崩 ✗ |
| **D** | **任务名/枚举不要硬编码** | `SwitchLmaTaskTool` 的参数枚举取自 `TaskRegistry.taskTypes()` (动态) — 硬编码任务名会在任务增删后失效 ✗ |
| **E** | **只读 vs 写操作要分清** | 只读工具**无门控** (TLM 内置只读工具同款惯例): 状态/世界/网格/蓝图/实体扫描/等待 ✓ |
| **F** | **参数 schema 与实现同步** | 每个工具的 `ObjectParameter`/`IntegerParameter` 声明就是 LLM 看到的契约 — 改实现必须同步改 schema (否则 LLM 传参与实现不一致) ✗ |

> **相关错题**: #161 (根包/ai 目录分片审计) · #174 (纯 JVM 测试边界 — mock MC 类型必炸) · #157 (AiControl 死链) · gametest `lmaAiControlGate` (门控回归)。
> **维护约定**: 新增工具必须在本表加行 (类名 + 门控判定) — 本包 README 是跨会话契约, 注释会被重构删掉。
