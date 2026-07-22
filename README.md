# LittleMaidMoreAction — 车万女仆「更多动作」

为 Touhou Little Maid 添加可视化规则编辑器与事件驱动的女仆战斗动作系统。

## 功能

- **可视化规则编辑器** — Cloth Config GUI, 无需手写 JSON
- **SelectionScreen v2** — 搜索框 + 分类下拉，107 条件/75 动作中 3 字符定位
- **9 条预设** — 处决/闪避/弹反/肘击..., 开箱即用
- **33 触发事件** — TLM + Forge 事件全覆盖
- **83 动作类型** — 战斗(19)/移动(10)/特效(5)/物品(4)/女仆控制(20)/世界(7)/流程控制(8)/消息(2)/脚本(1)（含 YSM 兼容 4 个，set_ysm_model 支持随机模型）
- **111 条件键** — MAID(67)/TARGET(21)/OWNER(5)/WORLD(16) 全覆盖，支持参数化条件（含 YSM 兼容 6 个）
- **条件参数支持** — 条件可携带参数(如 effect_id)，Pipeline 贯通 ConditionDef→CondRow→GUI→evaluate
- **20tick 时间窗口** — 瞬时条件(is_mainhand_attack 等)触发后 1 秒内保持有效
- **CompatScanner** — 通用三扩展扫描器（条件/动作/事件），新 compat 模块 ~15 行
- **注解驱动** — @RuleCondition/@RuleAction 自动注册, 外部模组 SPI 扩展
- **脚本支持** — JavaScript/Lua 脚本动作 (JSR-223)
- **可选 MVEL 表达式** — @{...} 语法支持复杂条件
- **动画元数据** — 每动画独立配置 (优先级/锁移动/冻结AI/可打断)，INSTANT/FULL 双模式
- **热重载** — F3+T 识别新增动画/音效

## 版本

**v53** — 2026-07-22 — 执行引擎收尾 + 重启自动恢复

| 指标 | 值 |
|------|-----|
| 任务类型 | 13 (7 Vanilla + 6 Create) |
| 条件/动作/事件 | 111 / 83 / 36 |
| 源文件 | 582 |
| 测试 | 144 |
| 兼容模块 | 6 (Create · SlashBlade · TPM · YSM · AI · FlowTask) |
| 架构层 | 6 (调度→状态→注册→引擎→执行→IO) |
| 版本演进 | v1→v53, 八轮重构 (v45-v53) |

## 开发

v53 架构 — 六层: 调度/状态/注册/引擎/执行/IO。规则引擎六边形架构, 零 Minecraft 依赖 core 模块。

| 层 | 包 | 说明 |
|----|-----|------|
| 调度 | `task/TaskDispatcher.java` | 所有任务唯一入口/出口 — submit/cancel/complete/fail/timeout |
| 状态 | `task/TaskStateManager.java` + `TaskEngine.java` | NBT 25键读写 + heartbeat 防超时 + adapter 解耦轮询 |
| 注册 | `task/TaskRegistry.java` | `register(name, pipeline, executor, showInBar)` — 统一入口 |
| 引擎 | `task/TaskStateMachine.java` | 泛型 FSM — 显式枚举状态·转换图验证·onEnter/onExit |
| 执行 | `adapter/` + `compat/` | Brain 驱动 (Vanilla) + Tick 驱动 (Create) · 双路径 |
| IO | `task/TaskKeys.java` + `LmaTaskDataHelper.java` | 26 NBT 常量 · 19读取器+16写入器 |
| core | `core/spi/` | 类型安全参数系统 (sealed TypedParam) |
| core | `core/registry/` | 注解扫描 + ClassGraph 自动注册 + BuiltinRegistrar 三层回退 |
| core | `core/engine/` | 异步管道 + 主线程安全执行 |
| core | `core/model/` | 不可变数据模型 + 树形条件 + 参数化条件 |

新增条件: 实现 `ICondition` + `@RuleCondition` → 自动注册
新增动作: 实现 `IAction` + `@RuleAction` → 自动注册

### Compat 模块 — 6 模组兼容层

| 模块 | 路径 | 功能 |
|------|------|------|
| Create | `compat/create/` | 6 机械动力任务 — crank/power/press/mix/running_belt/arm_transfer |
| SlashBlade | `compat/slashblade/` | 拔刀剑 Sa 槽 + 连段攻击 |
| TPM | `compat/tpm/` | True Power of Maid 连段修改 |
| YSM | `compat/ysm/` | Yes Steve Model — 条件/动作自动注册 |
| AI | `compat/ai/` | 多个 AI 模型集成 |
| FlowTask | `compat/flowtask/` | 通用流程任务基类

参考 TLM compat 架构。每个模块 `compat/<modid>/impl/condition/` + `action/`。门控加载 `CompatRegistry.checkModLoad()`。

**新增兼容模组**：创建 `compat/<modid>/` 文件夹 → 实现条件/动作 → 在 `CompatRegistry` 添加一行 `checkModLoad`。

## 安装

1. 安装 Touhou Little Maid (>=1.5.0) + Cloth Config
2. 将 jar 放入 mods/ 目录
3. 启动游戏

## 使用

**规则编辑器**: Forge 模组列表 → LittleMaidMoreAction → 打开规则编辑器

**自定义动画**: 将 `.animation.json` 放入 `config/littlemaidmoreaction/animations/` → F3+T

**内置音效**: JAR内置 3 个音效(man/manbaout/whatcanisay)，自定义音效通过资源包添加

**动画参数编辑**: 规则编辑器 → 动作编辑 → 选 play_anim → [编辑] 按钮 → 配置每动画参数
参数保存在 `config/littlemaidmoreaction/animationsetup/<name>.json`

**条件参数编辑**: 规则编辑器 → 条件编辑 → 选择带参数条件(如女仆效果) → 出现参数输入框

**独立规则编辑** ★ v8.7: 手持木棍右键女仆 → 打开该女仆独立规则界面 → 规则仅对该女仆生效
独立规则保存在 `config/littlemaidmoreaction/maid_rules/<uuid>.json`

## 贡献者 / Contributors

- **xiaozhaoz1** — 作者、设计、开发
- **DeepSeek AI** (deepseek-v4-pro) — AI 协作开发: 代码生成、架构设计、文档撰写、Bug 诊断修复、测试编写。累计贡献 582 源文件中大量代码，111 条件 + 83 动作实现，v5 六边形架构设计，v7 模块化重构

## 许可

MIT
