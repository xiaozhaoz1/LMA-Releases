# vanilla/input — io 读原语层

**作用**: 与世界的单次查询/读取 — 背包/女仆状态/方块/配方/扫描/节流。通用、无业务语义。

**判据 (两轴表)**: 无状态 + 无业务语义 + 读世界 → 这里; 带业务判定 → task/service。

**依赖方向**: 只依赖 MC + TLM 实体; 被 service/execute/pipeline 依赖。

**代表**: ItemStackHelper (纯比较) / ToolStateReader / MaidStateReader / BlockScanner 族 / ScanScheduler / HandSwap (通用复合原语) / ThrottleUtil

**修改注意**:
1. 纯逻辑抽纯类 (ThrottleMath/RingSpiral/ScanBudget) — 纯 JVM 可测
2. 门面变胖是漂移信号 (MaidStateReader 393 行 = 领域门面, 类头要标清)
3. 读操作不应有副作用; io 判据是通用性不是粒度 (复合原语也算)
