# vanilla/execute — 世界操作协调层

**作用**: 跨 tick 有状态的世界操作编排 (蓄力/相位/堵护/采集总协调) + 采集/导航域单拍执行器族。

**判据 (两轴表)**:
- 有跨 tick 状态 (相位/队列/状态表) → 协调器 (这里)
- 采集/导航域专属单拍动作 → 执行器 (留此因 api/pathing 依赖面 — 搬 task/service 会 api→task 倒挂)
- 任务流程判定 → task/pipeline; 决策/单拍业务 → task/service; 通用交换 → vanilla/io

**依赖方向**: vanilla/io + task/service + task/data; 被 task/pipeline 调用。

**代表**: ChainHarvestExecute (SCAN/CHARGE 显式相位) / ChainScan (扫描域) / DangerGuardCoordinator (堵护状态机) / SelfRescueCoordinator / BlockUpCoordinator / AnimExecute (通用动画, 历史位置) / **BlockPatternCache (单区块方块缓存 L3)** / **区块级缓存族 (Farm/空置域执行器)**

**修改注意**:
1. 状态进 MaidChainState/NBT 键; 静态 map 登记 MaidUnloadRegistry — 但**只登记有 per-maid 状态者**
2. 状态键保持原样 (行为零变化铁律); 显式状态读写单点 (同生同灭)
3. 纯内核抽纯类 (ChainHarvestMath) — 可测性边界
4. **键类型**: 本层 per-maid 表 (FarmExecute.SAVE_UNTIL / LAST_HARVEST_REGION) 用 `getStringUUID()`;
   新表一律用 `task/data/MaidKey.uuid(maid)` (错题 #272 — 实体 ID 会被复用)
5. **区块/维度级缓存的清理面是 `clearDimension` 而非卸载登记** (v79.63 已删 `BlockPatternCache` 的
   no-op 占位): 维度收口在 `TaskTickHandler.onServerStopping`。区块缓存的另一坑:
   **放块不触发失效 → 测试/验证必须越过 TTL** (错题 #238)。
