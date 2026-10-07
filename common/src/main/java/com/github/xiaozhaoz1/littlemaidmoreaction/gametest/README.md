# gametest — 游戏内集成测试 (121 用例)

**作用**: 在真实服务器环境跑端到端用例 (任务/方块/实体/GUI 服务端对偶)。**2026-09-21 按域拆分为 8 个文件** (原单文件 `LmaGameTests` 6490 行 ⇒ 7 测试类 + 1 基类), 用例方法名 `lmaXxx`。
**依赖方向**: 可以调任何层 (它是测试); 被 `:forge:1.20.1:runGameTestServer` / `:neoforge:1.21.1:runGameTestServer` 驱动。

## 〇、类结构 (2026-09-21 拆分; test ID 逐字未变)
| 类 | 用例数 | 域 |
|---|---|---|
| `LmaGameTestSupport` | — (**基类, 无用例**) | 27 个辅助/夹具方法 + 2 共享字段 (`extends TLMGameTests`) |
| `LmaCoreGameTests` | 8 | 冒烟/注册/生命周期/门控/引擎/NBT/配置 |
| `LmaHarvestGameTests` | 20 | 连锁采集 (矿/木) |
| `LmaStationGameTests` | 25 | 工作站/交互 (唱片机/钟/方块交互/手臂转移/熔炉/篝火) |
| `LmaWorldGameTests` | 21 | 空置域/填坝/农田 |
| `LmaCompatGameTests` | 18 | 合成/装配/Create·CBC |
| `LmaTacticalGameTests` | 14 | 防御塔/Token/哈气/自救 |
| `LmaPassiveGameTests` | 15 | 被动/环境 (刷扫/探险图/群系/节日/火把/结构与挤奶) |
> ⚠ **每个测试类自持** `@GameTestHolder(LittleMaidMoreAction.MOD_ID)` + `@PrefixGameTestTemplate(value = false)` ——
> 两平台源码实测 (`ForgeGameTestHooks:107` / `NeoForge GameTestHooks:92`) 注解按**方法所在类**读取、**继承不生效**,
> 且 `clazz.getDeclaredMethods()` 不收集继承方法 ⇒ **基类不带注解**, 少了会整类静默不注册 (用 `scripts/gametest-ids.mjs` 闸门兜住)。
> 拆分铁律: **test ID 逐字不变** (`node scripts/gametest-ids.mjs verify <node> before-1b` ⇒ 121 = 121)。

### ⚠ 槽位敏感 (2026-09-21 实测, **待评估** — 比单个 flake 更重要)
> **框架层：gametest 槽位分配依赖类扫描顺序 ⇒ 类结构变化会改变测试世界坐标。待评估是否应稳定化（按方法名 hash / 显式槽位声明）。**
- **实测依据**: 1b 拆分（1 类 → 7 类）后，同一用例的世界坐标从 `-5008607,-58,-12812533` 变为 `12763857,-58,2899656`；且**同一产物逐轮运行坐标也不同**（第三次采样 `490406,-58,4157188`）⇒ 槽位**既随类结构变、又逐轮浮动**。
- **影响**: 任何依赖"绝对世界坐标/邻居分布"的用例（连锁采集、寻路、清场类）都可能因**文件搬移/拆类**而变红 ⇒ 以后任何 gametest 结构重构都必须**跑全量并归因**。
- **评估方向**: ① 按方法名 hash 稳定槽位；② 用例显式声明槽位；③ 或由夹具自带"与坐标无关"的隔离（清场 + 只统计本结构）。
- **⚠ 标签更正 (2026-09-21, 用户裁定)**: `lmaChainOreNearBeforeFar` **不是"已登记间歇"** —— 它是**槽位敏感真缺陷**:
  **槽位落在水体/海床 ⇒ 必红**（确定性依赖槽位，非随机 ✗）。**翻转举证**: 上一轮 `neo 3/3 红 / forge 绿`，本轮 `neo 2/2 绿 / forge 红` —— 若只是"偶发"方向不会翻转 ✓。
  ⇒ 由此**证伪**"neo 特有任务层差异"的归因 ✗ (真因 = **地形**，不是平台 ✓)；真因链: 夹具清场球**敞口** ⇒ 上方水体倒灌走廊 ⇒ 走不动 ⇒ 超时 (证据: `路径诊断 … Water Water Cobbled Deepslate Water Water`)。
  ⇒ 修法 = **球壳密封** (19<d²≤20 → STONE，水密泡；壳在结构盒外 ⇒ 不干扰用例意图 ✓) + 密封前水源探针自证 ✓
