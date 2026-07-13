# LittleMaidMoreAction — 车万女仆「更多动作」

为 Touhou Little Maid 添加可视化规则编辑器 + I/O 三层架构驱动的女仆战斗动作系统。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)](https://minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-47.4.18-blue)](https://files.minecraftforge.net/)
[![Java](https://img.shields.io/badge/Java-17-red)](https://adoptium.net/)

## 功能

- **可视化规则编辑器** — Cloth Config GUI, 无需手写 JSON
- **139 条件 + 110 动作** — 覆盖战斗/移动/世界/特效/物品/女仆控制全领域
- **36 触发事件** — TLM + Forge 事件全覆盖
- **条件委托率 75%** — 98/139 条件通过 I/O Reader 查询，纯逻辑可测试
- **动作委托率 74%** — 55/110 动作通过 I/O Output 执行
- **7 Execute 编排类** — Craft/Furnace/Jukebox/Bell/Smelt/Container/PlaceBlock/Altar/Anim
- **Compat 模块** — YSM (Yes Steve Model) + SlashBlade (拔刀剑) + TPM
- **动画元数据** — INSTANT/FULL 双模式, 优先级/锁移动/冻结AI 配置
- **热重载** — F3+T 识别新增动画/音效

## 技术架构 — I/O 三层

```
input/   纯查询 (120 Maid + 25 Target + 19 World 方法)
output/  纯命令 (53 Maid + 24 Combat + 24 World + 16 Movement + 15 Item + ...)
execute/ 编排层 (7 类 — 双入口: RuleEngine + Brain Behavior)
api/     SPI + ItemResolver + ParamExtractor
```

**抽象层**: BaseStateMachine | BrainHelper | ItemResolver  
**Compat**: SlashBladeWriter | YsmWriter | TpmWriter  
**完整 API**: [doc/IO-API-REFERENCE.md](doc/IO-API-REFERENCE.md)

## 版本历史

| 版本 | 日期 | 关键特性 |
|------|------|---------|
| **v29** | 2026-07-13 | I/O 完善 346 方法, 55/110 动作委托, 7 Execute, TLM API 编目 |
| v28 | 2026-07-13 | I/O 原语架构 Phase 4-6, 唱片机四连修复 |
| v27 | 2026-07-13 | 消反射: BuiltinRegistrar 编译期安全 |
| v26 | 2026-07-13 | ParamExtractor + CombatOutput 充实 |
| v25 | 2026-07-13 | 83/120 条件→Reader 委托 |
| v16 | 2026-07-10 | 任务系统重置 (17→6 工具, 单轨, 5 Pipeline) |
| v12.7 | 2026-07-08 | Brain Memory 替代 PersistentData |
| v9 | 2026-06-30 | 文件系统规则, CompatScanner |
| v8.2 | 2026-06-25 | 100 条件 + 75 动作 |

## 安装

1. 安装 Touhou Little Maid (>=1.5.0) + Cloth Config
2. 将 jar 放入 mods/ 目录
3. 启动游戏

## 使用

**规则编辑器**: Forge 模组列表 → LittleMaidMoreAction → 打开规则编辑器

**自定义动画**: `.animation.json` → `config/littlemaidmoreaction/animations/` → F3+T

**独立规则**: 手持木棍右键女仆 → 打开独立规则界面

## 贡献者

- **xiaozhaoz1** — 作者
- **DeepSeek AI** — AI 协作开发

## 许可

MIT License — 详见 [LICENSE](LICENSE)
