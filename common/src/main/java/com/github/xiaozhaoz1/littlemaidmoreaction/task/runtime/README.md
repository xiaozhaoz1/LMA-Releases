# task/runtime — 调度层

**作用**: 任务怎么跑起来——提交 (Dispatcher) / 每 tick 驱动 (GMPM 单一驱动源) / FSM 基类 / 状态写入 / 卸载清理。

**判据 (什么进这里)**:
- 生命周期 (submit/cancel/complete/fail/timeout) + 驱动 + 调度横切 (心跳/看门狗/预算轮转)
- 不写具体任务业务

**依赖方向**: task/api + task/data; 被 task/pipeline 依赖。

**代表**: TaskDispatcher / GameTickPipelineManager / TaskStateMachine / TaskStateManager / MaidUnloadRegistry / WatchdogMath (纯) / PassiveRotation (纯)

**修改注意**:
1. 单一驱动是设计 — 新横切关注点挤进 GMPM tick 链 (别散开另开循环)
2. 心跳/看门狗仅 isLongRunning 生效 — 非长任务自终结无兜底 (设计约定)
3. 纯函数 (WatchdogMath/PassiveRotation) 保持零 MC — 纯 JVM 可测
4. 静态缓存必须登记 MaidUnloadRegistry (实体卸载清理)
