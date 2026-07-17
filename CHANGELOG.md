# Changelog

## [v36.7] — 2026-07-17 — 删除祭坛合成任务

- 任务栏移除"祭坛合成"（有合成台不需要祭坛 — 用户决策）：TaskRegistry 注册 + AltarCraftPipeline 删除、协调行为 altar_craft 分支清理、AI 工具/关键词/图标/分组/语言键同步移除
- **保留**规则引擎祭坛生态：place_altar_item 动作 / AltarPresets 预设 / AltarOutput/AltarExecute IO — 玩家仍可用规则驱动祭坛
- 条件默认参数值 altar_craft → craft_chain（3 文件 6 处）

## [v36.6] — 2026-07-17 — 状态原子化 + 采集目标抽象 + TaskEngine 打架修复

- **P0 根因**: lma_flow_tick 无心跳 → TaskEngine 60 秒超时杀活任务 → auto-restart 无限 churn（"卡住"的真凶，日志实锤）
- **TaskStateService**（新）: lma_flow_* 状态写入单一所有者 — init/heartbeat/fail/complete/clearAll；协调行为全部委托；执行器 keepAlive 打心跳 → 活任务永不被超时杀
- **HarvestTarget 抽象类**（新，用户要求的检测原子化）: matches/canHarvest/validAt/veinPredicate/consumesDurability/intervalTicks/label 六判定收拢，WOOD/ORE 两实现；执行器归零内联判断，新采集类型=一个新实现类
- 半径修复: 无家模式回退 config 默认 16 格（原裸用 restrictRadius 可能无效）
- 待机可感知: 无目标时一次性气泡"附近没有可采集的目标"（状态翻转才再发）+ DEBUG 含半径值

## [v36.5] — 2026-07-17 — 修复：stale failed 阻塞任务切换 + 气泡 API 误用

- **P0**: failTask 残留 lma_flow_task 导致切换任务后女仆不动 → checkExtra 对已注册 LMA 任务放行重新初始化
- **P0**: TLM 气泡 API 第二参数是上一个气泡 id 而非超时毫秒（WorldOutput 包装层 Javadoc 勘误）→ 新增 sendBubbleReplacing 替换式刷新，蓄力倒计时每 3 秒更新且无每 tick 洪水
- 链式任务搜索无果不再 failTask 刷屏 → 交给 executor 常驻空闲扫描待机

## [v36.4] — 2026-07-17 — 简化循环：3 秒扫描 → 走最近 → 蓄力整脉同消

- **循环重设计**（用户方案）：每 3 秒扫描周围 → 最近的能挖目标（等级过滤）设路径走过去 → 到达 BFS 整脉/整树 → **蓄力（块数 × 每块 tick）后整脉同时消失** → 立即接下一轮
- 蓄力期挥臂 + 气泡倒计时（"伐木 32 块 蓄力 6.4 秒"）
- **任务常驻**：范围内无目标不再休眠，保持空闲扫描 — 树再生/矿刷新自动恢复作业
- 耐久保护：蓄力前队列截断至剩余耐久-1；破坏后按实际块数一次性扣耐久
- 挖不了（等级不足/非天然树）→ 跳过 → 同 tick 找下一个
- 移除 v36.3 批量预队列（复杂度不换收益）；旧存档 lma_chain_idx/tick 残留 key 自动清理

## [v36.3] — 2026-07-17 — 批量预队列 + 头顶气泡进度

- **批量预队列**：到达时一次性搜范围内所有目标 → 逐脉 BFS → 全量扁平化入单一队列 → 跳过集标记。打破旧"每脉走→挖→搜下一脉"慢循环，20 棵树从 20 次导航降至 1 次+脉间短步
- **头顶气泡**：实时显示 "伐木/采矿 剩余 23/64 块"（WorldOutput.sendBubbleIfTimeout 3s 冷却）
- 跳过集 SKIP_MAX 128→10（批量预队列后作用降低）

## [v36.2] — 2026-07-17 — 挖掘等级速度表 + 跳过集收紧

- **速度按挖掘等级查表**（镐/斧同表，tick/块）：空手/非对应工具 40 / 木 20 / 石 15 / 铁 10 / 钻石 5 / 下界合金 5 — 等级越高越快
- 跳过集上限 128 → 20（范围内矿脉/树可数）
- 移除 config break_interval_ticks / no_axe_interval_multiplier（被固定等级表取代）
- 计算层新增 `ToolJudge.harvestIntervalTicks(tool, requireAxe)`

## [v36.1] — 2026-07-17 — 连锁任务流程修正（跳过机制 + 无斧慢砍）

