# task/service — 业务算法层 (决策 + 单拍编排)

**作用**: 「算」和「一件完整的事」——配方扫描/名单过滤/工具判断 (决策型) + 收产物/冲压/合成 (单拍编排型)。无跨 tick 状态。

**判据 (两轴表)**:
- 无跨 tick 状态 + 有业务语义 → 这里
- 跨 tick → vanilla/execute; 通用无业务语义 → vanilla/io; 任务流程 → task/pipeline

**依赖方向**: vanilla/io + task/data + MC; 被 pipeline/execute 调用; 不反向依赖 pipeline。

**代表**: FurnaceService (决策+单拍双型) / ToolJudge / ItemFilters / CraftService / PressService / HaqiService / JukeboxService / ArmTransferService / MaterialChecker+MaterialReport (材料充足性检查)

**修改注意**:
1. 纯逻辑抽纯类进纯 JVM 测试 (ItemFilters/ToolJudge 先例)
2. 单拍会碰世界 (PressService.executeDepotPress) — 碰不碰世界不是判据
3. 单任务专属放这里; ≥2 域复用的通用工具抽 vanilla/io (HandSwap 判据先例)
