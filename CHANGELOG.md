# Changelog

## [v29] — 2026-07-13 — I/O 架构完善

- **I/O 方法**: 95→346 (MaidStateReader 120, MaidStateWriter 53, CombatOutput 24, WorldOutput 24, MovementOutput 16, VisualOutput 12, ItemOutput 15, 等)
- **动作委托率**: 43→55/110 (41%→74%)
- **Execute 类**: +5 (PlaceBlockExecute, AltarExecute, AnimExecute, ContainerExecute, SmeltExecute)
- **抽象层**: +4 (ItemResolver, BrainHelper, BaseStateMachine, ContainerOutput)
- **Compat**: +3 Writer (SlashBladeWriter, YsmWriter, TpmWriter) + 12 动作委托
- **TLM API 编目**: SchedulePos(5), FavorabilityManager(3), ChatBubbleManager(2), Config(2)
- **API 文档**: `doc/IO-API-REFERENCE.md` — 346 方法完整参考
- **Bug 修复**: 30 bug (P0×5, P1×10), 错题集 84→95
- **40 commits** | 32 新文件 | 153 测试绿色

## [v28] — 2026-07-13 — I/O 原语架构迁移

- Phase 4: +30 I/O 方法 (MaidStateReader+17, MaidStateWriter+6 等)
- Phase 5: 14 动作委托 → ParamExtractor + Output
- Phase 6: 15 条件委托 → Reader
- 条件委托率: 83→98/131 (63%→75%), 动作委托率: 28→43/105 (27%→41%)
- GUI 修复 + 唱片机四连修复 + P1 爆炸源 + NBT 异常

## [v27] — 2026-07-13 — 消反射

- BuiltinRegistrar: FQCN 字符串 + Class.forName → 直接 new XxxAction()
- 零反射, 编译器验证, 覆盖 231 内置扩展

## [v26] — 2026-07-13 — ParamExtractor

- ParamExtractor 新建: rawParams ↔ typed values 桥接
- CombatOutput +6 新方法 (damageByRatio/magicDamage/genericDamage/healByRatio/knockbackWithVertical/shieldEffect)

## [v25] — 2026-07-13 — 条件委托

- 83/120 条件 → I/O Reader (MaidStateReader 56/77, TargetStateReader 17/22, WorldStateReader 10/21)

## [v16] — 2026-07-10 — 任务系统重置

- 17 工具→6 工具 | 双轨→单轨 | 5 Pipeline + 8 Service
- AI 唯一入口: `lma_start_task(task_type, target, target_count)`

## [v12.7] — 2026-07-08 — Brain Memory

- Brain Memory 替代 PersistentData 导航
- Brain Memory: 类型安全, Activity 切换自动清除, 不跨会话持久化

## [v10.0] — 2026-07-04 — 8 阶段重构完成 ✅

### v10 重构总结

| 阶段 | 内容 | 结果 |
|------|------|------|
| P0 死代码清理 | 删除 14 文件 | -800 行 |
| P1 数据模型统一 | data/ 包消除 | -300 行 |
| P2 屏幕去重 | CondRow/ActRow → Builder | -200 行 |
| P3 权限+命令 | CheatManager + /LMA | +100 行 |
| P4 @Mod 重构 | LmaSounds/LmaRegistrar | -60 行 |
| P5 标准化 | DoubleParam 类型安全 | ~0 行 |
| P6 BuiltinRegistrar | 反射循环替换手动 new | ~0 行 |
| P7 AI 基础 | compat/ai/ 包 + FlowTask | +50 行 |
| P8 测试扩展 | +2 测试文件 | +120 行 |

**总计: 删除 23 文件, 新建 13 文件, 修改 ~40 文件, 净 -1100 行**

### Phase 7 — AI 整合基础

- **LittleMaidMoreActionExtension**: AI stubs 注释 (需 TLM >= 1.5.1)
- **compat/ai/AiCompat.java**: 门控 (始终启用)
- **compat/ai/model/FlowTask.java**: 任务数据模型 + Codec

