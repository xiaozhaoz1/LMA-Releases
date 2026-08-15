# compat — 兼容模块层

**作用**: 按第三方 mod 分包的兼容代码——Create 任务系 (crank/press/mix/power/running_belt/assembly)、AI 工具 (Numen 门控)、YSM 动画、平台专属 (CBC forge / Numen neoforge)。

**判据 (什么进这里)**: import 第三方 mod 的代码必须住这里 (vanilla 层禁止依赖 Create/YS 等); 按 mod 分包子包。

**依赖方向**: task/* + vanilla/* + 第三方 mod; 被 TaskRegistry 门控注册 (CompatToggle + ModList)。

**代表**: create/task/* (Press/Mix/Crank/Power/RunningBelt + assembly) / ai/tool/* (GatedMaidTool 8 世界工具) / ysm/* (动画注入/输出)

**修改注意**:
1. 门控双条件 (CompatToggle 开关 + ModList 已装) 镜像 CompatRegistry.MODULES — 别只查一个
2. Create 域合法折叠: FSM 状态在管线 (MoveToBlockStateMachine), 单拍动作在 service — 不需要 compat/execute 层 (YAGNI)
3. 条件化块必须双分支对称 (错题 #175)

**子包索引** (无独立 README, 父层总览):
- `ai/tool/` — GatedMaidTool 8 世界工具 (AI 操控门控); `ai/` — AiToolRegistration
- `create/task/` — Press/Mix/Crank/Power/RunningBelt 管线 + service; `create/task/assembly/` — MaidAssembly 管线/库存/服务
- `create/block|render|client` — MaidPowerBelt 方块/渲染/客户端
- `ysm/` — YsmOutput/YsmReloadListener/YsmAnimInjector 动画注入链
- 平台节点目录: `forge\src\main\java\...\compat\createbigcannons\` — CannonLoad (forge 仅); `neoforge\src\main\java\...\compat\numen\` — NumenMaidBridge (neoforge 仅)