- **跳过机制**：镐等级不足的矿 / 非天然树 → 记入跳过集（按工具等级版本化，换好镐自动重置；上限 128 FIFO），接力搜索下一目标 — 不再 FAILED 休眠
- **等级前置过滤**：接力搜索 ORE 模式直接排除挖不动的矿（canPickaxeMine 进搜索谓词），不走冤枉路
- **无斧慢砍**：砍树不再要求斧 — 持可用斧=正常速度+扣耐久；空手/非斧=慢速（间隔 ×no_axe_interval_multiplier 默认 4）+ 不扣耐久
- **接力优化**：砍完一棵/挖完一脉直接搜下一目标设导航（免 GUI-init 往返）
- Config 新增: chain_harvest.no_axe_interval_multiplier(4)
- ChainWoodPipeline validate 软化（无斧提示慢砍模式而非拒绝）

## [v37.2] — 2026-07-17 — EnvSense 扩展：生物分类/时间段/亮度/死亡感知/玩家门控/结构探测

### 新增感知能力
- **玩家门控**（性能闸）：仅玩家 20 格（config player_gate_radius, 0=关）内的女仆参与一切感知 — 挂机农场女仆零开销
- **实体分类感知**（存在即命中，快照含实体列表）：`env_nearby_monster`（MobCategory.MONSTER, TLM 先例）/ `env_nearby_friendly`（非怪物非杂项 Mob）/ `env_nearby_maid`
- **时间段**：WorldInfo 新增 timeSegment（DAY/DUSK/NIGHT/DAWN 自定义四段）+ `env_time_segment_change` 边沿
- **亮度**：`env_darkness`（亮度 < 7 进入黑暗时触发，config darkness_threshold）
- **雷暴开始**：`env_thunder_start` 单边沿（比 weather_change 精确）
- **玩家周围实体死亡**：新规则事件 `lma_nearby_death`（LivingDeathEvent 驱动，ctx.target=死亡实体，玩家门控 + 女仆半径过滤；女仆自身死亡仍走 maid_death）
- **结构探测**（低频边沿，默认每 MC 天一次/女仆，config 可关）：`env_village_nearby` / `env_mineshaft_nearby`（原版 StructureTags）/ `env_pillager_outpost_nearby`（LMA 自带 datapack 标签，原版无此 tag）
- **StructureSensor 注册类型**：`addStructureSensor(id, TagKey<Structure>, appliesTo, callback)`
- Config 新增 5 项: player_gate_radius(64) / darkness_threshold(7) / structure_enabled(true) / structure_interval_ticks(24000) / structure_radius_chunks(8)

## [v37.1] — 2026-07-17 — 世界感知补齐（常驻 + 变化触发）

### 世界状态感知（判定对齐 TLM）
- **WorldSensor 类型** — `addWorldSensor(id, trigger(prev,now), appliesTo, callback)` prev/now 边沿触发
- **6 个内置常驻感知器**（所有女仆，纯规则事件通道）：
  `env_too_cold`（温度<0.15, TLM COLD 档）/ `env_too_hot`（>1.0, TLM 判热）/ `env_snowing`（下雨中且位置降水=SNOW）/ `env_day_night_change` / `env_weather_change` / `env_dimension_change`
- WorldInfo 新增: tempCategory(COLD/OCEAN/MEDIUM/WARM 四档) / temperature(biome 基础温度) / precipitation(NONE/RAIN/SNOW) / dayTime
- 边沿检测：只在进入状态时触发一次，不重复轰炸
- 内置注册后系统**常驻**（每女仆 200 tick 一次轻量世界读取；方块/实体扫描仍按需）
- Config 新增: cold_threshold(0.15) / hot_threshold(1.0)
- 用法：规则事件 `lma_env_scan` + 条件 `env_sensor_hit(sensor_id=env_too_cold)` → 任意动作

## [v37] — 2026-07-17 — 环境感知调度器 EnvSense

### 新系统（低耗环境感知 + 注册 API）
- **EnvSenseRegistry** — `addBlockSensor/addEntitySensor(id, matcher, appliesTo, callback)` 注册 API，id 去重 WARN，appliesTo=null 适用所有女仆
- **EnvSenseScheduler** — 每女仆每 200 tick（config 可调）最多一次合并扫描；**零注册=零开销**（每 tick 仅一次布尔判断）；按需扫描（无适用感知器的女仆零扫描）
- **EnvScanner**（输入层）— N 感知器共享单次 `(2r+1)²×9` 方块遍历 + 单次 AABB 实体查询 + 世界状态直读；半径=工作范围优先
- **EnvSnapshot** — 不可变快照缓存，任意代码 `getSnapshot(maid)` O(1) 读取；命中按距离排序截断
- **双通道触发**: ①命中感知器 Java 回调（try/catch 隔离）②`lma_env_scan` 规则事件（ctx attr `env_hits`）
- **env_sensor_hit 规则条件** — O(1) 查快照，规则不再自己扫描（对比 detect 条件绑 maid_tick 每 tick 3,969 方块位，降 ~99.5%）
- 缓存闭环：EntityLeaveLevelEvent 清理 LAST_SCAN + SNAPSHOTS
- Config 新增 env_sense 组: scan_interval_ticks(200) / default_radius(16) / max_hits_per_sensor(32)

