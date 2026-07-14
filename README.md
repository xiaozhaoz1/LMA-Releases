# LittleMaidMoreAction — 车万女仆「更多动作」

为 Touhou Little Maid 添加可视化规则编辑器与事件驱动的自动战斗动作系统。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)](https://minecraft.net) [![Forge](https://img.shields.io/badge/Forge-47.4.13-blue)](https://files.minecraftforge.net/) [![Java](https://img.shields.io/badge/Java-17-red)](https://adoptium.net/) [![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 这是什么

LittleMaidMoreAction 是 Touhou Little Maid（车万女仆/TLM）的附属模组。玩家通过可视化规则编辑器配置"**当某事件发生时 → 满足某条件 → 执行某动作**"的自动化行为。无需编写任何代码或 JSON。

**典型场景**：
- 女仆低血量时自动治疗 → `maid_health < 20%` → `Heal`
- 主人受到攻击时女仆反击 → `owner hurt` → `ForceTarget + Dash + ExecutionKill`
- 下雪时换冬装 → `is snowing` → `SetYsmModel(winter)`

## 游戏版本

| 依赖 | 版本 |
|------|------|
| Minecraft | **1.20.1** |
| Forge | **47.4.13** (兼容 47.4.x) |
| Java | **17** |
| Touhou Little Maid | **1.5.0+** |
| Cloth Config | **11.1.136** |

> ⚠️ 不兼容 1.20.2+, Fabric, NeoForge。

## 当前版本 — v34

| 指标 | 数值 |
|------|------|
| 条件 (Conditions) | **139** (75% I/O 委托) |
| 动作 (Actions) | **110** (74% I/O 委托) |
| 事件 (Events) | **36** |
| 源文件 | **~480** |
| I/O 方法 | **375** (20 文件, 全覆盖 TLM API) |
| Execute 类 | **9** (扁平化) |
| 测试 | **153** (JUnit 5) |
| 女仆编辑器 | **v34** GUI 80字段 5×4网格 |

## 功能详情

### 可视化规则编辑器

Cloth Config GUI — 搜索框 + 分类下拉，3 字符定位任意条件/动作。支持复制/粘贴/导入/导出规则。

### 条件系统 (139 条件, 98 委托给 I/O Reader)

| 类别 | 数量 | 示例 |
|------|------|------|
| MAID 女仆自身 | 85 | `maid_health`, `maid_distance`, `maid_is_begging`, `maid_schedule` |
| TARGET 目标 | 22 | `target_health`, `target_is_monster`, `target_type` |
| OWNER 主人 | 8 | `owner_distance`, `owner_health`, `owner_holding_item` |
| WORLD 世界 | 18 | `world_time`, `world_is_thundering`, `world_biome` |
| YSM 模型 | 6 | `is_ysm_model`, `ysm_model_id`, `ysm_roaming_var` |

### 动作系统 (110 动作, 55 委托给 I/O Output)

| 类别 | 数量 | 常用动作 |
|------|------|---------|
| COMBAT 战斗 | 25 | `deal_damage`, `execution_kill`, `bleed`, `launch`, `damage_nearby`, `life_steal`, `launch_projectile` |
| MOVEMENT 移动 | 12 | `teleport`, `dash`, `leap`, `freeze_ai`, `swap_position`, `guard_pos` |
| MAID 控制 | 22 | `set_maid_task`, `set_model`, `set_home`, `set_bauble`, `force_target` |
| WORLD 世界 | 17 | `set_weather`, `set_time`, `explosion`, `summon_lightning`, `place_block`, `trade_villager` |
| VISUAL 视觉 | 6 | `play_anim`, `play_sound`, `spawn_particle`, `spawn_heart_particle` |
| ITEM 物品 | 5 | `give_item`, `repair_item`, `clear_inventory`, `drop_item`, `extract_maid_xp` |
| CONTROL 流程 | 9 | `wait`, `repeat`, `send_message`, `open_maid_editor` |
| MESSAGE 消息 | 2 | `send_chat`, `send_bubble` |
| EFFECT 效果 | 2 | `apply_effect`, `clear_effects` |
| COMPAT 兼容 | 10 | `set_ysm_model`, `slashblade_sa` 等 |

### 动画系统

- **INSTANT** 模式 — 单动画播放，常用于处决/闪避
- **FULL** 模式 — 三阶段施法动画（Start→Casting→End），用于大招
- **YSM 模型** — 支持 Yes Steve Model 轮盘动画
- **动画参数** — 每动画配置：优先级/锁移动/冻结AI/可打断
- **热重载** — F3+T 识别新增动画

### I/O 三层架构

```
input/   纯查询  — MaidStateReader(148) + TargetStateReader(25) + WorldStateReader(19)
output/  纯命令  — CombatOutput(24) + WorldOutput(24) + MaidStateWriter(59) + MovementOutput(16) +
                    VisualOutput(12) + ItemOutput(15) + EffectOutput(3) + EntityOutput(4)
execute/ 编排层 — CraftExecute, FurnaceExecute, JukeboxExecute, BellExecute,
                   ContainerExecute, AltarExecute, AnimExecute, PlaceBlockExecute, AutoCropHandler
api/     工具+注册 — SlotLayout, ItemMover, TaskHandlerRegistry, TaskResult, VanillaConstants, ParamExtractor
adapter/ TLM桥接 — TlmEventAdapter, LmaFlowCoordinationBehavior, LmaTaskTypeRegistry 等 11 文件
```

完整 API 参考：`doc/IO-API-REFERENCE.md`

### 模组兼容

| 模块 | 内容 | 条件 | 动作 |
|------|------|------|------|
| **YSM** (Yes Steve Model) | 模型切换/轮盘动画/漫游变量 | 6 | 6 |
| **SlashBlade** (拔刀剑) | 拔刀剑连段/SA/ProudSoul | 17 | 6 |

## 安装

1. 安装 [Minecraft Forge 47.4.13+](https://files.minecraftforge.net/) for 1.20.1
2. 安装 [Touhou Little Maid 1.5.0+](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid)
3. 安装 [Cloth Config 11.1.136](https://www.curseforge.com/minecraft/mc-mods/cloth-config)
4. 下载 `littlemaidmoreaction-x.x.x.jar` → 放入 `.minecraft/mods/`
5. 启动游戏

## 使用

### 打开规则编辑器
Forge 模组列表 → LittleMaidMoreAction → 打开规则编辑器

### 创建一条规则
1. 选事件（如 `maid_hurt_target_pre` — 女仆攻击前）
2. 加条件（如 `maid_health < 30%` + `target_is_monster`）
3. 加动作（如 `play_anim(anim=execution)` + `deal_damage(damage=20)`）
4. 保存 → 立即生效

### 自定义动画
将 `.animation.json` 放入 `config/littlemaidmoreaction/animations/` → F3+T 热重载

### 独立规则 (v8.7)
手持木棍右键女仆 → 打开该女仆独立规则界面 → 规则仅对该女仆生效
保存路径: `config/littlemaidmoreaction/maid_rules/<uuid>.json`

## 版本历史

| 版本 | 日期 | 内容 |
|------|------|------|
| **v34** | 2026-07-14 | I/O 全覆盖 375方法, 女仆编辑器 GUI 80字段 |
| **v33** | 2026-07-14 | compat/vanilla 整理, execute 扁平化 |
| **v32** | 2026-07-14 | adapter→compat/vanilla, 去重 MaidAPI |
| **v31** | 2026-07-14 | 统一任务入口, 6 action→PersistentData |
| **v30** | 2026-07-14 | SlotLayout+ItemsUtil, FurnaceSlotMapping 删除 |
| **v29** | 2026-07-13 | Execute优化, ItemMover, TaskHandlerRegistry |
| v28 | 2026-07-13 | I/O原语 Phase 4-6, 唱片机四连修复 |
| v27 | 2026-07-13 | 消反射 — BuiltinRegistrar 编译期安全 |
| v26 | 2026-07-13 | ParamExtractor + CombatOutput 充实 |
| v25 | 2026-07-13 | 83/120 条件→Reader 委托 |
| v17-v24 | 2026-07-12 | I/O 三层架构 (input/output/execute) |
| v16 | 2026-07-10 | 任务系统重置 (17→6工具, 单轨, 5 Pipeline) |
| v12.7 | 2026-07-08 | Brain Memory 替代 PersistentData 导航 |
| v9 | 2026-06-30 | 文件系统规则, SelectionScreen v2, CompatScanner |
| v8.2 | 2026-06-25 | 100条件+75动作, 全屏选择器 |
| v7 | 2026-06-24 | 6阶段模块化重构, 动画系统 Magic-ISS |

[完整更新日志](CHANGELOG.md)

## 贡献者

- **xiaozhaoz1** — 作者、设计、开发
- **DeepSeek AI** — AI 协作开发

## 许可

MIT License — 详见 [LICENSE](LICENSE)
