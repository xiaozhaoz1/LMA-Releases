# task/passive — 纯触发型被动任务 (零 tick, 无管线)

**作用**: **"脱管线"的被动任务**: 只由**事件/信号触发**、**不占 tick** (`showInBar=false`)。
**与 `task/pipeline/sense` 的区别**: 那边是**走 GMPM 的被动管线** (有 tick/FSM); 这边是**纯触发** (无 pipeline 占位, 由 `PassiveDispatcher` 派发)。
**依赖方向**: task/data + task/sense (信号) + api; 被 `PassiveSenseRegistration`/`TaskRegistry.registerPassive` 注册。

## 一、类明细 (6 类)
| 类 | 行数 | 职责 |
|---|---|---|
`PassiveTask` | 38 | **接口**: 纯触发任务契约 (被触发时做什么) |
`PassiveDispatcher` | 98 | **派发器**: 收到信号/事件 → 找对应 PassiveTask → 执行 (含开关判定) |
`PassiveConfigUtil` | 57 | 被动开关读取 (全局 `PassiveTaskConfig` / per-maid `switchScope`) |
`impl/FestivalPassiveTask` | 107 | **节日**触发 (读 `storage/FestivalTable`) |
`impl/RareBiomePassiveTask` | 85 | **稀有群系**触发 (进群系时播报) |
`impl/StructureSensePassiveTask` | 62 | **结构感知**触发 (读 `task/sense/StructureSense`) |

## 二、连接链
```
注册: PassiveSenseRegistration → TaskRegistry.registerPassive("structure_sense"/"festival"/"rare_biome")
   ↳ TaskRegistryManifest 的**启动期 fail-fast 三连**会校验"纯触发被动注册缺失" ⇒ 漏注册会启动即炸
触发: 信号/事件 → PassiveDispatcher → PassiveTask (读 PassiveConfigUtil 判开关) → 执行 (气泡/播报/效果)
```

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **纯触发 ≠ 走管线** | 这里没有 `tick`/相位机; 需要"持续动作"的被动任务应放 `task/pipeline/sense` (走 GMPM)。|
**B** | **注册必须齐** | `registerPassive` 漏一个 → 启动期 fail-fast 直接炸 (这是**刻意**的: 宁可启动失败也别静默少功能)。|
**C** | **开关语义** | 纯触发型的开关归属 (全局 vs per-maid) 走 `PassiveConfigUtil`/`switchScope` — 与主动任务的 `TaskToggle` 不是一套。|
**D** | **触发要幂等** | 事件可能重复到达 (重连/区块加载) ⇒ 执行前判状态, 不要叠加副作用。|