## [v36] — 2026-07-17 — 连锁采集任务：采集木材 + 采集矿石

### 新任务 (环境感知硬编码任务引擎，非规则引擎)
- **collect_wood 采集木材** — 女仆持斧自动寻找工作范围内树木，走到树旁 3 格内连锁砍伐整棵（BFS 26 邻域），树叶留存自然衰减
- **collect_ore 采集矿石** — 女仆持镐自动挖掘同种矿脉，挖掘等级判定（木镐挖不动钻石矿）
- 多 tick 逐块破坏（默认 5 tick/块）+ 挥臂动画 + 工具耐久消耗（剩 1 点耐久停手）
- 天然树校验：原木需连接非手放树叶，防拆玩家木建筑（config 可关）
- 一轮完成自动搜索下一目标，连续作业

### 新分层组件 (IO 原子化架构)
- **输入层** `input/item/ToolStateReader` — 工具原子读 IO（tier/类型/耐久/NBT）
- **输入层** `input/search/ConnectedBlockSearch` — BFS 连通搜索 + 天然树 DFS 校验
- **计算层** `task/service/ToolJudge` — canPickaxeMine/canAxeChop/isToolUsable 判断组合
- **执行层** `execute/ChainHarvestExecute` — WOOD/ORE 共用状态机，只编排零内联判断
- `task/pipeline/ChainWoodPipeline` + `ChainOrePipeline`

### 框架扩展
- TaskRegistry 新增 `registerSearch()` — searchPredicate 标签/多方块搜索（原 targetBlock 只支持单方块）
- LmaFlowCoordinationBehavior searchBlock/isBlockValid 支持 searchPredicate
- TlmEventAdapter 跨 session 清理新增 lma_chain_* 队列键
- LmaTaskTypeRegistry.getIcon 精确映射优先（修复 collect_wood 误中 collect→镐图标）
- Config 新增 chain_harvest 组: max_blocks(64) / break_interval_ticks(5) / wood_nature_check(true)

## [v34.2] — 2026-07-15 — 女仆编辑器注册模式 + execute Bug 修复

### 注册模式重构 (4 新文件)
- **FieldType.java** — 字段类型枚举 (INT/FLOAT/BOOL/STRING)
- **MaidEditorRegistry.java** — 注册中心 (addGroup/addField), 外部 mod 可扩展
- **BuiltinMaidEditorRegistration.java** — LMA 内置 9 组 ~120 字段注册
- 使用 I/O 层 reader/writer lambda: MaidStateReader 读, MaidStateWriter 写

### MaidEditorScreen 重写
- 硬编码 GROUPS + 120 case switch → registry 动态读取
- 修复 apply：不再 rebuild 覆盖用户编辑
- 新增"保存"按钮：全字段写入 + 关闭
- 修复 writer 缺失 9 处（攻击/移速/幼年/游泳 等）
- 只读字段加 `(只读)` 标记
- 抗性组重做 (8项可改) + 饰品组新增 (12项只读)
- 布局修正：页码居中于翻页按钮之间

### MaidListScreen
- TLM MaidModelGui 同款预览：直接渲染选中女仆，跟随鼠标旋转
- 去除预览面板框

### execute/ 6 Bug (v34 代码审查)
- FurnaceExecute: 冗余 isEmpty 预检 → 去掉
- FurnaceExecute: COLLECT_RESULT 非对称 → 无条件推进
- AltarExecute: List.hashCode() → System.identityHashCode()
- AltarExecute + 3文件: 硬编码 -4/4 → VanillaConstants.SEARCH_VERTICAL
- CraftExecute: 加单线程假设注释

### MaidStateWriter 新增
- +17 writer: setHealth/setMaxHealth/setAttackDamage/setMovementSpeed/setFollowRange/
  setKnockbackResistance/setAttackSpeed/setArmorToughness/setBaby/setSprinting/setSneaking/
  setSwimming/setRestrictRadius 及 7 个 InitAttribute writer

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
