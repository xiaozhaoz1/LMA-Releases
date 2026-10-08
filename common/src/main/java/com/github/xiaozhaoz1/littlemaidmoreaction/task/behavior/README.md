# task/behavior — 常驻习惯层

**作用**: 女仆跨任务一直保持的行为 (饿了吃 / 顺手捡 / 慢慢修) — 挂 TLM Brain, 不经任何管线, 与任务状态无关。

**判据 (什么进这里)**:
- 与具体任务无关的常驻习惯; brain 每 tick 评估调度

**依赖方向**: TLM brain API + task/service; 管线通过 TaskConfigurable 开关声明 (enableWorkEat/collectFilter), 不直接 new。

**代表**: WorkEatBehavior / NearbyCollectBehavior / AutoRepairBehavior / DefaultBehaviorBrain (唯一注册面)

**修改注意**:
1. 注册面只有一个: DefaultBehaviorBrain (IExtraMaidBrain) — 别散开
2. MC brain 类纯 JVM 测不了 → gametest 覆盖
3. 行为与管线的边界: 管「女仆一直保持的习惯」, 不管「当前任务干什么」
