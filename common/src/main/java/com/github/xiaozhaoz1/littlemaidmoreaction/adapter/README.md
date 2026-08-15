# adapter — TLM 桥接层

**作用**: LMA 任务系统 ↔ TLM 的翻译官——任务注册进 TLM TaskManager、TLM 任务切换翻译成 LMA 提交、气泡/进度显示到 TLM 界面。

**判据 (什么进这里)**: 一切「TLM API → LMA 概念」的适配 (IMaidTask 实现/事件监听/显示桥)。纯 LMA 逻辑不在这里。

**依赖方向**: task/* + api + chatbubble; 被 init/平台入口驱动。

**代表**: LmaTaskTypeRegistry (任务栏注册+迟注册钩子) / LmaTypedFlowTask (每任务 IMaidTask) / TlmTaskMonitor (TLM_SWITCH 旗标) / TlmEventAdapter / LmaTaskProgressDisplay (气泡显示桥)

**修改注意**:
1. TLM TaskManager.init 末尾冻结 (ImmutableMap) — 迟注册钩子必须 fail-soft
2. uid 净化规则 (TaskTypeUid) 是任务栏唯一真相处 — 命名必须小写下划线
3. 本层跨度偏大 (注册/监听/显示/魔法咏唱) — 新增桥接先问归属: 显示类是否该去 chatbubble/screen
