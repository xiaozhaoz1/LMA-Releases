# task/data — 数据层

**作用**: 键表单一真相 (TaskKeys/DataKey) + 类型化读写门面 (MaidData) + 开关 (TaskToggle)。所有 lma_* 键只在这里定义。

**判据 (什么进这里)**:
- 键定义 / 读写门面 / 清理集合 / 开关
- 业务逻辑不在这里

**依赖方向**: 只依赖 MC NBT; 被所有层依赖 (最底层)。

**代表**: TaskKeys / DataKey / MaidData (PL 内存态+CFG) / FlowTaskData / TaskMetaData / TaskToggle

**修改注意**:
1. 新增键必须声明清理归属 — DataKeyConsistencyTest 守护
2. set → remove 闭环 (跨 session 残留 = 错题 #67 族); 删代码不删旧键清理 (clearAll 字面量兜底)
3. PL (lma_pl_<task>) 是内存态 — 修改后显式 flush (心跳 20t/离开/终结自动)