- **关联**: chain-ore 家族红（`build-logs/PLAN-maidindex-family.md` + `VERDICT-chainore-cross-structure-cleanup.md`）就是该问题的**症状之一**（另一层: 跨结构清场 ✓ 已由独占 batch 解决 ✓）。

## 一、运行方式
```bash
rm -rf forge/versions/1.20.1/run/gametest          # 清世界 (必须! 否则残留状态污染)
./gradlew :forge:1.20.1:runGameTestServer          # 121 用例 (neoforge 同: :neoforge:1.21.1:runGameTestServer)
./gradlew -PcompatRuntime=true :forge:1.20.1:runGameTestServer   # 带 Create/CBC (compat 任务才真跑)
```
**判定**: 只看日志 `GAME TESTS COMPLETE` / `All N required tests passed` —— ⚠ **mod 加载失败时 Gradle 仍可能报 BUILD SUCCESSFUL** (假绿形态之一)。

## 二、用例分布与覆盖
| 域 | 代表用例 | 备注 |
|---|---|---|
任务生命周期/通用 | `lmaTaskLifecycle` 等 | 提交→tick→完成 |
移动/工作站 | `lmaFurnaceNavigate`(真导航) · `lmaCampfireCook` · `lmaBellRing`? | **移动类必须真导航** (见下陷阱) |
农田/挖空 | `lmaFarm*` · `lmaVoidExcavation*` | 区域/池 |
防御塔 | `lmaDefenseTower*` (7 条) | **各自独立 batch + 固定清单清场** |
界面 (服务端对偶) | `lmaConfigMenuMatrix` · `lmaDefenseTowerMenuWrite` · `lmaMaidAssemblyWriteRun` | 菜单构造/写入/产出 |
compat | `lmaMaidAssembly*` · `lmaCrank/Press/Mix/Power/RunningBelt` · `lmaCannonLoad` | 需 `-PcompatRuntime=true` 才真跑 |

