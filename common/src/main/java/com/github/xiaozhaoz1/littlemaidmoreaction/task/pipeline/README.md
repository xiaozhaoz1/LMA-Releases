# task/pipeline — 流程判定层 (主动管线)

**作用**: 每个任务一个管线, 只写「什么状态下干什么」的判定。三种形态: 直连 tick / FSM 状态机 / 工作站节拍。
**v79.61x S1**: 相位机统一进 TaskStateMachine — furnace/jukebox 已从 WorkStationPipeline 迁移为 FSM (workStationGated 复用 WorkStationPipeline.gate 门); WorkStationPipeline 现仅剩 craft_chain/bell_ring 两个工作站子类。

**判据 (什么进这里)**:
- 任务流程: 状态枚举 / transitions / 节拍 / 守卫链
- 业务算法 → task/service; 跨 tick 世界操作 → vanilla/execute; 通用交换 → vanilla/io

**依赖方向**: task/api + task/data + task/service + vanilla/*; 管线之间互不依赖。
**已裁定反向例外 (2026-08-15)**: 主动侧 WorkStationPipeline→TaskDispatcher.complete / ArmTransferPipeline→fail (容器消失终态), 被动侧 submitPassive/cancelPassive 自闭环 (v79.58 用户裁定) — 仅限这个 Dispatcher 面, 不扩散其他 runtime 面。

**代表**: FurnacePipeline (FSM 相位机 — workStationGated 复用工作站门) / JukeboxPipeline (FSM 相位机) / ArmTransferPipeline (FSM 四态) / ChainHarvestPipeline (Mode 参数化变体) / PressPipeline (FSM 薄壳 95 行)

**修改注意**:
1. 四段式长相: 身份 (taskType/steps/isLongRunning) → 行为 (tick) → 配置 (可选) → 私有业务
2. FSM 的 tick 用 switch 分派 — 短状态内联, 长状态拆顶层方法 (无处理器表双重间接)
3. 状态键保持原样 (行为零变化 > 形状洁癖)
4. 加任务 = 抄最像的模板 + 规格表加一行
