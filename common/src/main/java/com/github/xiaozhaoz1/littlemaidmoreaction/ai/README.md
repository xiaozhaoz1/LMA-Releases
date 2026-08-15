# ai — AI 上下文层

**作用**: 给 LLM (Numen) 构建世界上下文——任务状态/附近方块/环境感知, 供 AI 决策。

**判据 (什么进这里)**: 上下文构建与扫描 (喂给 LLM 的数据组装)。AI 工具本身在 compat/ai/tool。

**依赖方向**: task/data + task/sense + vanilla/input; 被 compat/numen 消费。

**代表**: MaidTaskContext / LmaEnvSenseContext / LmaBlocksContext / NearbyBlockScanner / OwnerFoodTracker

**修改注意**: 上下文是 LLM 提示词的原料 — 改字段顺序/命名会影响 AI 理解质量; ai/model 子包为空壳 (已删)。