### Phase 8 — 测试扩展

- **ActionStepTest**: of() 工厂, 不可变性 (4 tests)
- **RuleDefTest**: factory/compat/minMatch (6 tests)
- **总测试**: 13 文件, ~88 方法

## [v10.0] — Phase 6 完成: BuiltinRegistrar 反射化

### Changed — 手动 new → 反射循环 (288→275行)

- **registerAllConditions()**: 130行 tryRegister → 8行 for-Class.forName 循环
- **registerAllActions()**: 100行 tryRegister → 8行 for-Class.forName 循环
- **删除 tryRegisterCond/tryRegisterAct**: 20行样板 → 日志内置在循环中
- **类名列表**: String[] 数组 (~140 条件 + ~90 动作), 编译期字符串验证

## [v10.0] — Phase 5 完成: 代码标准化

### Changed — TypedParam 类型安全

- **DealDamageAction.amount**: StringParam → DoubleParam
- **DealTrueDamageAction.amount**: StringParam → DoubleParam
- **HealAction.amount**: StringParam → DoubleParam
- **LifeStealAction.amount**: StringParam → DoubleParam

### Note — TypedParam 日志

- TypedParam 位于 core/ (零 Minecraft 依赖) — 保留 System.getLogger
- 添加注释说明设计理由

## [v10.0] — Phase 4 完成: @Mod 入口重构

### Changed — 构造器简化 + 提取初始化类

- **LittleMaidMoreAction.java**: 构造器 115→48行 (净 -67行)
- **init/LmaSounds.java**: DeferredRegister<SoundEvent> (对标 TLM InitSounds), 3 音效静态注册
- **init/LmaRegistrar.java**: 初始化编排 (扫描/加载/文档/版本门控)
- **StartupLoader.java**: 移除 SOUNDS 字段 (已迁移到 LmaSounds)

### Fixed — 清理

- **StartupLoader**: 移除未使用的 import (SoundEvent/DeferredRegister/ForgeRegistries)

## [v10.0] — Phase 3 完成: 权限系统

### Added — 权限与命令

- **CheatManager**: Minecraft 原生权限 (hasPermission 2/3/4), 单机作弊 → 等级 4
- **/LMA 命令**: `/LMA rule` (编辑入口), `/LMA trace on|off|live|list|clear` (追踪)
- **LmaCommand**: 替换旧 LmaDebugCommand, @Mod.EventBusSubscriber 自动注册

### Removed

- **forge/LmaDebugCommand.java** → 功能迁移到 `commands/LmaCommand.java`
- **forge/ 包** — 已空, 删除

## [v10.0] — Phase 2 完成: 屏幕去重

### Changed — CondRow/ActRow → Builder 模式 (净 -200 行)

- **CondRow (109行) → ConditionDefBuilder (92行)**: 可变构建器, fromParts()/fromICondition()/copy()/build() API
- **ActRow (143行) → ActionStepBuilder (128行)**: 同上, 含 57 参数键中文映射
- **6 屏幕文件更新**: RuleEditScreen, MaidRuleEditScreen, ConditionListScreen, ActionListScreen, ConditionEditScreen, ActionEditScreen
- **CondRow.java + ActRow.java 删除**: 252 行样板代码消除

### Fixed

- **ConditionEvaluator**: applyMath → ConditionMatcher.applyMath (package-private)
- **ConditionMatcher**: applyMath 从 private → package-private

## [v10.0] — Phase 1 完成: 数据模型统一

### Changed — data/ 包消除 (净 -300 行)

- **data/ConditionDef → core/model/ConditionDef**: 合并 Javadoc, 删除 @Deprecated applyMath()
- **data/ActionStep → core/model/ActionStep**: 合并 of() 工厂, Gson 适配器 → core/serialization/ActionStepAdapter
- **data/RuleDef → core/model/RuleDef**: 合并 compat 字段 + simple()/full() 工厂, 删除 toCore() 桥接
- **data/RuleEvent → event/RuleEvent**: 事件枚举迁移, event/ 包激活
- **data/MaidHarvestCropEvent → event/MaidHarvestCropEvent**: 自定义事件迁移
- **data/ 包删除**: 5 文件 → 0

