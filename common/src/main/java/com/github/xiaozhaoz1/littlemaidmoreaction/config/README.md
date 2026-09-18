# config — 全局配置层 (最底层, 被所有层读)

**作用**: 模组全局配置 (TOML, 双平台) + 兼容模块开关。**只被读, 不依赖任何上层**。

## 一、组成 (4 类)
| 类 | 行数 | 覆盖内容 | 引用面 |
|---|---|---|---|
`MoreActionConfig` | 150 | **主配置** + `SPEC` (平台注册入口) + `reg(...)` 辅助 | 66 文件 |
`ActiveTaskConfig` | 420 | **主动任务全局默认** (80 个 `IntValue/BooleanValue/...`): 钟间隔/音量音调、斧白名单、挖空尺寸、防御塔… | 122 文件 (最广) |
`PassiveTaskConfig` | 411 | **被动任务全局默认** (78 项): 哈气/点火把/自救/挤奶等开关与参数 | 76 文件 |
`DefenseTowerConfig` | 68 | 防御塔专属 (伤害/半径/间隔等) | 18 文件 |

## 二、连接链 (配置怎么生效)
```
平台入口 (LmaForgeEntry / LmaNeoForgeEntry) 构造器
  → modContainer.registerConfig(COMMON, MoreActionConfig.SPEC)  (+ 平台分支 ForgeConfigSpec / ModConfigSpec)
  → 各配置类 static 字段 = spec 的 Value (懒读)
  → 运行时: ActiveTaskConfig.XXX.get() / .getDefault()
```
**单女仆覆盖**: 任务侧 per-maid 值存 `MaidData.cfgOrCreate(maid, taskType)` (NBT `lma_cfg_<taskType>`), **空则回落全局** (参 `task/gui/README.md` §二)。防具: `setSaveConsumer` 只在 GUI 编辑时用。

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **键名是契约** | TOML 键名改了 = 老存档配置静默丢失/复位。改键名必须迁移或保留旧键读取。|
**B** | **双平台 spec 类型不同** | `ForgeConfigSpec` vs `ModConfigSpec` — 新配置项必须**双分支**定义 (Stonecutter `//? if`), 否则一端编译不过或运行时缺项。|
**C** | **"改了不生效"先分清全局 vs 单女仆** | 用户可能改的是**全局**但该女仆已有**单女仆覆盖值** (或反之)。排查时先确认生效层级 (参 §二 回落规则)。|
**D** | **不要在 config 里做业务** | 这里只放"可调值"; 判定/默认值语义放 service/pipeline。|
