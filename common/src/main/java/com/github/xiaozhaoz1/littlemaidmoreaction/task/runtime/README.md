# task/runtime — 任务运行态 (驱动 / 调度 / 状态机 / 守卫)

**作用**: 把"任务"跑起来的机械层: **每 tick 驱动** (GMPM)、**提交/完成/失败** (Dispatcher)、**相位机基类**、**看门狗/降级**、**卸载清理**、**进度显示**。

## 一、组成 (11 类)
| 类 | 行数 | 职责 |
|---|---|---|
`GameTickPipelineManager` (GMPM) | 256 | **唯一 tick 驱动**: 遍历活跃任务的管线调 `tick()`; 心跳 20t 续命 + 看门狗 (仅 `isLongRunning=true`); 孤儿状态收容 |
`TaskDispatcher` | 223 | 状态机: `submit`(validate→init→setTask) / `complete` / `fail` / `cancel` / 被动 `submitPassive/cancelPassive` — **唯一任务生命周期入口** |
`TaskStateManager` | 41 | 状态写入收敛: `init`(写 FlowTaskData + 设 TLM 任务) / `heartbeat` / `clearAll` |
`TaskStateMachine` | 275 | **FSM 基类**: `stateClass/initialState/transitions` + switch 分派; `workStationGated` 让 FSM 复用 `WorkStationPipeline.gate` |
`TaskTickHandler` | 120 | per-maid tick 补充 (GUI 启动标记消费等) |
`EngineGuard` | 127 | **引擎隔离**: 管线连续异常 3 次 → `fail(maid, "管线连续异常 (3 次) — 引擎隔离")` |
`RecoveryLadder` | 113 | 降级/恢复阶梯 |
`WatchdogMath` | 34 | 看门狗**纯函数** (单测覆盖) |
`MaidUnloadRegistry` | 83 | **卸载清理注册表**: 实体/区块卸载时回调 (static 状态必须注册, 防泄漏) |
`EntityCleanupListener` | 43 | 实体移除事件 → 触发上面的清理 |
`LmaTaskProgressDisplay` | 113 | 进度显示 (`friendlyName` 覆盖 manifest 全部任务 — `TaskRegistryDriftTest` 守护) |

## 二、连接链
```
提交:  任意入口 (GUI/命令/事件/恢复) → TaskDispatcher.submit
          → TaskRegistry.validate (失败→气泡+拒) → TaskStateManager.init
          → FlowTaskData.setTask + maid.setTask(TLM 任务) + STATE=IN_PROGRESS
驱动:  每 tick → GMPM → 活跃任务 pipeline.tick(...)     (+ TaskTickHandler 补充)
        GMPM 心跳 (20t) → TaskStateManager.heartbeat     (isLongRunning 才生效)
        GMPM 看门狗 → 超时无进展 → fail / EngineGuard 隔离
终结:  complete/fail/cancel → TaskStateManager.clearAll (+ 各服务自己的清理注册)
卸载:  实体/区块卸载 → EntityCleanupListener → MaidUnloadRegistry 回调
```
**纪律**: static per-maid 状态 **必须**在 `MaidUnloadRegistry` 注册清理 (参 `task/data/README.md` 缓存纪律表); 管线连续异常由 EngineGuard 隔离 (避免一个坏任务拖垮引擎)。

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **`isLongRunning` 决定看门狗与心跳** | 返回 false 的任务**没有**超时保护, 必须自终结; 反之长任务不返回 true 会被判"无进展"中断 (工作站族恒 true)。|
**B** | **任务生命周期只能走 Dispatcher** | 直接 `maid.setTask(...)` 或手写 FlowTaskData = 绕过 validate/清理 ⇒ 状态残留 (老"幽灵任务"根源)。|
**C** | **新增 static Map 前先看缓存纪律** | per-maid → UUID 键 + `MaidUnloadRegistry` 注册; 区块 → `clearDimension`; 三条"实体 id 例外"有注释, 不要扩散。|
**D** | **GMPM 是唯一 tick 源** | 管线不要再自己注册 tick (双驱动会导致节拍/相位错乱); `pipeline.tick` 之外的世界推进放 service/io。|
**E** | **FSM 迁移要保持状态键语义** | `TaskStateMachine` 迁移(如 furnace)时状态键保持原样 (行为零变化 > 形状洁癖), 迁移后跑对应 gametest 兜底。|
