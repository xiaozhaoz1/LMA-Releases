# task/pipeline/sense — 被动管线层

**作用**: 事件/信号触发的任务 (不挂 TLM 任务栏), 与主动任务平行运行。触发面 + 轻量计时状态机。

**判据 (什么进这里)**:
- 被动任务 (showInBar=false): onSignal 触发 / 周期扫描触发 (HaqiTrigger) + 计时 tick
- 执行细节 → task/service (HaqiService 先例: 挥击/音效出管线)

**依赖方向**: task/api + task/data + task/service; 被 TaskRegistry 注册 (PASSIVE 规格表)。

**代表**: HaqiPipeline (双通道触发+LOOK 状态机) / StructureSensePipeline / TorchLightPipeline / SelfRescuePipeline

**修改注意**:
1. 被动键 lma_passive_<type> 与主动 lma_flow_task 是两套平行世界 — submitPassive/cancelPassive 键闭环
2. 哈气互斥 (哈气运行时其他被动停 tick) 是全局规则 — 改互斥先读 GMPM.tickPassiveFor
3. 开关: TaskToggle.isEnabled 未知类型默认开 (黑名单语义)
