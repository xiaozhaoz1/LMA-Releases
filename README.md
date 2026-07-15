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

**v34.2** — 2026-07-15 — 注册模式重构 + MaidEditorScreen apply/save + execute Bug修复

| 指标 | v7.1 | v8.2 | v8.4 | v8.7 | v9.0 |
|------|------|------|------|------|------|
| 条件 | 35 | 100 | 100 | 107 | 107 |
| 动作 | 53 | 75 | 70 | 75 | 75 |
| 事件 | 29 | 29 | 28 | 28 | 28 |
| 源文件 | 169 | 258 | 258 | 278 | 285 |
| 测试 | 85 | 85 | 85 | 85 |

## 开发

v5 工业级规则引擎 — 六边形架构, 零 Minecraft 依赖的 core 模块, 异步执行管道。

| 层 | 包 | 说明 |
|----|-----|------|
| core | `core/spi/` | 类型安全参数系统 (sealed TypedParam) |
| core | `core/registry/` | 注解扫描 + ClassGraph 自动注册 + BuiltinRegistrar 三层回退 |
| core | `core/engine/` | 异步管道 + 主线程安全执行 |
| core | `core/model/` | 不可变数据模型 + 树形条件 + 参数化条件 |
| adapter | `adapter/tlm/` | TLM 事件桥接 (29→RuleEngine) + MagicCasting Provider |
| adapter | `adapter/gui/` | Visitor 模式动态表单 |
| compat | `compat/ysm/` | YSM 兼容 — 门控扫描 + 条件/动作自动注册 |
| network | `network/` | ID 同步 (旧系统) + PersistentData 同步 (专用服务器) |
| storage | `storage/` | JSON 持久化 + 动画元数据 (animationsetup/) |

新增条件: 实现 `ICondition` + `@RuleCondition` → 自动注册
新增动作: 实现 `IAction` + `@RuleAction` → 自动注册

### Compat 模块 — 模组兼容层

参考 TLM 既有 compat 架构，每个兼容模组独立子包，门控加载。

```
compat/
├── CompatRegistry.java            # 调度中心 (InterModEnqueueEvent + checkModLoad)
└── ysm/                           # YSM (Yes Steve Model)
    ├── YsmCompat.java             # 门控 + 限定包扫描 + 自动注册
    ├── YsmEvent.java              # 事件订阅（预留）
    └── impl/
        ├── condition/             # @RuleCondition → ICondition
        │   ├── IsYsmModelCondition.java   # is_ysm_model (BOOL)
        │   └── YsmModelIdCondition.java   # ysm_model_id (STR)
        └── action/                # @RuleAction → IAction
            └── SetYsmModelAction.java     # set_ysm_model
```

**新增兼容模组**：创建 `compat/<modid>/` 文件夹 → 实现条件/动作 → 在 `CompatRegistry` 添加一行 `checkModLoad`。

**条件/动作**：放在 `compat/<modid>/impl/condition/` 和 `compat/<modid>/impl/action/` 下，
使用 `@RuleCondition`/`@RuleAction` 注解，由 `XxxCompat.scanAndRegister()` 自动发现。
仅在目标模组加载时注册到编辑器。

## 安装

1. 安装 Touhou Little Maid (>=1.5.0) + Cloth Config
2. 将 jar 放入 mods/ 目录
3. 启动游戏

## 使用

**规则编辑器**: Forge 模组列表 → LittleMaidMoreAction → 打开规则编辑器

**自定义动画**: 将 `.animation.json` 放入 `config/littlemaidmoreaction/animations/` → F3+T

**自定义音效**: 将 `.ogg` 放入 `config/littlemaidmoreaction/sounds/` → F3+T

**动画参数编辑**: 规则编辑器 → 动作编辑 → 选 play_anim → [编辑] 按钮 → 配置每动画参数
参数保存在 `config/littlemaidmoreaction/animationsetup/<name>.json`

**条件参数编辑**: 规则编辑器 → 条件编辑 → 选择带参数条件(如女仆效果) → 出现参数输入框

**独立规则编辑** ★ v8.7: 手持木棍右键女仆 → 打开该女仆独立规则界面 → 规则仅对该女仆生效
独立规则保存在 `config/littlemaidmoreaction/maid_rules/<uuid>.json`

## 贡献者 / Contributors

- **xiaozhaoz1** — 作者、设计、开发
- **DeepSeek AI** (deepseek-v4-pro) — AI 协作开发: 代码生成、架构设计、文档撰写、Bug 诊断修复、测试编写。累计贡献 258 源文件中大量代码，100 条件 + 75 动作实现，v5 六边形架构设计，v7 模块化重构

## 许可

MIT