### Fixed — 级联修复

- **RuleEngine**: 移除 dataRule.toCore() 调用 (不再需要), 直接使用 core.model.RuleDef
- **ConditionEvaluator**: ConditionDef.applyMath → ConditionMatcher.applyMath (package-private)
- **RuleActionStorage/MaidRuleStorage**: Gson 适配器 → core.serialization.ActionStepAdapter
- **~12 文件 import 更新**: data.* → core.model.* / event.*

## [v10.0] — Phase 0 完成: 死代码清理

### Removed — 死代码与旧系统 (14 文件, -800 行)

- **ServiceLoaderExtensionLoader**: loadAll() 从未调用 — 外部 SPI 不可用
- **YsmEvent**: 空壳类, 注册到事件总线无任何 @SubscribeEvent
- **SlashBladePresets**: createDefaults() 永远返回空列表
- **ConditionNode / ConditionNodeAdapter**: 条件树功能从未使用 (toCore() 传 null)
- **core/script/** 包: IScriptPlugin/JsScriptPlugin/LuaScriptPlugin/ScriptEngineManager — 均未使用
- **MoreActionAnimationMessage** (id=0): 已由 LmaAnimSyncMessage (id=1) 替代
- **空包**: event/, init/, impl/action/script/

### Fixed — 代码清理

- **CompatRegistry**: 补全 YSM 键映射 (6→9 条件, 4→6 动作)
- **TPM 事件**: 删除构造器反射块, 统一到 TpmCompat 模式
- **网络包重编号**: id 0→删除, id 1→LmaAnimSync, id 2→OpenMaidEditor
- **ConditionMatcher**: 移除 evaluateNode() 树形评估 (死代码)
- **RuleDef**: 移除 conditionTree/hasConditionTree/validateNode (死代码)

## [v9.3.2] — 2026-07-04 — 种地自动作物匹配

### Changed — 白名单 → 自动作物匹配 (净 -1 文件, 0 缓存)

- **AutoCropHandler**: 替代 WhitelistCropHandler。canPlant() 查看耕地上方作物类型，种子必须匹配才允许种植。空地走原版逻辑。
- **AutoMatchCropAction** (`auto_match_crop`): 新增动作，scope(maid/global)+enabled(bool)，控制自动匹配开关。其他模组可通过规则触发。
- **无缓存**: 状态内聚在 AutoCropHandler（ConcurrentHashMap.newKeySet + volatile boolean），不另建缓存文件。

### Added — 收获事件 + 预设

- **MaidHarvestCropEvent**: 新 Forge 事件，在 AutoCropHandler.harvest() 中同步触发（isEnabled 之前），使规则可响应每次收获
- **预设-自动匹配种子** (ID=10): `maid_harvest_crop` → `auto_match_crop(scope=maid, enabled=true)`，概率 1.0，冷却 2tick，无前置条件。首次收获即启用

### Removed

- `SetFarmSeedsAction` / `FarmWhitelistCache` / `WhitelistCropHandler` — 被自动匹配替代

## [v9.3.1] — 2026-07-03 — 属性动作增强 + 动态类型 + BuiltinRegistrar 修复

### Changed — 属性动作/条件动态类型 (5 项)

- **MaidAttrRegistry.Entry**: 添加 `valueType` 字段 (num/bool/str)，17 属性全部标记 num
- **ModifyMaidAttrAction**: params 简化为 1 参数（仅 attribute），execute() 根据 valueType 分发（num: set/add/multiply/divide + 钳制，bool: 是/否→1.0/0.0，str: 预留）
- **MaidAttrCondition**: evaluate() 根据 valueType 返回不同格式（num→数值，bool→true/false，str→字符串）
- **ActionEditScreen**: +initModifyMaidAttr() 动态表单（NUM: 运算下拉+数值输入，BOOL: 是/否，STR: 文本框）
- **ConditionEditScreen**: +getEffectiveType()，maid_attr 条件操作符根据属性类型动态切换

### Fixed — BuiltinRegistrar 遗漏 (3 项)

- **ModifyMaidAttrAction**: 补注册到 BuiltinRegistrar（MAID_EXT 区）
- **SetFarmSeedsAction**: 补注册到 BuiltinRegistrar（MAID 区）
- **MaidAttrCondition**: 补注册到 BuiltinRegistrar（maid 条件区）

### Docs — 错题集 (3 条)

- ERROR_LOG 新增 12.1-12.3: BuiltinRegistrar 注册遗漏 / 未检查 git log 创建重复代码 / 裸 parseDouble

## [v9.3] — 2026-07-03 — 种地白名单 + 女仆属性读取

### Added — 耕种种子白名单 (3 项)

- **SetFarmSeedsAction** (`set_farm_seeds`): 限制女仆只能种植指定种子。`scope=maid` 独立规则覆盖全局，`scope=global` 全局默认
- **WhitelistCropHandler**: ISpecialCropHandler 实现，拦截 FARMLAND 上的 canPlant 调用
- **FarmWhitelistCache**: 双层优先级缓存 (maid > global > allow all)，纯内存不写 NBT

### Added — 女仆属性条件/动作 (2 项)

- **MaidAttrCondition** (`maid_attr`): 读取 17 个 TLM+原版属性 (attack_damage, max_health, maid_pickup_range...)
- **ModifyMaidAttrAction** (`modify_maid_attr`): add/set 模式修改属性基础值

### Added — 属性 SelectionScreen GUI (1 项)

- 任何名为 `attribute` 的 StringParam 自动渲染为 SelectionScreen 按钮（搜索+分类过滤），无需手写渲染代码
- ConditionEditScreen + ActionEditScreen 通用支持

### Fixed — 文档 (1 项)

- CLAUDE.md: 移除不存在的 DynamicSoundResources.java 引用

## [v9.1] — 2026-06-30 — TLM API 全覆盖 + PlaySound 增强 + 版本门控

### Added — TLM 全 API 覆盖 (22 项)

- **2 新事件**: `wireless_io` (MaidWirelessIOEvent), `maid_transform` (MaidAndItemTransformEvent)
- **1 反射事件**: `maid_request_item` (TLM >= 1.5.1, 版本门控反射注册)
- **17 新条件**: maid_home_mode, maid_has_restriction, maid_in_restriction, maid_restrict_radius(NUM), maid_is_pickup, maid_can_brain_move, maid_has_backpack, maid_has_helmet, maid_has_chestplate, maid_has_leggings, maid_has_boots, maid_on_hurt, maid_swinging_arms, maid_is_fishing, maid_backpack_fluid(STR), maid_sound_pack(STR), maid_schedule_activity(STR)
- **3 新动作**: set_invulnerable (无敌开关), set_home_mode (家园模式), clear_restriction (清除活动范围)

### Added — PlaySound 增强 (3 项)

- **模组名随机**: `modid:` → 从该模组所有已注册音效随机
- **编号系列**: `modid:sound` 不存在 → 尝试 sound1, sound2, ... 收集+随机
- **上限保护**: 最多扫 99 个编号

### Added — TLM 版本门控

- `TlmVersion`: 版本检测 + 纯数字剥离(-forge后缀)
- `TlmVersionedEvents`: 运行时反射注册高版本事件
- 新增 `isV151/isV152/isV160` 快捷方法

### Fixed (3 项)

- 规则排序 ID 数字序 (修复 100 排在 2 前面)
- nextId 填缺号 (修复 max+1 跳号)
- PlaySound 兼容检测 + 已注册验证

## [v9.0] — 2026-06-30 — 文件系统规则 + 搜索 + YSM 随机模型

### Added — 文件系统规则存储 (4 项)

- **多目录单文件**: `rules/main/<id>.rule.json` + `rules/<modid>/` + `maid_rules/<uuid>/`
- **compat 字段**: 规则新增 `compat` 数组，标记依赖模组，保存时自动推断
- **门控加载**: compat 规则目录仅在对应模组加载时激活
- **迁移**: `scripts/migrate_rules.py` 旧格式→新格式 + 备份

### Added — SelectionScreen v2 搜索+分类 (3 项)

- **搜索框**: `EditBox` 实时过滤，匹配 label+subtitle（中文名/key/分类），3 字符定位
- **分类下拉**: `CycleButton` 按需出现，`categoryFn` 参数控制，分类从 options 动态提取
- **compat 自动纳入**: YSM 等兼容模块的条件/动作自动出现在对应分类下

### Added — CompatScanner 通用扫描器 (1 项)

- **三扩展支持**: condition/action/event 三类目录，任意传 null 跳过
- **YsmCompat 瘦身**: 241→65 行 (-73%)，扫描逻辑全部委托 CompatScanner
- **新 compat 模块**: 只需 ~15 行 init() 代码

### Added — SetYsmModelAction 随机模型 (1 项)

- **mode SelectParam**: `ysm女仆模型`(默认) / `输入`
- **随机模式**: "酒狐与小伙伴" 22 模型硬编码列表 → 随机模型 + 随机纹理
- **手动模式**: model_id/texture/model_name 均支持逗号分隔多值随机选一

### Changed

- **文档**: REFACTOR_PLAN.md 更新 V9 路线图 (新增 Phase 6-9)

## [v8.7.1] — 2026-06-29 — Bug 修复: 崩溃 + UI + 验证

### Fixed — P0 崩溃 (2 项)

- **P0: ★ 右键女仆闪退 — Recursive update 崩溃**: `MaidRuleStorage.getRules()` 使用 `ConcurrentHashMap.computeIfAbsent()` 但其 lambda 内 `load()` 又调用 `STORE.put()`，触发 Java 递归更新异常 → 渲染线程崩溃。修复：`getRules()` 改用 `get() + null-check + load()` 模式。同时 `load()` 所有异常/边界路径统一 `STORE.put` 缓存空值防止重复 IO。
- **P0: ★ MaidRuleEditScreen UI 堆叠**: 冷却标签与输入框同 X 坐标重叠、优先级标签叠匹配按钮、启用按钮 130px 遮挡 ID 文字、重复 import。修复：对齐 `RuleEditScreen` 布局 — 启用按钮改 56x16 放右侧、冷却标签偏移 `font.width`、匹配按钮左+优先级标签右、删除重复 import。

### Fixed — P1 验证/UI (3 项)

- **P1: :=: 操作符验证误报**: `ConditionOperator.EQ` token 是 `":="` (2字符)，但所有预设用的都是 `":=:"` (3字符，符合 `:op:` 约定)。`isValidToken` 严格检查报错。修复：`fromToken()` 和 `isValidToken()` 增加 `":=:"` 别名。
- **P1: load() 异常路径未缓存**: 目录创建失败/JSON 解析失败路径未 `STORE.put` 空值 → 每帧重复文件 IO。修复：全部返回路径统一缓存。
- **P2: "恢复预设"按钮文案过时**: 显示 "恢复 3 条预设规则" 实际已 9 条。修复：改为 "恢复预设规则？所有自定义规则将被清除，此操作不可撤销！"

### Changed

- **MaidRuleEditScreen 布局对齐 RuleEditScreen**: 字段间距、标签位置、按钮大小全部统一
- **ConditionOperator**: `:=:` 和 `:=` 均合法，互操作

### 文件变更

| 文件 | 变更 |
|------|------|
| `storage/MaidRuleStorage.java` | getRules() 去 computeIfAbsent; load() 所有路径 STORE.put |
| `screen/MaidRuleEditScreen.java` | 布局对齐 RuleEditScreen; 删除重复 import |
| `screen/MainEditorScreen.java` | "恢复预设"按钮文案 |
| `core/model/ConditionOperator.java` | fromToken/isValidToken 加 :=: 别名 |

## [v8.4] — 2026-06-26 — Bug 修复 + auto_wait + 删除过强项

### Removed — 删除过强内容 (5 项)
- **事件**: `maid_tick` — 每 tick 高频触发，性能 + 平衡问题
- **条件**: `maid_is_vehicle` — 作为载具检测
- **动作**: `set_data` (设置数据), `conditional` (条件分支), `run_script` (执行脚本)
- 联动清理: RuleEvent, TlmEventAdapter, BuiltinRegistrar, GroupBuilder, ActionPipeline (evalInlineCondition), ParallelGroup, DocGenerator

## [v8.4] — 2026-06-26 — Bug 修复 + auto_wait

### Fixed — P0 Bug 修复 (7 项)

- **P0: wait_anim 无限循环**: `ActionPipeline` 恢复索引指向 wait 步骤自身，导致重新遇到并再挂起。修复：`resumeIdx+1` 跳过 wait 步骤自身
- **P0: send_message 三种类型均不显示**: `"actionbar"` 与参数 `"action_bar"` 拼写不匹配，status 类型未实现。修复：添加 switch 分发 + ServerBossEvent 3秒显示
- **P0: maid_tick 日志洪水**: `RuleEngine.handleEvent()` 每 tick INFO 级别输出淹没了 trace 输出。降级为 DEBUG
- **P1: DealDamageAction execution_kill 伤害源错误**: fall through 到 `mobAttack`，应为 `magic` (与 ExecutionKillAction 一致)
- **P1: resumeFrom 无追踪**: TickScheduler 恢复执行时没有 RuleTracer 支持，后续动作不产生 trace/chat 消息。添加 RuleTracer.start/finish/addAction 贯通

### Added — PlayAnimAction auto_wait

- **auto_wait 参数**: play_anim/play_weapon_anim 新增 `auto_wait: true` 参数，动画播放后自动挂起管道等待动画完成。替代 `play_anim → wait_anim` 两步骤
- **GroupBuilder 自适应**: auto_wait 时自动将 play_anim 单独成组 (与后续动作隔离)
- **ActionPipeline 挂起检测**: 并行组完成后检测 auto_wait 组，自动调度 TickScheduler 挂起
- **FULL 模式支持**: 三阶段总时长 (dur_start + dur_casting + dur_end) 自动计算

### Changed

- **ExecutionKillAction**: 添加 info 级斩杀日志 (含目标名/hp/dmg)
- **SendMessageAction**: 添加 owner 离线 warning + 消息为空 debug 日志
- **预设处决规则**: 简化为 cancel_event → teleport → play_anim(auto_wait) → execution_kill

## [v8.3] — 2026-06-25 — 调试系统完善

### Added — 游戏内实时调试消息
- **`/lmma_trace live`** — 实时游戏内消息模式，规则触发时向主人玩家输出：
  - 规则命中：规则名称、事件ID、匹配模式
  - 条件逐条评估：每条件通过/失败 + 实际值 vs 期望值
  - 动作逐步执行：每步骤类型 + OK/FAIL
- **`/lmma_trace list`** — 展开显示条件和动作详情（不再只有摘要）
- **RuleTracer 全面贯通**：
  - `ConditionMatcher.evaluate()` → `RuleTracer.addCondition()`
  - `ActionPipeline.execute()` → `RuleTracer.addAction()`
  - `RuleEngine.handleEvent()` → `RuleTracer.start(m aid)` / `RuleTracer.finish()`
- **ThreadLocal 隔离**：多女仆并行事件互不干扰

### Changed — 日志审计
- `RuleEngine`: ENTER/HIT/EXIT 日志保留，添加 `[LMA/Trace]` 前缀的追踪日志
- `ConditionMatcher`: 条件评估自动记录到 RuleTracer
- `ActionPipeline`: 动作执行自动记录到 RuleTracer
- `LmaDebugCommand`: list 命令展开条件/动作详情

## [v8.2] — 2026-06-25 — 条件/动作/UI 全面扩展

### Added — 100 条件 (从 35 增长)

- **v8 参数化条件**: ConditionDef/CondRow 新增 `params` Map 字段, Pipeline 贯通 data→core→cache→matcher
- **v8.1 TLM API 条件 (+12)**: maid_is_baby, maid_is_begging, maid_is_rideable, maid_is_aiming, maid_experience, maid_luck, maid_schedule, maid_is_invulnerable, maid_model_id, can_see_target, is_tamed, maid_is_struck_by_lightning
- **v8.2 全维度覆盖 (+53)**: MAID 数值属性(maid_health/max_health/armor_toughness/attack_damage/speed/fall_distance/air_supply), MAID 布尔状态(maid_is_sleeping/swimming/using_item/in_lava/in_rain/sprinting/vehicle/has_target/holding_projectile/has_any_effect/has_curse/can_climb), MAID 字符串(maid_mainhand_tag), TARGET 全状态(target_health/armor/distance_h/distance_v/is_animal/monster/alive/on_ground), WORLD 全维度(world_time/is_day/is_thundering/moon_phase/difficulty/biome/light_level/has_daylight), OWNER 扩展(owner_health_ratio/armor/holding_item), DAMAGE(bypasses_armor/is_critical_attack)

### Added — 75 动作 (从 53 增长)

- **战斗动作 (+8)**: deal_percent_damage, deal_true_damage, heal_percent, life_steal_percent, launch(击飞), extinguish(灭火), execution_kill(斩杀), bleed(流血DoT)
- **移动动作 (+6)**: pull(拉拽), push(推开), swap_position(交换位置), slow(减速), face_target(面向目标), freeze_ai(冻结AI)
- **女仆控制 (+3)**: set_glowing(发光), set_invisible(隐身), set_silent(静音)
- **世界交互 (+3)**: set_weather(天气), set_time(时间), send_chat(聊天)
- **物品 (+2)**: clear_inventory(清空背包), repair_item(修复物品)

### Added — GUI 升级

- **SelectionScreen**: 全屏列表选择器, 仿 Minecraft LanguageSelectScreen, 替代 CycleButton 用于条件键(100)/动作类型(75)/事件(29)选择
- **条件参数编辑**: ConditionEditScreen 新增类型化参数渲染(SelectParam/BoolParam→CycleButton, 其他→EditBox)
- **条件分类添加**: ConditionListScreen [+num/str/bool] → [+ 添加条件] 分类选择按钮
- **分类副标题**: SelectionScreen 支持右侧分类/类型显示

### Fixed

- **P0**: `target_type :=: zombie` 不匹配 — `getPath()` 去掉命名空间前缀
- **P0**: `is_mainhand_attack` 逻辑反转 — `getEntity()==null`→`!=null` + 武器检测 + 20tick 窗口
- **P1**: `is_owner_attacker`/`is_owner_target` 瞬时条件不可触发 — PersistentData + hurtTime 时间窗口
- **P1**: `maid_has_curse` 编译失败 — `hasCurse()` 不存在于 1.20.1, 改用 hasBindingCurse/hasVanishingCurse
- **P1**: `Difficulty.getKey()` 不存在 — 改用 `.name().toLowerCase()`
- **P1**: `DamageSource.bypassArmor()` 不存在 — 改用 `damageSources().magic()`

### Verified — API 精确性

- EntityMaid 2844 行源码精确提取 80+ 公开方法
- 所有新增条件/动作方法签名均从 TLM/Forge 源码验证
- 用户参考中的 9 个 TLM 事件(MaidUseTotem/MaidBlockDamage 等)经源码验证不存在于 1.20.1

## [v7.1] — 2026-06-24 — 模块化标准化重构

(see previous changelog entries)