## 三、血泪陷阱 (每条都是本会话实测踩出来的)
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **模板是 16³, 边界无地面** | 目标放 x/z > 15 **会掉下去** ⇒ 目标必须在模板内 (塔测试实测: 越界目标 19/20 箭脱靶)。|
**B** | **移动类任务: 目标必须 >10 格** | 否则测不到"走过去" (用户铁律); 且**禁止注入 `TARGET_POS`** —— 注入 = 女仆"已在目标旁", 搜索+导航整段没测 (既有 furnace/campfire 测试就这样 ⇒ 96 用例全绿而游戏是坏的)。|
**C** | **塔测试: 独立 batch + 固定清单清场** | ① 同 batch 内用例**并发**, 塔会互射对方目标 ② 塔方块**不自动消失**, 残留塔继续射击后续用例 ⇒ 两者都要治 (清场**按已知坐标**, **禁止 ±64 逐格扫描** = 81 万次/调用拖垮服务器)。|
**D** | **靶子不能共用** | 多塔共用靶子时, **不耗箭的一方(御币弹幕)先把目标杀光** ⇒ 别的塔 `damage=0` 看似"坏了" ⇒ 每塔配**对齐同 z 的专用靶**。|
**E** | **夹具校验要区分"夹具坏"与"被测对象坏"** | 失败信息里带 `[夹具: 目标存活=N/M]`; 被本方塔打死**属正常** (证明塔在工作), 不能当失败条件。|
**F** | **compat 用例可能是"跳过式通过"** | 无 Create/CBC 时任务不注册 ⇒ 用例直接 `succeed()` ⇒ **假绿** ⇒ 真跑要 `-PcompatRuntime=true`; 基线表已标注。|
**G** | **共享世界 = 状态会串** | 每轮挂的用例可能不同 ⇒ **判定回归看"失败集合是否稳定"**, 单轮失败不等于回归 (用 `runGameTestServer` 多轮采样)。|
**H** | **客户端渲染测不到** | gametest 是服务端 ⇒ 渲染只能静态审查 + `[Screen]` 生命周期日志 (参 `screen/README.md` 陷阱 E)。|
**I** | **"限时等 AI 完成"必须用条件轮询, 不能用固定 tick 断言** | `runAfterDelay(N){ 断言 }` = 赌 N tick 内 AI 走完 (走路路径/扫描节流/机器负载都影响) ⇒ **时序 flaky** (错题 #270「全绿不可复现」同源)。正解 = **条件轮询到截止时间**: `pollUntil(helper, 谓词, maxTicks, what, maid, watch...)` (成功即结束; 超时打印现场: 女仆位置/任务/观察点方块); 需要"达成后先收尾再 succeed"用 `pollUntilSucceedOn(..., onSuccess)` (如农田清区域); 两阶段用 `pollUntilThen(helper, 阶段①谓词, 摆场景回调, 阶段②谓词, onSecondOk, 窗口①, 窗口②, ...)`。**窗口取值沿用原墙点** ⇒ 最坏耗时不变。`runAfterDelay` 只允许用于**确定性时间门** (如缓存 TTL 过期: 放块不触发失效只能等 200t)。 |
**J** | **两阶段轮询的铁律 (实测踩过)** | ① **阶段①的谓词不得带副作用** (摆场景/改状态一律放 `onFirstOk` 回调里, 否则"判定"与"改变被测状态"混在一起); ② **阶段①的谓词必须从起始 tick 起就可只读判定** (信号源若读的是"管线尚未写入的内存态", 轮询会把它提前到前提条件之前 ⇒ 假红, 实测一次: 石头铺在区块认领之前 ⇒ `assigned=0`); ③ 阶段①在 t=0 即成立由 `pollUntilThen` 内置保险直接 fail 并指名道姓 (见该函数注释); ④ 阶段②必须**下一 tick 起** (同 tick 复用刚成立的阶段①语义 = 秒过)。 |
**K** | **`helper.succeedWhen(...)` 在 1.20.1 是"一次性求值", 不是"重试直到成立"** | 源码实证 (`forge-1.20.1-47.4.16-sources.jar`): `succeedWhen` → `createSequence().thenWaitUntil(run).thenSucceed()`, 而 `GameTestSequence.tick()` 里 `e.assertion.run(); iterator.remove();` = **跑一次就出队** (抛 `GameTestAssertException` 时被 `tickAndContinue` 静默吞掉, 事件已出队 ⇒ 不重跑)。⇒ **不要**用 `succeedWhen` 做条件等待 (会一次判定 + 最终超时), 用陷阱 I 的轮询家族。Create 的 `succeedWhen+assertSecondsPassed` 写法靠"抛异常=真失败"而非重试, 不可照抄。 |
**L** | **`!entity.isAlive()` 不能当"被打死"的判据** | 1.20.1 `Entity.isAlive() = !isRemoved()` (`Entity.java:1835`) ⇒ **任何原因移除实体都会让它为真** ⇒ 断言 "目标 !isAlive ⇒ 通过" 会**白过** (实测: 塔 `FIRED=0`/箭余未动, 僵尸被移除却 green)。正解 = 断言**被测行为本身**: 塔用例必须同时验"**耗了箭** (弹药减少) + 目标死亡"; 涉及实体时优先用 `setPersistenceRequired()` 防被移除, 并用 `level.getEntity(uuid) == ent` 复核"还在世界上"。|
**M** | **结构内天然怪会抢塔的目标 + 靶子可能被静默移除** | 塔选目标 = "半径内 + LOS 可见 + **水平最近**" (`DefenseGarageKitBlockEntity#towerTick`) ⇒ 结构内更近的野生 creeper/spider 会把远处靶子挤掉; 实测还会有"服务端 `Can't keep up!` 期间靶子 `isRemoved()=true` 血还满"的静默移除。三招固定夹具: ① **下半砖铺满地板** (`SMOOTH_STONE_SLAB`+`SlabType.BOTTOM` — 源码实证 `SupportType.FULL.isSupporting` 等价 `Block.isFaceFull(..., UP)`, 半砖非全高 ⇒ 不刷怪) ② **关本世界 `RULE_DOMOBSPAWNING`** (`ServerChunkCache:349` 门控整块 spawnAndTick) ③ 清结构 AABB 内 `Enemy` (**务必 `!= 测试靶子`**, 首版把靶子一起清了 ⇒ 塔无目标)。|
**N** | **`pollUntil` 的 `what` 文案是"调用时快照"** | `what` 是 String ⇒ 里面拼的数值 (`箭余 20→20, hp=20.0`) 在**调用瞬间**求值, 不是达成时值 (实测达成日志显示 20→20/hp20 而真实是箭余 18/僵尸已死) ⇒ **判定以 `达成: … 用时=Nt` 那行为准**; 别在 what 里写会变化的数值当证据 (要证据就在谓词里打日志)。|
**O** | **TLM 半径三件套: 搜索半径 / 寻路限制 / `SchedulePos` 40t 拉回** (错题 **#255 / #261**, 2026-09-21 实测踩到) | **源码实证 (TLM 本地源码, 逐条标行)**:<br>① `EntityMaid.getRestrictRadius()` = 实体数据字段 `RESTRICT_RADIUS` (`EntityMaid.java:2136-2138`), 取值由 `SchedulePos.restrictTo()` 按活动写入 (`SchedulePos.java:97-115`: **开头 `if (!isHomeModeEnable()) return;`** ⇒ **home 关 = 限制不生效**; WORK⇒`MAID_WORK_RANGE` 默认 12/上限 64, IDLE⇒`MAID_IDLE_RANGE` 默认 6/上限 32)。<br>② `IMaidTask.searchRadius()` = `getRestrictRadius()` (`IMaidTask.java:244-247`) ⇒ 决定 `searchDimension()` 的**实体/材料搜索盒** (`225-235`: 有 home ⇒ 以 `restrictCenter` 为心; 无 home ⇒ 以**女仆自身**为心) ⇒ 目标超出该盒 = 任务"看不到它" ✗。<br>③ `MaidNodeEvaluator.getMaidBlockPathTypeRaw` (`MaidNodeEvaluator.java:108-111`): `isWithinRestriction() && !isWithinRestriction(pos)` ⇒ **BLOCKED** ⇒ 限制外的格算不出路 ⇒ 擦 WALK_TARGET —— ⚠ **仅 home 模式开启时生效** (无限制时 `isWithinRestriction` 恒 true, `EntityMaid.java:2117-2121` ⇒ 该分支不触发)。<br>④ `SchedulePos.tick` (`SchedulePos.java:56-57`) **每 40t** (`tickCount % 40 == 0`) 重设限制 + `setWalkAndLookTargetMemories` ⇒ **会重写 WALK_TARGET 把女仆往回带** ✗。<br>**血例**: `lmaChainOreNearBeforeFar` 远矿 15 格 ⇒ 她走到近矿后**卡在距远矿 6.0 格**至 1101t 超时 (探针 `有Home=false 半径=8.0 中心=-`)。⚠ **归因边界 (诚实标注)**: 该探针显示 home=**false** ⇒ **不是** ③ 的 BLOCKED 分支; 属 ④ 的 `SchedulePos` 体系 ⇒ 已证到"半径/SchedulePos 层", **具体触发链未逐帧证** ✗ (下次再遇由夹具自证抓)。<br>**夹具铁律**: ① 目标可能超出默认半径 (≈6~12) 时, 先 `setHomeModeEnable(false)` + **`clearRestriction()`** (= `SchedulePos.clear`, `SchedulePos.java:139-145`) ② 再**自证**: `hasRestriction() && !isWithinRestriction(target)` ⇒ 当场 `fail` 打印半径 (别靠"跑满超时"表达 ✗)。上游: [TouhouLittleMaid](https://github.com/TartaricAcid/TouhouLittleMaid) |
**P** | **目标必然出结构的用例 = 冲突域, 必须独占 batch** (2026-09-21 建立) | 结构只有 **16³** ⇒ 需要"≥16 格 / 跨区块"的用例**数学上装不下** (女仆起点常在局部 7, 加 16 = 局部 23 ✗) ⇒ 她**必然走出本结构**。此时: ① 落进邻居结构盒 ⇒ 邻居 `succeed()` 的 `getStructureBounds().inflate(1)` 清场把她 **discard** (UUID 级取证: 框架帧 `GameTestInfo.lambda$succeed$6`) ② 也依赖邻居地形/被邻居目标抢占。**规则**: 这类用例**每条独占 `z_<域>_<名>` batch** (现有 10 条: `z_chainore_*` 6 / `z_void_*` 3 / `z_damfill_wall` 1), 且夹具自带地板/垫层与自证; 普通结构内用例**共享 batch 不动** (实测耗时: 改前 ~150s ⇒ 改后 161/170/176/181/187/215s, 即 **+7%~+43%, 中位 ~+18%**)。|

## 四、新增用例 checklist
1. **先判能不能测**: 服务端可观测的才写 (渲染/鼠标拖拽测不到, 写"服务端对偶")。
2. **靶子专用 + 目标 >10 格 + 不注入内部记忆**。
3. **要并发隔离时**给独立 `batch`; 结束时清理自己造的东西 (实体/方块)。
4. **失败信息要能自证**: 打印关键状态 (数量/存活/距离), 让人一眼分清夹具/被测对象。
5. **AI 完成类一律条件轮询** (陷阱 I), **禁固定 tick 断言**; `runAfterDelay` 只作确定性时间门。
6. **跑两轮**确认不是偶发 (同轮判定看"失败集合是否稳定", 见陷阱 G)。

## 五、已知 flaky (截至 2026-09-20: **本轮 6 轮全绿 ⇒ 家族清空**)

> 共同特征 = **"限时等 AI 完成"的固定 tick 断言** (陷阱 I)。错题 #270 记载"全绿是一次采样, 不是属性"。
> **⚠ 2026-09-20 重要结案**: 连跑 **双平台各 3 轮 = 6/6 全绿**(neoforge 66~201s · forge 150~175s) ⇒
> 此前登记的多条 flake (`lmaChainOreNeighbourChunk` / `lmaVoidExcavationMultiMaid` / `lmaMlgRescue` /
> `lmaFurnaceSmokerType` / `lmaBrushNothing` / `lmaChainOreNearBeforeFar` …) **均已随同一根因修复消失**：
> **`EntityLeaveLevelEvent` 对活女仆也会触发 ⇒ `MaidIndex` 被误除名 ⇒ 心跳不 tick 她 ⇒ "任务不动/原地"**
> (v79.66p 修: 只在 `isRemoved()||!isAlive()` 时真除名; 并把"在册"从日志**升级为硬断言**)
> ⇒ **新口径**: 若再复现"女仆不动/任务不推进"类红轮, **先看 `[GAMETEST-NBCHUNK-DBG] … 在册=`** ✓
> (对照: 修前现场 `在册=false 任务=collect_ore BFS可达=true 能动=true`)。

| 用例 | 观测到的失败语义 | 状态 |
|---|---|---|
| `lmaVoidExcavationMultiMaid` | `assigned=0` / "560t 没挖掉任何认领区块的石头 (挖掘未推进)" | ✅ **已收口** (2026-09-19): 保留 60t **确定性认领门**(铺石头) + 挖掘阶段改 `pollUntilSucceedOn`(至少 1 块被挖, 窗口 500t, watch 传全部石块 ⇒ 超时自动打每块状态) ⇒ 双平台 `达成 60t` + `final assigned=4 dug=4/4` ✓。**教训**: 曾把"记日志/校验/取消/succeed"的**收尾写在 pollUntil 之后** ⇒ 谓词未达成时也会执行到 succeed ⇒ **轮询回调永不再跑 = 白过**(`dug=0/4` 却绿) ✗ — 收尾必须放成功回调内 |
| `lmaDefenseTowerFarRange` | "僵尸中箭但未死 (hp=14.096)" — 语义具体 ⇒ 按真缺陷查 | ✅ **已修复** (2026-09-19): 根因 = 靶子被静默移除 (`isRemoved()`, 血还满) + 结构内野生怪抢目标; 修法 = 下半砖铺地(陷阱 M-①) + 关 `doMobSpawning` + 清框内 Enemy(排除靶子) + 靶子 `setNoAi(true)`/`setPersistenceRequired()` + 断言"耗箭 + 死亡"(陷阱 L) ⇒ 双平台 ×2 轮稳定 `用时=80t` 达成; 详见 `docs/reference/gametest-flaky-tower-investigation.md` |
| `lmaMlgRescue` | "摔落受伤 19.0/20.0 (MLG 倒水未生效)" | ✅ **已改造** (2026-09-19): 探针证实**倒水确实发生**(t=21 换桶/t=22 入水/t=23 落地), 但 ~1/6 轮次落地后仍有小额伤害 ⇒ 原断言 "hp 一点都不许掉" 把真自救误判为失败 ✗; 改为 `pollUntil`(落地 + 未摔死 hp>起-6) + 补 `timeoutTicks=200`(原默认 100t 小于窗口 = 我的锅); 窗口内 40t 稳定达成 ✓ |
| `lmaFurnaceSmokerType` | "烟熏炉输入槽未放入生牛肉 (SMOKING 分支未生效)" | ✅ **已改造** (2026-09-19): 80t 固定墙点 → `pollUntil` 入料即过 (窗口 300t); 实测 40–60t 达成 ✓ |
| `lmaBrushNothing` | "无目标不应耗耐久 dmg=1" / "普通沙砾不应产生考古掉落" / "普通沙砾不应被改变" | ✅ **已改造** (2026-09-19): 判据是"**持续**什么都不该发生" ⇒ 不能用"成立即过"的轮询(会秒过) ⇒ 改**观测窗口 + 到点总结**(t=10..100 每 10t 记违规, 110t 汇总) ✓ |
| `lmaChainOreNearBeforeFar` | 1100t 两块矿都没挖、女仆离矿 19 格 | ✅ **已修复 (根因实测)**: 夹具**清场走廊 (dx -3..20) 覆盖不到扫描半径** ⇒ 走廊外的矿被"最近优先"抢走 (现场: t=2 距 10 → t=40 距 13 → t=200 距 18 卡死, `[ORE-DBG] navigate` 指向走廊外另一格) ⇒ 改为**半径 20 球清场只留两块目标矿** (同 `lmaChainOreNeighbourChunk` 既有模式) ✓ 双平台复验 140t 达成 |
| `lmaChainOreWallDropCollect` | "墙上矿没被挖掉 / 掉落物收不到" | ✅ **已修复 (同类根因)**: 原不清场 ⇒ 野矿抢目标; 加 `clearStrayOres(半径 20, 留柱顶矿)` + 固定 600t 窗口 → `pollUntilSucceedOn`(矿物删除 ∧ (已拾取 ∨ 落道已清)) ✓ neoforge 复验绿 |
| `lmaChainOreNeighbourChunk` | 长时间 `[ORE-TIER] 命中=无` 后超时 (女仆原地) | ✅ **已修复 (边界实测)**: 夹具把矿放在**下一区块第 1 格 = 水平 16 格** ⇒ 3D d²=256 **恰好卡在远扫球边界** (`NearestBlockSearch` 按 `k ≤ r²` 迭代 3D d², `RADIUS_FAR=16`) ⇒ 女仆**偏 1 格或高低差 1 格**即 d²>256 ⇒ **目标直接不可见** ⇒ 卡死 ✗。改为**下一区块第 0 格 (15 格, 留 1 格余量)** — 仍是"隔壁区块第一格"语义 ✓ ⇒ forge 80t 达成、双平台绿 ✓ |
| 定点窗口已全部转换 ✅ | `lmaFarmJob`(300→门 200t + 轮询) · `lmaFarmHarvestBox`(300→轮询, 违禁品即刻判红) · `lmaFarmSugarCaneTop`(600→轮询"顶端被收 ∧ 基部保留") · `lmaFarmMelonNoFalseHarvest`(300→**观测窗口** `observeWindow`) · `lmaChainOreSameChunk12`(600→轮询) | ✅ 2026-09-19 完成, 双平台 118/118 |

> **`observeWindow(helper, ticks, step, probe, onDone)`** — 治"**持续**什么都不该发生"类断言 (如"西瓜不该被收")
> 的**观测窗口**助手: 每 `step` 跑一次 `probe` (违规时 probe 内直接 `helper.fail` ⇒ 立刻判红、语义精确),
> 到 `ticks` 仍无违规才 `onDone` 收尾成功。**不能**用"成立即过"的 `pollUntil` (t=0 就成立 ⇒ 秒过 = 没测) ✗。
>
> ⚠️ **`pollUntil` / `pollUntilSucceedOn` / `pollUntilThen` 之后不要写"收尾逻辑"** ✗ —— 轮询是**异步**的
> (未达成时仅排下一次回调就返回), 后续语句会**提前执行** ⇒ 一旦含 `succeed()` 就是**白过** (回调永不再跑) ✗。
> 收尾 (记日志/校验/清理/`succeed`) 一律放进 `pollUntilSucceedOn` 的 `onSuccess` 回调或 `pollUntilThen` 的 `onSecondOk` ✓。

> **采矿夹具铁律 (2026-09-19 实测得出)**: 清场范围**必须覆盖扫描半径**（`NearestBlockSearch.RADIUS_FAR = 16` /
> 中扫 10 / 近扫 4 — 见 `task/service/harvest/README.md` 与 `vanilla/input/search/README.md`），否则**最近优先**
> 会选中场地外的野矿 ⇒ 女仆跑去挖别人的矿、超时假红。做法: 用 `clearStrayOres(helper, center, 20, 保留目标...)`
> （只替换矿石格、不动地形）。

## 六、耗时构成与优化边界 (2026-09-20 实测, 结论: **不再追 ≤60s**)

**forge 整轮 ~150s 的构成** (以单轮日志跨度 132s 为口径, 另有 gradle 前后 ~20-40s):

| 段 | 耗时 | 说明 |
|---|---|---|
| 启动 | **52s (硬成本)** | ModLauncher + Forge 加载 + Minecraft 启动 + **gametest 世界创建** (跑前清 `run/gametest` ⇒ 全量重生成) |
| 批内 | **~80s** | **11 个批串行**; 每批耗时 = 批内**最慢**用例 + 批固定开销 (结构放置/世界重置/结算) |
| 收尾 | ~6s | 汇总/退出 |

* **批内并行**: 同一 `batch` 的用例**并行**跑 ⇒ 该批成本 ≈ **批内最慢一条** (77 例的 `defaultBatch` 仅 19~25s ⇒ 不是"77 条相加")
* **长杆是"批数多且串行"**: 6 个 `z_tower*` 批合计 37s, 而其中塔用例自身仅 ~4s/条 ⇒ **~13s 是批固定开销**

### ⛔ 两条硬边界 (勿再重复尝试)

1. **forge `≤60s` 不可达** — 仅**启动就 52s** (JVM+Forge+MC+世界创建), 加最小批开销 ⇒ 现实下限 **~110-130s**。
   唯一能省启动手段 = 保留 `run/gametest` 世界 (只清结构区, 省 20-30s), 但**违反"跑前清世界"纪律**且易脏状态假红 ✗ ⇒ 已裁定**不做**。
2. **塔用例必须独占批** ✗ (2026-09-20 实验): 把 6 处 `batch="z_towerN"` 合并成 `z_tower` ⇒ 批数 11→6 ✓ 但 **forge 立刻红**:
   `lmadefensetowerdurability failed! 弓未掉耐久 (damage=0, 箭余=32) [夹具: 目标存活=0/3]` ·
   `lmadefensetowerfarrange failed! 超时未达成 … (箭余 20→20)`
   ⇒ **根因**: 塔索敌半径 **32 格 > 结构间距** ⇒ 同批并行的**邻居测试塔把本测试靶子打死了** ⇒ 靶子归零、塔不开火 ✗。
   **结论: 凡"半径 > 结构间距"的索敌/影响类用例, 必须单独成批** (实验后已回退, 复验 120/120 ✓)。

> **耗时目标口径**: 不再追 forge ≤60s; 以 **neoforge ≤~200s / forge ≤~200s 全绿** 为验收 (当前 neoforge 66~201s · forge 125~175s)。
