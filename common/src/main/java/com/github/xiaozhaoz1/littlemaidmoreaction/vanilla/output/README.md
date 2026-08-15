# vanilla/output — io 写原语层

**作用**: 与世界的单次写入/交换 — 容器存取/掉落/伤害/状态写。通用、无业务语义。

**判据 (两轴表)**: 无状态 + 无业务语义 + 写世界 → 这里; 多步业务动作 → task/service (FurnaceOutput 已迁走先例)。

**依赖方向**: 只依赖 MC; 被 service/execute/pipeline 依赖。

**代表**: ContainerOutput (六方向能力统一入口) / ItemSpawner (落地) / CombatOutput (伤害) / MaidStateWriter

**修改注意**:
1. 业务类别再住进来 — 层名与内容漂移是已犯过的病 (FurnaceOutput/JukeboxOutput 已迁 task/service)
2. 物品写入返回值不丢 — 丢物品族 (错题 #162): 放回/背包/落地三链
3. 纯逻辑抽纯类 (RepairCost 先例)
