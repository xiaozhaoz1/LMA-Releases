# event — 事件处理层

**作用**: Forge/NeoForge 事件订阅——设置交互 (绑定取货点/交互方块)、伤害监听、收获事件。

**判据 (什么进这里)**: 跨域事件订阅 (无法归入某个任务域的事件入口)。

**依赖方向**: task/* + vanilla/*; 被平台入口注册。

**代表**: ArmTransferSetupHandler (绑定搬运坐标) / BlockInteractSetupHandler / MaidDamageListener / MaidHarvestCropEvent / StickBindUtil

**修改注意**: 任务域设置交互 (Arm/BlockInteract SetupHandler) 与 task/pipeline 重叠 — 修改要两边同看; event/bridge 子包为空壳 (已删)。
