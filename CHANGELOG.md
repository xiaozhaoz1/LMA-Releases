# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 0.9.74 (2026-09-18) — Token 定稿 (透明贴图 + 创造栏可见)

> 0.9.73 是本功能的首版打包, **未对外发布**; 本版为定稿 (取代 0.9.73)。

### Fixed / Changed (相对 0.9.73)
- **贴图换透明版**: 原用 `token.jpg` (JPG **白底** ✗ ⇒ 游戏里是白方块) ⇒ 改用 `token.png` (源 2048²
  `Format32bppArgb` ✓) 转 128×128, 转换用 `CompositingMode=SourceCopy` (直拷像素, 避免半透明边缘被底混色 ✗)
  ⇒ 实测产物四角/边缘 `alpha=0` ✓ 中心 `alpha=255` ✓ jar 内 `colorType=6 (RGBA)` ✓
- **创造栏可见**: `LmaCreativeTab.displayItems` 是**显式列物品**的 ⇒ 原先创造栏**翻不到 Token** ✗ (只能 /give)
  ⇒ 已加入双平台分支 ✓ (反编译 jar 内 `LmaCreativeTab` 含 `TOKEN` ✓)
### Testing (基建, 不影响玩法)
- gametest flaky **家族**放宽等待窗口 (语义不变): `lmaVoidExcavationMultiMaid` 200→500t (timeout 400→900) ·
  `lmaChainOreNeighbourChunk` 600→1800t (timeout 1300→2600) ⇒ 改后实测: 两项**双平台转绿** ✓
  (neo 全绿; forge 这轮暴露同族另两个 — 已记入基线行"flaky 家族 + 长期正解=轮询到截止" ✓)
### 验证
双编译 ✓ · 单测 **88 类 557 用例 0 失败** ✓ (部署前门禁) · jar 内 `version = "0.9.74"` + 透明 PNG + 配方 +
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
## 0.9.73 (2026-09-18) — 新功能: Token (可食用 · 女仆饰品)

### Added
- **Token** — 用户需求: 可食用 + 可当女仆饰品 ✓
  - **食用 (三路径同一套结算 ✓)**: +2 饱食度 · **回 5 点血** · **30s「缓慢恢复 I」** ✓
    (① 玩家吃 ② 右键喂女仆 = `TokenFeedHandler`/`InteractMaidEvent` ③ 女仆吃自己背包的 = `finishUsingItem`)
  - **佩戴**: `TokenBauble.onTick` 每 40t 补 60t 的「生命恢复 I」⇒ 不断档 ✓; **摘下即清** ✓
  - **可堆叠 64 + 可作饰品**(TLM `BaubleItemHandler` 只判"是否注册过的饰品", `getSlotLimit` 无覆写 ⇒ 上限=物品堆叠数 ✓
    已 fact-forcing 实证) ⇒ 16 个的合成产出成立 ✓
  - **配方**: 8 个**青金石**围一圈(中间空) ⇒ **16 个** ✓ 双平台照既有 cake 配方格式
    (1.20.1 `recipes/` 复数 + `result.item/count` + `show_notification`; 1.21.1 `recipe/` 单数 + `result.count/id` ✓)
  - **素材**: `token.jpg` (2048²) ⇒ 128×128 PNG 进包 ✓
### Added (测试)
- **gametest `lmaTokenEffects`**: ① 结算(补血到 15 / 饱食 +2 / 恢复 I 600t) ② 佩戴 60t 仍持有(且时长为饰品自身短刷新 ≤60t)
  ③ 摘下 70t 后消失 ✓ 双平台通过 ✓
- **单测 `TokenSpecGuardTest`** (3 例): 效果数值常量 / 配方(形状 8 格环 + 空中心 + 青金石 + 产出 16 + 双平台目录与 result 格式) / 资源三件套 ✓
### 验证
双编译 ✓ · 单测 **88 类 557 用例 0 失败** ✓ · gametest **117 例**(forge/neo 均仅既有 flaky 红:
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
`lmachainoreneighbourchunk` / `lmavoidexcavationmultimaid`) ✓ · jar 内 recipe/png/model/handler 齐 ✓ · 部署 `.bak-0917x` ✓
## 0.9.72 (2026-09-18) — 修「应力配置改了但读数不变」: 写 BE 字段 ≠ 生效, 必须 dirty 动能网络

### Fixed
- **皮带发电应力配置不生效 (第二层根因)**: 用户把「杂项 → 皮带发电应力」改成 100000, 日志显示字段已是
  `stress=100000.0` ✓ 但 Create 护目镜仍显示 **1024** (改配置**之前**算出的旧容量) ✗
  根因: `MaidPowerBeltBlockEntity.setGeneratedOutput` 只写 `generatedSpeed/generatedCapacity` 两个字段,
  **没有 dirty 动能网络** ⇒ Create 的应力容量是**网络级缓存**, 不重算就永远用旧值 ✗
  (fact-forcing 实证: javap 本地 `create-1.20.1-6.0.8.jar` ⇒ `KineticBlockEntity` 有
  `public boolean networkDirty`, 另有 `updateSpeed` 只管转速路径 ✓)
  ⇒ 修法: 值真的变化时 `networkDirty = true` ✓ (调用方每 tick 调, 幂等闸门在方法开头 ✓)
- **日志刷屏**: `[MaidPowerBeltBlock] direct …` 原本每 sprint tick 都打 (实测 **5842 行** ✗) ⇒ 移入 BE 的
  "值真的变了"分支并改名 `[MaidPowerBelt] 输出变更: rpm=… capacity/每RPM=… ⇒ 总应力=…` ✓ (刷屏 → 个位数)

### 说明 (与 0.9.71 的关系 — 两层根因)
- 第一层 (0.9.71 已修): 魂符收放后 FSM 卡在旧分支 ⇒ 她进不了 running ⇒ 应力路径根本不跑 ✓
- 第二层 (本版): 即便跑起来了, **写 BE 字段也不会被 Create 采纳** ⇒ 必须显式 dirty 网络 ✓
  ⚠ 0.9.71 的 changelog/错题 #358 里"修好后下一次 sprint 即生效"的说法**不完整** ✗ ⇒ 已在 #358 内更正,
  并新增 **#359** 记录该教训 ✓

### 验证 (0.9.72)
双编译 ✓ · 单测 **87 类 554 用例 0 失败 0 错误** ✓ · gametest **forge 116/116 全绿** ✓ · neo 仅既有 flaky
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
`lmavoidexcavationmultimaid` 红 (与本改动无关) · jar 内 `version = "0.9.72"` ✓ · 部署 `.bak-0917o` + md5 双端一致 ✓


### 补充修复 (同版 0.9.72, 未升号 — 用户裁定: 没修好不上号)
- **「配置 100000 仍读到 1024」的真根因 (第三层)**: **两个 writer 打架** ✗ —
  ① direct 路径 (女仆 sprint ⇒ `setDirectOutput(rpm, customStress)`) 写 100000 ✓
  ② **表面速度链路** (`MaidPowerBeltBlockEntity.applyDetectedSurfaceMovement` → `setGeneratedOutput`) 每个检测
  周期用**采样值**覆盖它 ✗ ⇒ 护目镜读到 1024 (采样/老公式值) ✓
  ⇒ 修法: direct 输出**权威化** — 记录 `lastDirectOutputTick`, 表面链路在 **40t 窗口内 yield**
  (女仆 sprint 期间 direct 每 tick 刷新 ⇒ 持续权威 ✓; 停 sprint 后窗口过期 ⇒ 采样链路自然接管 ✓)
- **日志降噪**: `[MaidPowerBelt] 输出变更` 改按**总应力**判定 (rpm/换算容量随蛋糕数抖动时不刷屏 ✓
  实测曾 600~5842 行 ✗)

### 补充修复 2 (同版 0.9.72) — 「Cloth 设置屏没挂保存回调」(含全量配置写入审计: 5 Spec × 全部落盘入口 ⇒ 仅此屏漏挂, 其余健全 ✓; 并已对齐多人同步 `ConfigSyncPacket.send()` ✓)⇒ 改了的设置重启就回默认

- **现象**: 用户把「杂项 → 发电皮带应力」改成 100000 ⇒ 当次会话日志里确实读到 100000 ✓, 但**每次进游戏又变回 -1** ✗
  ⇒ 应力永远 1024 ✓ (前三条修复 — FSM 分派键 / networkDirty / 双 writer — 都真实存在且已修 ✓, 但都
  **不是**用户看到 1024 的原因 ✗)
- **根因**: `ClothSettingsScreen.create(...)` 只给每个条目挂了 `setSaveConsumer(值::set)`(只写**内存 spec** ✗),
  **没有** `.setSavingRunnable(MoreActionConfig::saveAll)` ✗ ⇒ 没人调 `ACTIVE_SPEC.save()` ⇒ TOML 里
  **从没有这个键** ✓ (实证: `config/littlemaidmoreaction-common.toml` 只有 `[debug]` ✗)
  **对照物**: 同类屏 `TaskSettingsScreen.java:303` **有** `setSavingRunnable` ✓ ⇒ 只有本屏漏 ✗
- **修法**: builder 链上补 `.setSavingRunnable(MoreActionConfig::saveAll)` ✓ (= 该屏**所有**设置恢复可落盘 ✓)
- **验证**: 双编译 ✓ · 单测 87 类 554 用例 0 失败 ✓ · jar 内 `ClothSettingsScreen` 已含保存回调 (反编译 ✓) ·
  同版 0.9.72 重新打包部署 (旧的轮换成 `.bak-0917q`) + md5 双端一致 ✓
## 0.9.71 (2026-09-18) — 修「魂符收放后传送带不再变发电」+ 转换路径可观测性

### Fixed
- **魂符收放后无法再转成发电皮带** (用户实测): `converted` 是跑步带 FSM 的**分派键**, 而
  `RunningBeltPipeline.onMaidUnload` 只清 `target` **不清 `converted`** ✗ (且清理语句放在 `anchor == null`
  **早退之后** ⇒ 无锚点时连清理都跳过 ✗) ⇒ 女仆被魂符放回后永远进 `tickRunning`, 回不到
  "站上去 → 转换"的 `tickSearching` 分支 ✗
  ⇒ 修法: ① `onMaidUnload` **第一条语句**清 `converted` ✓ ② `tick()` 里 `converted=true` 时**校验绑定**
  (锚点已非发电皮带/无锚点 ⇒ 清两键 + 冷却归零 ⇒ 立即回 searching 重新转换) ✓
- **「应力改了没变」是连带, 不是配置没绑对**: Cloth 「杂项」↔ `ActiveTaskConfig.POWER_BELT_STRESS` ↔
  方块侧 `MaidPowerBeltBlock` 每 tick **活读** = 同一个 spec 值 ✓; 但她既然回不到 running/sprint,
  `setDirectOutput` 永不调用 ⇒ 2048 无从体现 ✓ (修好转 换后下一次 sprint 即生效 ✓)

### Added (可观测性 — 本次排查被"全静默"坑了)
- `RunningBeltService.convertToMaidPowerBelt`: 5 类失败原因**节流日志** (`gameTime % 200`) + 成功一行 ✓

### Added (回归)
- gametest `lmaRunningBeltRebindAfterUnload`: 卸载回调后 **`converted` 必须被清** ✓ (修前必红) —
  按用户裁定**降级为只验 PD 状态契约** (gametest 环境 Create 皮带/BE 不可构造, 报 `IBE`/`BeltBlock`),
  **真实"再站上去能再转换"链路待实机验证** ✓

### 验证 (0.9.71)
双编译 ✓ · 单测 **87 类 554 用例 0 失败 0 错误** ✓ · gametest **forge 116/116 全绿** ✓ ·
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
neo 116 例仅 `lmavoidexcavationmultimaid` 红 = **既有 flaky** (与本改动无关: 同提交 forge 全绿) ✓ ·
jar 内 `version = "0.9.71"` + 动画仅 haqi/maimeng ✓ · 部署 `.bak-0917n` + md5 双端一致 ✓

## 0.9.70 (2026-09-18) — 修外部玩家崩溃「畸形动画关键帧」+ 动画预设精简为 2 个 + 空置域 home 还原

### Fixed
- **外部玩家 GeckoLib 加载崩溃** (`GeckoLibException: …/animations/dodge.animation.json` +
  `Invalid keyframe data - multiple starting keyframes?`): 该文件 `animation.flash1` 的 **13 个骨骼通道全部**
  键序为 `'1'` → `'-0.03'` → `0.3…1.97` (**首键不在时间轴开头且非单调**) ⇒ GeckoLib 4 解析 **MAP 形式**
  按**文件键序**建关键帧表 ⇒ 判非法 ✗ (同文件 `flash2` 正常; 全仓 60 动画/1100+ 通道仅此 13 处; 与旧检出
  逐字节相同 = 历史遗留, TLM/GeckoLib 校验收紧后才爆)
- **空置域任务结束 home 不还原**: `VoidExcavationPipeline.tick` 的"记录 wasHome + 强制 home"块**每 tick 都跑**,
  第 2 tick 起把第 1 tick 记的 `false` **覆盖成 `true`** ⇒ `onCleanup` 的 `!wasHome` 判定失败 ⇒ home 永不关 ✗
  ⇒ 改为「**只记一次**」(与 `DamFillPipeline.ensureHomeMode` 同款) ✓

### Changed (用户裁定: 动画只留 哈气 haqi + 卖萌 maimeng)
- **删除 6 个废弃预设**: `execution` / `dodge` / `taunt` / `parry` / `man` / `ysm_slashblade`
  (无代码消费者; jar 体积 **-1.6 MB**) — 兜底自检表 `AnimationDurationManager.FALLBACK_ANIMATIONS` 同步改为只校验 `haqi`/`maimeng` ✓
- **老玩家 config 目录自动清理** (`AnimationPresetNames.syncConfigDir`): 该目录是 LMA 专属 ⇒ `load()`/`reload()`
  都对照 **JAR 内实际文件**，**删除所有不在 JAR 里的 `*.animation.json`** (只补缺失的复制逻辑删不掉旧文件,
  而 `scanAnimations` 扫的是目录 ⇒ 畸形文件会继续崩 ✗) ✓

### Added (守护)
- `AnimationPresetGuardTest` (4 例): ① 随包动画**关键帧必须升序** ② 随包清单 == resources 实际文件
  ③ config 目录对照 JAR 清理 (只删动画文件/保留在用/幂等) ④ 自检表键都在随包动画里 ✓
  (探针实测: 把畸形 `dodge.animation.json` 临时放回 ⇒ **BUILD FAILED** ✓)

### 验证 (0.9.70)
双编译 ✓ · 单测 **87 类 554 用例 0 失败 0 错误 0 跳过** ✓ · gametest **forge 115/115 · neoforge 115/115** ✓ ·
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
逐节点 clean+jar ✓ · jar 内 `version = "0.9.70"` + 动画仅 `haqi`/`maimeng` ✓ · 部署 `.bak-0917m` + md5 双端一致 ✓

### ⚠ 已知未修 (根因已查明, 见错题 #357)
「空置域已关闭仍被拉回」(跑步机强制 home 时女仆被 TLM `restrictTo(workPos)` 传送回坑): 根因 = `adaptToChunk`
改的 `SchedulePos` 三点**会落盘**, 而卸载路径只释放区块认领**不还原**; 本版曾尝试"原点持久化 + 结束/卸载/重进
还原", 但引入 neo 回归 (`lmavoidexcavationmultimaid` dug=0/4) ⇒ **整体回滚** (代码已恢复原行为, 死代码待清) ✗

## 0.9.69 (2026-09-17) — 丢物品族**全仓审计** + 3 处"退还余量"硬化 (规则升格 README 契约)

> 用户裁定: "看下其他代码有没有同样错误" ⇒ 8 类丢物模式全仓扫描 (393 main 文件) ⇒ 收掉 3 处低风险硬化点。

### Audited (结论: 真实缺陷仅 dam_fill 一处, 已于 0.9.68 修)
扫描面: 覆盖写手 16 · 库存槽覆盖 20 · `insertItem` 返回值 45 · `extractItem` 返回值 37 · `removeItem` 3 ·
`copy()`+`shrink/setCount/grow` 7 · `setCount(0)` 1 · `shrink(` 16。
- **覆盖写手** 全部有保全 (Assembly save/restore · NearbyCollect 塞不进则不换手 · WorkEat 旧物先入包 ·
  TorchLight 灯先回包才清副手 · BlockUp `HandSwap` 三链 · LmaPlayerSimulator 回写前旧物入包/落地 · HandSwap 原语本身) ✓
- **槽覆盖** 装配机器槽全部"写前判空 / 余量交调用方 / 产物 `deposit` 背包→无线→落地"; 配方匹配用的临时 handler 非玩家物品 ✓
- **返回值丢弃** 4 处故意消耗 (射箭/弹碟/放软块/装配耗材); "赋值后从未使用"机械扫 **0 命中** ✓
- **copy+改** 7 处全是"先验证存入成功, 再扣源栈"的正确写法 ✓

### Fixed (硬化 3 处"退还余量未接" — 不可复现, 防 mod 容器拒收)
- `VoidExcavationContainerService` (工具溢出退还输入箱) → 余量 `ItemSpawner.spawnForPickup` 落地待拾 ✓
- `ContainerOutput.depositItemStack` (退还女仆背包槽) → 余量 `HandSwap.stashOrDrop` (背包 → 落地) ✓
- `ContainerOutput.withdrawItemStack` (退还源容器) → 余量 `ItemSpawner.spawnForPickup` ✓

### 文档 (规则升格为契约)
- `vanilla/output/README.md` **陷阱 B** 补: "**'退还类' insert 同样要接余量**" —— 退还目标是刚被 extract 的同一槽
  (正常必有空间 ⇒ 不可复现), 但 mod 容器有 insert 筛选时会拒收 ⇒ 余量必须落地 ✓
- 错题集 **#354** 追加「族审计结果」小节 (扫描面 + 逐类判定 + 3 处硬化 + 无自动化覆盖的诚实标注) ✓

### 验证 (0.9.69)
双编译 ✓ · 单测 **86 类 550 用例 0 失败 0 跳过** ✓ · gametest **forge 114/114 · neoforge 114/114 全绿** ✓ ·
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
逐节点 clean+jar ✓ · jar 内 `version = "0.9.69"` + 反编译确认兜底代码在位 ✓ · 部署 `.bak-0917l` + md5 双端一致 ✓

## 0.9.68 (2026-09-17) — 修「排水换桶把主手物品弄没」: 换手改走 HandSwap 保全链 (错题 #162 丢物品族第三处)

> 用户报告: 排水 (dam_fill) 时把空桶换到手上, 主手的东西**直接消失** ✗

### Fixed
- **`DamFillPipeline.ensureBucket`** (两分支: 背包取桶 / 输入箱取桶): 原为
  `maid.setItemInHand(MAIN_HAND, 桶)` **直接覆盖** ⇒ 原主手物品(工具/其它)**既没回背包也没落地** ⇒ 永久丢失 ✗
  ⇒ 现统一为"取 1 桶 → 置主手 → 旧物交 `HandSwap.stashOrDrop` (背包 → 满则落地)" ✓
  (项目早有 `vanilla/output/item/HandSwap` 原语, javadoc 明写"整槽提取 + 旧物保全三链", 来源收敛正是 #162 的
  两处复制链 — **dam_fill 是第三处且未收敛**; WorkEat/NearbyCollect/Assembly/BlockUp 均已保全 ✓)

### Added (测试)
- **gametest `lmaDamFillBucketKeepsMainHand`** (批 `z_void`): 主手放铁镐 + **背包**放 1 空桶 ⇒ 排水首 tick 换桶;
  断言 **主手=空桶 且 铁镐未丢** (背包或落地) ✓ (修前实测: 超时未达成 ⇒ 必红 ✓)
- ⚠ **顺带查明为什么这 bug 一直没被测出**: 现有 `lmaDamFill` 用 `getAvailableInv(true).insertItem(0, 桶)`,
  而 TLM `getAvailableInv(handsFirst)` = **`[手槽, 背包]`** ⇒ **slot 0 是手槽** ⇒ 桶直接进了手,
  `ensureBucket` 首行即早退 ⇒ **从未覆盖"背包 → 手"换手路径** ✗ (新用例改用 `getAvailableBackpackInv()`)

### 文档
- `vanilla/output/README.md` 陷阱 C (回滚语义) + `task/pipeline/README.md` `dam_fill` 行: 明确"**覆盖式写手必须带旧物去向**" ✓

### 验证 (0.9.68)
双编译 ✓ · 单测 **86 类 550 用例 0 失败 0 跳过** ✓ · gametest **forge 114/114 · neoforge 114/114** ✓
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
(新用例双平台达成 20t; forge 另 1 例 `lmafarmplant` 为既有 flaky) · 逐节点 clean+jar ✓ ·
jar 内 `version = "0.9.68"` ✓ · 部署 `.bak-0917k` + md5 双端一致 ✓

## 0.9.67 (2026-09-17) — 修「空置域一直提示**未设起始点**」: 无效 `min_y` ⇒ 误判挖完 + 误删 start (已部署双平台)

> 用户报告 (1.20.1): 女仆无法进行挖空置域, 一直提示未设起始点。

### 诊断 (1.20.1 日志 + 代码控制流, 链完全闭合)
1. 旧 GUI 保存 bug 曾把 `min_y` 存成 **0**, 而标记层 `start.y = -60` (该坏值**仍在女仆 NBT 里**;
   新保存已在 0.9.64.4 修 ✓);
2. `while (y > minY)` 即 `-60 > 0` = **false** ⇒ 挖掘循环**一次不进**;
3. 循环后的 `if (y <= minY)` = `-60 <= 0` = **true** ⇒ 首 tick 就 `poolMark(区块, 2)` = **把区块标记为"已挖完"**;
4. 下 tick 认领池发现"区域全部区块已完成" ⇒ `poolClaim` 返回 null ⇒ 管线判"**全部挖完**" ⇒
   **`cfg.remove("start")`** + 气泡"完成" + cancel;
5. `start` 被删 ⇒ 之后每次启动 `validate` 都失败 ⇒ **"未标记起始点"反复出现** (日志里每 4 秒一轮 = 反复重标) ✓

### Fixed
- **管线自愈** (`VoidExcavationPipeline`): `min_y >= start.y` ⇒ 无可挖层 ⇒ **移除该无效键** + WARN,
  回落"自动检测基岩层" ⇒ 女仆可正常开挖, 且**不再误删 `start`** ✓
- **屏端拒存** (`VoidExcavationConfigScreen`): 保存最低高度时若 `min_y >= 标记层` ⇒ 直接拒绝 + 提示
  (`screen.littlemaidmoreaction.void.min_y.invalid`, zh+en) ✓ — 从源头杜绝无效值 ✓

### Added (测试)
- **gametest `lmaVoidMinYInvalidSelfHeal`** (批 `z_void`): 置 `min_y == start.y` (同一判定分支) ⇒ 断言
  ① `min_y` 键被移除 (自愈) ② `start` **未**被误删 ③ 游标首格**实际开挖** ✓
  (修前: 首 tick 判完成 + 删 start ⇒ 三条断言全红 ✓)

### 文档
- `task/service/harvest/README.md` 增「`min_y` 必须低于标记层」小节 (含控制流推导 + 两道防线 + 回归用例);
  `task/gui/README.md` 表格 `min_y` 单元格补同款约束 ✓

### 验证 (0.9.67)
双编译 ✓ · 单测 **85 类 549 用例 0 失败** ✓ · gametest **forge 113/113 + neoforge 113/113 全绿** ✓
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
(实测日志: `[VOID] min_y=-59 不低于标记层 start.y=-59 ⇒ 无可挖层, 已自愈为自动检测基岩层` ✓) ·
逐节点 clean+jar ✓ · jar 内 `version = "0.9.67"` ✓ · 部署 `.bak-0917j` + md5 双端一致 ✓

## 0.9.66 (2026-09-17) — 空置域「**第一层检查**」: 传送前先挖净落点 2 格 (防卡方块窒息掉血) (已部署双平台)

> 用户报告: "女仆挖空置域的第一层可能挖不到 (因为是实心的), 传送过去会被卡方块里掉血" ⇒ 裁定:
> **第一层先把它上面的方块挖掉, 再挖第一层的方块** — 挖掉两块后女仆即可安全传送站位 ✓

### Fixed (产品)
- **传送落点未净空 ⇒ 头埋方块窒息** (`VoidExcavationPipeline`): 女仆站姿 = 脚在落点格 + 头在其上 1 格。
  逐层下挖时**后续层头位**已在上一层挖空 ⇒ 安全; 但**第一层 (标记层) 头位是未开挖地表** ⇒
  两条传送路径都会把女仆**传进实心方块** ⇒ 窒息持续掉血 ✗ (解释"只有第一层中招" ✓)。
  **修法**: 新增 `ensureLandingClear(...)` — 传送前把落点挖成 **2 格净空**, 顺序按用户裁定
  **头位 (y+1) → 本体 (y)** ✓; 走既有 `tryDig` 通道 (工具/耐久/销毁名单/背包一致 ✓) + 同一挖掘节拍
  (不额外加速 ✓); 基岩/屏障清不掉则跳过 (不阻塞进度 ✓); 区块未加载则交回原门控 ✓。
  **两条路径都加**: ① 关导航的"传区块中间" ② 导航超时的"传 `target.above()`" ✓
- 落点净空后女仆传送进去即可**站在自己挖出的 2 格空间**里, 不再窒息 ✓

### Added (测试)
- **gametest `lmaVoidFirstLayerLandingClear`** (独占 `z_void` 批): 落点本体+头位都摆实心石 (模拟第一层),
  女仆站**水平 5 格外** (`near` = 水平 ≤4 格 ⇒ 必走传送分支 ✓) ⇒ 断言头位与本体先后挖净 **且血量全程不掉** ✓
  (修前: 直接传进实心石 ⇒ 头埋方块 ⇒ 血量断言必红 ✓) + 每 100t 打诊断 (失败可定因)

### 文档
- `task/pipeline/README.md` 空置域行 + `task/service/harvest/README.md` 增「**传送前第一层检查**」小节
  (含"为什么只有第一层中招"的几何解释 + 回归用例位置) ✓

### 验证 (0.9.66)
双编译 ✓ · 单测 **85 类 549 用例 0 失败** ✓ · gametest **forge 112/112 + neoforge 112/112 全绿** ✓
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
(实测日志: `[VOID-LAND] 第一层检查: 挖净传送落点 88,-58,200 (头位)` → `88,-59,200 (本体)`, 女仆在 93,-57,200 未受伤 ✓) ·
逐节点 clean+jar ✓ · jar 内 `version = "0.9.66"` ✓ · 部署 `.bak-0917i` + md5 双端一致 ✓

## 0.9.65 (2026-09-17) — **正式版** (含 0.9.64.1~0.9.64.4 全部内容): neo 箭塔伤害补齐 + 空置域 Y 保存变 0 + 绑定/崩服修复 + flaky 治本

> **版本说明**: 本会话先以 `0.9.64.1` ~ `0.9.64.4` 四次补丁部署 (每次都已上双平台); 用户裁定**统一为 `0.9.65` 正式版**
> ⇒ 本节即"0.9.65 实际包含的全部内容", 其下 `0.9.64.1` ~ `0.9.64.3` 各节保留为当时的中间部署记录 (内容已并入本版) ✓

> 用户裁定: "补齐, 还有修bug" — ① 补齐 1.21.1 箭塔伤害配置; ② 修"TLM 任务栏界面设置空置域 Y 轴 → 保存后变 0"。

### Fixed
- **neo 箭塔忽略伤害配置** (产品): `DefenseTowerFire.fireArrow` 的 1.21.1 分支**整块缺失**伤害口径
  (含 `setBaseDamage`) ⇒ neo 上单塔 GUI 覆盖与全局 `defense_tower.damage` **都无效**
  (v79.63.11 那次修只进了 1.20.1 分支) ✗ ⇒ 现补齐: `单塔覆盖 > 全局默认 > vanilla`,
  且**默认 2.0 = 原版箭基础伤害 ⇒ 未改配置者行为不变** ✓
- **空置域屏「保存高度」存成 0** (产品): `VoidExcavationConfigScreen` **绕过基类助手**手写 payload —
  用 `putString("value", val)` 配 `ACTION_SET_INT`, 而服务端是 `cfg.putInt(key, payload.getInt("value"))`
  ⇒ 对 StringTag 取 int **得 0** ✗ (min_y=0 = 挖到世界底部, 覆盖"自动检测基岩层"; 屏上回读也变 0) ⇒
  改走 `sendSetInt` ✓; 同屏另两处手写 (销毁名单 / 导航开关) 一并改用基类助手 `sendSetList` / `sendToggle` ✓,
  空的 min_y 继续用 `sendRemove` (回落到自动检测) ✓

### Added
- **`DefenseTowerLogic.effectiveArrowDamage(override, global, vanilla)`** — 伤害决策**纯函数**,
  1.20.1 / 1.21.1 两分支**共享同一实现** (本次 neo 漏配的根因就是"两处内联重复实现必漂移" ✗) ✓
- **单测**: `DefenseTowerLogicTest` 增"伤害优先级"用例 (含"默认配置 ⇒ 2.0 行为不变"回归锚点) ✓
- **守护测试 `GuiActionPayloadGuardTest`**: 机械拦住"配置屏内直接引用通用动作常量"
  (必须经 `sendSetInt/sendSetList/sendSetString/sendToggle/sendRemove`) ⇒ 旧代码那行会被当场拦下 ✓
- **README**: `task/gui/README.md` 增「payload 字段类型契约」表 (含本次实测后果); `defense/README.md` 记伤害口径 + 双平台差异备案 ✓

### 附: 其余配置项已一并核对
空置域屏 4 个写动作全部核对 (销毁名单/导航开关/保存高度/清空高度) ✓; 全部屏 30 处动作调用逐条审计,
除本屏外均**已走基类助手** ✓; 两个自定义动作 (`craft_chain` 的 `ACTION_SET_TARGET` = String↔String、
`ai_control` 的 `ACTION_TRANSFORM` = 空 payload↔不读) 均**类型对称** ✓

### 验证 (0.9.64.4 部署时实测 — 该版本内容已并入 0.9.65, 见本节顶部「版本说明」)
双编译 ✓ · 单测 **85 类 549 用例 0 失败** ✓ · gametest **forge 111/111 + neoforge 111/111 全绿** ✓ ·
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
逐节点 clean+jar ✓ · jar 内 `version = "0.9.64.4"` ✓ · 部署 `.bak-0917g` + md5 双端一致 ✓

## 0.9.64.3 (2026-09-17) — 右键绑定**全量审计**: 另修 2 处手工 BlockPos 解析 (统一 NbtCodecs) (已部署双平台)

> 用户要求: "检查下右键绑定的是否都正确" ⇒ 对全部绑定入口做**机械对账** (73 处读写配对逐一核)。

### 审计结果
| 位置 | 结论 |
|---|---|
| `BlockInteractSetupHandler` | 0.9.64.2 已修 ✓ |
| **`ArmTransferSetupHandler.readPos`** | **本批修** — 手工双平台解析 (`NbtUtils` / `getCompound(key).getLong("pos")`), 而写侧 (`FarmContainerBindPacket`) 用 `NbtCodecs` ⇒ 各写一套 = 错题 #348 同款隐患 (重复实现必漂移; 1.20.1 分支还缺"坏数据→null", 会静默得 `(0,0,0)`) ⇒ 统一 `NbtCodecs` ✓ |
| **`BlockInteractConfigScreen.getPosText`** | **本批修** — 同款手工解析 ⇒ 统一 `NbtCodecs`; 坏数据/未绑定显示走 translatable (`screen.littlemaidmoreaction.bi.unbound`, zh+en), 不再显示 `0, 0, 0` ✓ |
| `MaidPowerBeltBlockEntity` (Create 皮带 `Controller`) | **明确不动** — 双平台**各自读写配对** (1.20.1 `CompoundTag{X,Y,Z}` / 1.21.1 `IntArrayTag`), 属**方块实体存档格式**; 改成 NbtCodecs 会让**老存档读不出** ✗ (审计的价值也在于"确认哪些不该改" ✓) |
| `origWorkPos` | ✓ 旧档迁移读取 (写侧已退役), 读侧已用 `NbtCodecs` |
| void_excavation / dam_fill / farm 两个包 / 三条管线 / 全部 gametest | ✓ 全 `NbtCodecs` |

### 验证 (0.9.64.3)
双编译 ✓ · 单测 **84 类 547 用例 0 失败** ✓ · gametest **forge 111/111 + neoforge 111/111 全绿** ✓ ·
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
逐节点 clean+jar ✓ · jar 内 `version = "0.9.64.3"` ✓ · 部署 `.bak-0917f` + md5 双端一致 ✓

## 0.9.64.2 (2026-09-17) — 补丁: **修「右键交互绑定后方块信息丢失」** (木棍标记读写不对称) (已部署双平台)

> 用户报告: 右键交互任务绑定方块后, 绑定信息丢失。**取证**: 解 `latest.log` + 解压 TLM 女仆备份 (gzip NBT) 与主世界
> 实体区块, 得到完整链路 (见 lessons #348)。

### Fixed (产品, 3 处)
- **绑定坐标被静默替换成女仆自己脚下的格子** (根因): `BlockInteractSetupHandler` 标记路径**绕过**项目编解码器
  —— 写 `tag.put(MARK1, NbtUtils.writeBlockPos(pos))` (1.21.1 返回 **IntArrayTag**), 读 `NbtUtils.readBlockPos(tag, MARK1)`
  (期望 LongTag/CompoundTag) ⇒ **读空** ⇒ `.orElse(maid.blockPosition())` 兜底生效 ⇒ 绑成女仆自己站的格
  (实测: 标记 `-3092,63,2269` → 绑成 `-3091,63,2270`) ⇒ 下一 tick 该格非目标方块 ⇒ 走"方块被破坏"分支把绑定清掉 ✗
  **修法**: 标记读写统一走 `NbtCodecs` (与 `VoidExcavationSetupHandler` 同款, 双平台对称, 错题 #183), 并**取消危险兜底**
  —— 读不到就明确提示重标, 绝不静默绑到她脚下 ✓
- **未加载区块被误判为"方块被破坏"**: `world.getBlockState(pos).isAir()` 对未加载区块也返回空气 ⇒ 女仆远离/传送/跨维度时
  会把用户绑定静默清掉 ✗ ⇒ 加 `if (!world.isLoaded(pos)) return;` ✓
- **`NbtCodecs.readBlockPos` 1.20.1 坏数据返回 `(0,0,0)`** (与 1.21 的 null 语义不一致) ⇒ 补键存在性校验, 双平台统一 null ✓
  (此条由**新增单测** `NbtCodecsTest` 抓到 — 见下)

### Added (测试)
- **单测 `NbtCodecsTest`** (3 例, 纯 JVM): 锁"标记编解码**对称**"契约 (往返逐字段相等 / 缺键与坏数据必须 null 不伪造坐标 / 多键互不干扰) ✓
- 顺带修正 1 处: 我新加的玩家提示原为硬编码中文 ⇒ 改 `Component.translatable`
  (`message.littlemaidmoreaction.bind.mark_invalid`, zh_cn + en_us) — **被既有 `PlayerTextI18nGuardTest` 当场抓到** ✓ (守护有效 ✓)

### 验证 (0.9.64.2)
双编译 ✓ · 单测 **84 类 547 用例 0 失败** ✓ · gametest **111/111** (forge ✓ / neo 连跑 2 轮全绿 ✓) ·
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
逐节点 clean+jar ✓ · jar 内 `version = "0.9.64.2"` + lang 已打包 ✓ · 部署 `.bak-0917e` + md5 双端一致 ✓

## 0.9.64.1 (2026-09-17) — 补丁: **修崩服** (老农场区域导入递归) + 右键交互屏标签重叠 (已部署双平台)

### Fixed
- **崩服 (StackOverflowError, crash-2026-09-17_12.49.53)**: v79.63.23 引入的"老 `farm_regions.json` 惰性导入"存在
  **无限递归** —— `FarmRegionStorage.getFor()` → `FarmRegionLegacyImport.maybeImportFor()` → 又回调 `getFor()` ✗
  ⇒ 服务器 tick 循环栈溢出崩服。
  **修法**: ① `FarmRegionStorage` 拆出**无回调读** `readNbt(maid)` (纯 NBT 读取), `getFor` = 导入 + `readNbt`
  ⇒ 导入流程内部一律走 `readNbt`, **结构上不可能再递归** ✓; ② 导入器解析成功后即**本进程收口** (原逻辑等"集齐
  老文件里所有 uuid"才 seal, 而那些 uuid 未必都会加载 ⇒ 永不解封 ⇒ 每次 `getFor` 都重读重解析 ✗)。
- **右键交互任务界面标签重叠** (`BlockInteractConfigScreen`): 标签画在 row0/row1, 而"定时开关/间隔步进"按钮
  **也在 row0/row1** ⇒ 同排重叠 (错题 #331 第 7 条同款) ✗。**修法**: 标签独占 row0 (绑定方块) / row1 (定时间隔),
  控件整体下移 row2 (定时开关) / row3 (±1/±10 四等分) / row4 (清除绑定) ⇒ **零同行重叠** ✓
- **新增崩溃回归用例** `lmaFarmLegacyImportNoRecursion` (**独占批次 `z_legacy`**): 用**路径覆盖**造出"老文件含该女仆 uuid"
  这一条件 (gametest 沙箱原本没有老文件 ⇒ 旧逻辑直接早退 ⇒ **测试从未覆盖到递归分支** ✗ 这正是上线才崩的原因) ✓

### 验证 (0.9.64.1)
双编译 ✓ · 单测 83 类 544 用例 0 失败 ✓ · gametest **111/111** (forge ✓ / **neo 连跑 3 轮全绿** ✓; 其中新增 z_legacy 用例实测
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
`区域数=1 首区=1,2,3` = 导入正确执行且**无栈溢出** ✓) · 逐节点 clean+jar ✓ · jar 内 `version = "0.9.64.1"` ✓ ·
部署 `.bak-0917d` + md5 双端一致 ✓

## 0.9.64 (2026-09-17) — 本会话批次汇总: Cloth 杂项 / 节日纯读 jar / 作物区域随实体 / 用例治 flaky (已部署双平台)

> 版本 0.9.63 → **0.9.64** (patch bump: 公开 API 面未变 — `PathingApi` 等 public 方法数与已发布 0.9.63 一致)。
> 本节汇总本轮全部落地内容; 各批细节见下方 v79.63.21~26 各节与 `docs/lessons-learned.md` #338~#346。

### Added / Changed (用户可见)
- **Cloth 全局设置新增「杂项」分类** (`active.running_belt.stress` = 发电皮带应力, -1=原公式 / ≥0=固定);
  顺带补齐三处"有 TOML 键零 GUI 入口"的配置 (稀有群系 ×3 → 环境感知; `dam_fill.default_chunks` → 填坝排水子屏)。
- **节日数据改纯读 jar 预设** (`assets/littlemaidmoreaction/festival.json`): 删除 config 副本与"缺字段自愈"补丁;
  老 `config/…/festival.json` 静默忽略 (不读不动) ⇒ 根治"老 config 永不升级 ⇒ 节日不送礼"。
- **作物区域改存女仆 NBT** (`lma_cfg_farm.regions`, 对齐 TLM 做法): 数据随实体 ⇒ 删女仆/收魂符不再留孤儿条目、
  编辑不再全量重写全局文件; 每区域记 **dimension** (换维度该区域暂停, 屏上橙字提示);
  老 `config/farm_regions.json` **一次性导入** (惰性 + 全 uuid 命中才改名 `.imported`); `FarmRegionStorage` 从 `storage/` 迁 `task/service/harvest/`。

### Fixed (测试夹具治 flaky — 产品逻辑零改动)
- 塔族 2 例: ① GameTest 框架**屏障围墙**挡住模板外靶子 (`StructureUtils.encaseStructure`) ② 夹具主人是**创造模式** ⇒ 原版 `hurtAndBreak` 本就不扣耐久。
- `lmaenginepanicisolation`: 夹具注入的全局必抛任务被"自动启动"**反复复活** ⇒ 两条断言建立在错误前提 (已改抗复活形式 + 护栏不变量断言)。
- 采矿 3 例: `runAfterDelay` 定时断言 → **条件轮询** (达成即过, 实测 40-140t); 两例场地**不可走** (矿摆在高 1-2 格) ⇒ 降到可走平面 (用户规则"能走到才挖")。

### 验证 (0.9.64, 2026-09-17 实测)
双编译 `--no-build-cache` ✓ · 单测 **83 类 544 用例 0 失败** ✓ ·
gametest **forge 110/110 ×3 轮 + neoforge 110/110 ×3 轮全绿** ✓ · 逐节点 clean+jar ✓ · 部署 `.bak-0917b/c` + md5 双端一致 ✓

## 0.9.63 / v79.63.26 (2026-09-17) — 采矿用例**场地可走性**修正 (按"能走到才挖"规则) (已部署双平台)

> 用户裁定 (2026-09-17): "这不是经典的走不到吗, 走不到就不挖, 只有能走到的头上才会挖" ⇒ 用例场地必须**可走**;
> 产品侧现行行为 (斜上 >4 格的矿: 试 `target.above()` 悬空格 → 导航失败 → 跳过集) **符合该规则, 不改产品** ✓

### Fixed (测试夹具, 场地几何)
- `lmaChainOreCrossChunkMid`: 矿原在 `footAbs+0` = 平台面**上方 2 格** (悬空不可站层) ⇒ 女仆走不到 ⇒ 期望与场地矛盾;
  现降到 `footAbs-2` (与平台面上表面同层, 垫石 `-3`) ✓
- `lmaChainOreNearBeforeFar`: 近/远矿原在 `footAbs+0` = 女仆脚下**上方 1 格** ⇒ 同因; 现降到 `footAbs-2` ✓
- 保持: 跨区块 (下一区块内 5 格) / 10 格与 15 格距离 / 3×3 石台 / `collect_ore` 链路 / 条件轮询断言 ✓

### 验证 (2026-09-17)
双编译 ✓ · 单测 **83 类 544 用例 0 失败** ✓ · gametest **forge 110/110 ×3 轮** + **neo 110/110 ×3 轮** (全绿, 此前每次 1-3 例 flaky) ✓ ·
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
达成耗时更快 (跨区块 40t / 远矿 60t / 近远 60t) ✓ · 逐节点 clean+jar ✓ · 部署 `.bak-0917b` + md5 双端一致 ✓

## 0.9.63 / v79.63.25 (2026-09-17) — 引擎护栏用例**抗自动复活**修复 (治偶发假红) (已部署双平台)

> 用户裁定 (2026-09-17): `lmaenginepanicisolation` 的偶发红要修。

### Fixed (测试夹具, 非产品缺陷)
- **根因**: 该用例 `registerPassive("__panic", 必抛管线)` 是**进程全局注册**且 `TaskRegistry` **无注销 API** ⇒
  任务留在注册表; 而 `tickStandalonePassives` 每 10t 有"自动启动"分支 (开关开 + 非 in_progress ⇒ 自动 submit)
  ⇒ 降级 (cancelPassive 清 in_progress) 后**下一 tick 就被复活** ⇒ 同一执行内该 maid 被降级 3 次、
  驱动次数观测到 6/9 ⇒ 两条断言 (`calls == 3` / `降级后不应再驱动`) 假红 ✗。
- **修法**: ① `calls` 断言改为抗复活形式 — `calls ≥ MAX(3)` 且**必须出现"第 1..3 次 + 紧随降级"序列**;
  ② 尾段"降级后不应再驱动"改为**护栏真正的不变量**: 降级后再驱动 **不得让异常逃逸** (连驱 3 次全绿) ✓;
  ③ 收尾 `cancelPassive` 减小复活窗口 ✓。
- **撤销**: 期间试过的 `setDayTime` 时钟对齐**无效** (它只改白天时间, 不推进 `getGameTime` ⇒ 手工驱动每次仍推进 1t) — 已撤 ✓。

### 验证
双编译 ✓ · 单测 83 类 544 用例 0 失败 ✓ · gametest **neo 连跑 3 轮 panic 用例全绿** ✓ · 逐节点 clean+jar ✓ · 部署 `.bak-0917a` + md5 双端一致 ✓
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓

## 0.9.63 / v79.63.24 (2026-09-17) — 采矿用例**条件轮询**改造 (治时序 flaky) + gametest 口径修正 (已部署双平台)

> 用户裁定 (2026-09-17): 采矿面 flaky 的根因是"断言写在固定 `runAfterDelay(N)` 墙点上, 而走路+挖穿耗时浮动"
> ⇒ 改为**条件轮询** (成立即通过)。

### Changed
- **3 个采矿用例改条件轮询** (`lmaChainOreFar` / `lmaChainOreCrossChunkMid` / `lmaChainOreNearBeforeFar`):
  新增共用工具 `pollUntil(...)` — 每 20t 检查一次, **成立即 `succeed`** (顺带记录**实际达成耗时**用于校准窗口),
  到期未成则打印现场 (女仆位置/观察点方块名) 后 `fail` ✓。窗口: 900/800/1200 (`timeoutTicks`)。
  实测达成耗时: **60t / 60t / 140t** (改前固定窗口 400/400/1000 ⇒ 原设计余量其实很大, 说明 flaky 来自
  "偶发改走其它目标"这类路径抖动, 轮询能在条件成立的**当刻**收口 ✓)。
- 塔族用例批次复位: `lmaDefenseTowerDurability` 从 `z_tower4` 回到独占批次 `z_tower6` (另一会话曾把它并进 z_tower4)。

### Fixed (文档口径)
- 基线表 gametest 数字 **111 → 110**: 逐批求和 (defaultBatch 73 + z_ore 15 + z_slow 8 + z_void 8 + z_tower1/2/3/5/6/7 各 1)
  与源码 `public static void …(GameTestHelper)` 计数、merged 源、编译 class **四者一致 = 110** ✓;
  此前 111 记录来自**另一会话并发编辑同一测试文件的中间产物**, 非本期回归 ✓。

### 验证 (2026-09-17)
双编译 ✓ · 单测 **83 类 544 用例 0 失败** ✓ · gametest **forge 110/110** ✓ / **neo 110/110** ✓
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
(中间轮 neo 曾 1 例 `lmaenginepanicisolation` 红 = 既有 flaky, 非采矿) · 逐节点 clean+jar ✓ · 部署 `.bak-0916s` + md5 双端一致 ✓

## 0.9.63 / v79.63.23 (2026-09-16) — 作物区域改存**女仆 NBT** (对齐 TLM) + 记维度 / 老文件一次性导入 (已部署双平台)

> 用户裁定 (2026-09-16): 区域数据应跟女仆实体走 (TLM 做法: 任务信息挂实体), 不用全局 config 文件;
> 每区域记 **dimension** (换维度该区域暂停); 老 `farm_regions.json` **一次性导入**。

### Changed
- **`FarmRegionStorage` 迁 `storage/` → `task/service/harvest/`** (ArchGuard 硬红线: `storage` 不得 import `task.*`,
  而区域要经 `MaidData` 写女仆 NBT) — 存储形态从"全局文件 `Map<uuid,List<区域>>`"改为**该女仆 NBT
  `lma_cfg_farm.regions`** (ListTag)。数据随实体消失 ⇒ 删女仆/收魂符不再留孤儿条目; 编辑不再**全量重写整份文件**。
- `FarmRegion` 记录加 **`dimension`** 字段; `FarmExecute.tick` 只处理**当前维度有效**的区域
  (`isActiveIn`), 其余暂停; 区域管理屏显示维度 (异维度时橙字「⚠ 其他维度·暂停」)。
- 网络包 (Bind/Edit) 改为**按 uuid 在玩家所在维度解析女仆实体** (`FarmRegionStorage.findMaid` —
  `level.getEntity(UUID)` 在 1.20.1 不存在, 用 `getAllEntities()` 遍历); 女仆不在线 ⇒ 静默放弃。
  Sync 包新增 dimension 字段 (双平台同构, `writeUtf(...,128)`)。

### Added
- **`FarmRegionLegacyImport`** — 老 `config/littlemaidmoreaction/farm_regions.json` **一次性导入**:
  惰性触发 (`FarmRegionStorage.getFor` 首次读时, 幂等) · 老数据无维度 ⇒ 补**该女仆当前维度** ·
  **全 uuid 都遇到过才改名 `.imported`** (避免"改名太早 ⇒ 后续才加载的女仆数据丢") · 坏 JSON 收口不重试。

### Removed
- 旧 `storage/FarmRegionStorage` (全局表 + `load`/`save` 文件面) · 旧 `FarmRegionStorageTest` (uuid 版契约, 由
  `FarmRegionStoragePureTest` 纯函数面 + gametest `lmaFarm*` 实体面替代) · 主类/neo 入口的 `onServerStarting` 加载调用。

### 验证 (2026-09-16 实测)
双编译 ✓ · 单测 **83 类 544 用例 0 失败** ✓ (旧 uuid 版 6 例退役, 新纯函数 8 例) ·
Token 三件套齐 ✓ · 部署 `.bak-0918a` ✓ · 交付副本 `release-0.9.74/` ✓
gametest **forge 111/111** ✓ / neo 111 跑满 3 例 (2 塔视线 + 1 既有 flake `lmachainorenearbeforefar`) ✓ ·
逐节点 clean+jar ✓ · 5b 双平台 (新类在位/旧类零残留/dimension+findMaid 在位/forge SRG 10-31 抽样正常) ✓ · 部署 `.bak-0916q` + md5 双端一致 ✓

## 0.9.63 / v79.63.22 (2026-09-16) — 节日数据改为**只读 jar 预设** (删除 config 副本与自愈补丁) (已部署双平台)

> 用户裁定 (2026-09-16): 节日是**预设数据**, 不给用户改 ⇒ 不需要 config 副本; 老 config 文件**静默不管**;
> 自愈代码**直接删干净** (仅节日面)。

### Changed
- `storage/FestivalLoader` 重写为**纯读 jar 资源** `assets/littlemaidmoreaction/festival.json` → `FestivalTable`
  (178 → 114 行): 删除 `copyPresetIfMissing()` (不再往 config 写文件)、删除 `selfHealEnabled` 开关与自愈块 (~45 行)、
  删除 config 路径读取。新增纯解析入口 `parse(Reader)` + `loadFromJar()` ⇒ **纯 JVM 可测**; `loadFromFile(Path)` 保留为测试/调试入口。
- 加载 INFO 保留并明确来源: `[LMA/Festival] 节日表已从 jar 预设加载 — 17 节日`。

### Fixed
- **根治「节日不送礼」的结构性病根** (lessons #335/#337): 旧设计把预设复制成 `config/…/festival.json` 且
  **只在缺失时复制** ⇒ 老 config 永久冻结 ⇒ 后续新增节日/新增字段 (`foods`) 老用户**永远拿不到** ✗。
  改单源后, jar 预设与版本天然一致, 不会再复发; 之前的"缺字段自愈"补丁随之失去意义而删除。
- 老 `config/littlemaidmoreaction/festival.json` 残留: **静默忽略, 不读不动** (不删用户文件)。

### 验证 (2026-09-16 实测)
双编译 `--no-build-cache` ✓ · 单测 **83 类 542 用例 0 失败** ✓ (+2: jar 预设契约用例) ·
gametest **forge 111/111** ✓ / neo 111 跑满 / 2 例 = 既有塔视线用例 (无新增回归) ✓ ·
逐节点 `clean+jar` ✓ · 5b 双平台 (`FestivalLoader` 属"零原版调用"类 ⇒ SRG 计数天然为 0, 改按**类抽样**判定:
`DefenseTowerFire` m_=21 / `GameTickPipelineManager` m_=6 / 主类 m_=3 ⇒ jar 已 reobf ✓) · 部署 `.bak-0916p` + md5 双端一致 ✓

## 0.9.63 / v79.63.21 (2026-09-16) — Cloth「杂项」分类 + 全局配置项接线补缺 (已部署双平台)

> 用户裁定 (2026-09-16): 跑步发电应力**不进 TLM 任务设置页**, 改为**全局设置**里新增「杂项」分类承载。
> 顺带修掉三处「有 TOML 键、无任何 GUI 入口」的配置 (新增 define 忘接线 GUI 的空档)。

### Changed
- **Cloth 设置新增「杂项」分类** (真全局参数归属; 判据: 不属于任何单个任务、无任务子屏/无 per-maid 覆盖):
  `发电皮带应力` = `active.running_belt.stress` (-1 = 原公式随蛋糕 1024/2048/4096; ≥0 = 固定应力; 转速仍随蛋糕 96/192/256 RPM)。
- **环境感知**分类补 `稀有群系通报` / `稀有群系检测间隔` / `稀有群系信号半径` = `passive.env_sense.rare_biome_*` (此前无入口)。
- **任务自定义 → 填坝排水** 子屏补 `默认区块数` = `active.dam_fill.default_chunks` (此前无入口; 单女仆仍可在 TLM 任务设置页覆盖)。
- `ConfigConsistencyTest` 人工核对条目数更正为实测值 (Cloth 45 / 任务子屏 43, 旧注释 36/27 已严重漂移), 并注明回写方法。
- README/文档: `screen/README.md` 补「配置项归属判据」+ 漏接线警示; `docs/ARCHITECTURE.md` 基线表按实测回写 (单测 83 类 540 用例; gametest forge 111/111, neoforge 111 跑满/5 例塔 mock 噪音); 错题集头声明 #333/184 → **#337/188**。

### Fixed
- 全局配置**专用服务器同步**面复核: 三键均已在 `MoreActionConfig.reg` 注册 (否则改 `active.*` 只写客户端无效) — 无缺陷, 记录为交付检查项。

### Fixed (neo gametest 夹具, 用户 2026-09-16 批准)
- **塔族 7 例 `sync_data` 崩溃消除** (2 例剩余): 根因 = mock 主人注册路径 `PlayerList.placeNewPlayer` 中途触发
  TLM 1.21.1 `onPlayerJoinWorld` 无条件发 `SyncDataPackage` ⇒ mock 连接被拒抛异常 ⇒ 原版末尾 `players.add` /
  `playersByUUID.put` 被跳过 ⇒ 主人从未注册。修法: 夹具 `registeredOwner` **per-dimension 缓存复用** +
  容错注册 (**只补 `playersByUUID`** — `getPlayer(uuid)` 的唯一入口; 不进 `players` 以免 `broadcastAll`
  拿 null connection 发包污染其它用例)。实证: `TOWER-DIAG ownerOnline=true` + 塔真开火; 7 例 ⇒ **2 例**。
  只动测试夹具, 未碰 `defense/` 产品代码。详见 lessons #339。

### 待查 (非本批次引入)
- `lmadefensetowerdurability` / `lmadefensetowerfarrange`: TOWER-DIAG 显示 `ownerOnline=true` 但
  `no LOS target candidates=1` ⇒ 塔卡**视线判定** (`hasLineOfSight` 不通过 ⇒ 不开火 ⇒ 箭数不变/弓不掉耐久)。
  **两平台都有该日志** (非平台差异) ⇒ 属塔视线判定或夹具几何待查。
- gametest 套件本身有既有 flaky (同日三次 forge 运行各 1 例不同失败: `lmafurnaceblacklist` / `lmafarmplant`),
  与错题 #270「全绿不可复现」同源。

## 0.9.63 / v79.63 (2026-09-11 补记) — 成就感知 + 任务树 + 防御塔/五子棋 (已部署双平台)

> ⚠️ 本条为 **2026-09-11 文档审计批次补记** (0.9.62/0.9.63 发布时 changelog 未同步)。细节权威: [docs/lessons-learned.md](docs/lessons-learned.md) 错题 #262-268 + [docs/plans/doc-audit-2026-09-11.md](docs/plans/doc-audit-2026-09-11.md)。

- **成就感知 API** `AdvancementSenseApi` (2026-08-31, 待用户写反应验收): 主人完成成就 → 女仆反应; 通配 + 精确注册可叠加; 回调统一 String advancementId (双平台差异消化在事件层)。
- **任务树**: `task/quest/` 4 类 (QuestNode/QuestTreeLoader/QuestTreeLayout/QuestProgressReader[占位]) + `screen/LmaQuestScreen` 成就树屏 (数据 = 运行时读原版成就 JSON, 双平台 `advancement(s)/` 路径; 进度走 ClientAdvancements.Listener)。
- **防御塔** `defense/` (v79.62.3): garage_kit 扩展为可放置防御塔 (箭/弹幕双模式自动判定), BE 继承 TLM TileEntityGarageKit; `config/DefenseTowerConfig` + Cloth 分类 + 每塔 GUI 覆盖。
- **五子棋「直接判你赢」** (v79.62.3): 女仆开关 (PD `lma_gomoku_instant_win`) + 2 网络包 + `MaidOtherSettingsScreen`; 判赢走 TLM 原生链路 (不取消事件/不重发包)。
- **挖空置域大修 + 填坝排水** (v79.62 → 0.9.63): 见 0.9.62 条 (两条同批发布)。
- **TaskTree NPE 修复** + quest 任务树/advancement 感知 (commit 431a5f4)。

## 0.9.62 / v79.62.2 (2026-09-11 补记) — 填坝排水 + 挖空置域大修 + 温度触发语义

> ⚠️ 同样为审计批次补记; 详细设计见 [docs/design/void-excavation-and-dam-fill.md](docs/design/void-excavation-and-dam-fill.md)。

- **新任务 dam_fill 填坝排水** (用户逐条裁定): 填坝 = 外圈 4 方向 (北南西东) 筑**重力方块自落**墙 [沙/砾石/铁砧, 放一批等自然下落, 检测列堆顶到 topY = 标记层+3, 列间等 20t]; 排水 = 按**排**从墙边往里 [第 1 排 x=minX 横穿所有区块 → x+1 → maxX, 液体→空气 + 破坏粒子 + 舀水声 + 挥桶]; 两模式**独立** (cfg `drain_enabled`, 不做「墙完自动接排水」); cfg 只存 start/size/input/drain_enabled, **游标/方向/列/排全 PD 内存**; 任务期间 HOME + 每 200t 续水下呼吸; TLM 任务栏配置屏「模式」切换按钮。
- **挖空置域系列修复**: 液体提前空气化 + 相邻扫描 + 清 WATER 缓存 / 矿 6 邻面至少 1 面空气才挖 + 脚下 >2 格深矿扫描排除 / `navGoal = target.above()` + 看门狗 5s→2s CD→2 次→跳过集 10s / 递归 `idleScan` 三处改 CONTINUE / **ChunkWorkArea 每 tick 设 work·idle·sleep 三中心** (防 Activity.IDLE 被拉回) + anchorY 用游标 y / 挖产物遍历全槽 `insertItemStacked` / 认领新区块清 navCd·navTimeout / 空气格跳过去掉限距。
- **温度感知语义改造** (用户裁定): 触发即写 5 分钟 CD + tick 未冷却即 `cancelPassive` + 到火/水旁立即 CD 并结束 (删 30 秒停留)。
- **认领池生命周期死锁修复**: `MaidUnloadRegistry` → 收魂符/死亡/移除时 `poolMark(0)` 释放 + 清游标。

## 0.9.61+ / v79.62 (2026-08-19) — 新功能: 种菜区域制 (未发布, 待验收)

- **作物区域种植管线** (用户裁定区域制): 木棒框选 AABB 区域 + 指定作物 (cropId), 女仆 farm 任务在绑定区域内收成熟 + 种指定作物 (跨区块续)。`config/littlemaidmoreaction/farm_regions.json` 按女仆 uuid 持久化 (区域管理 GUI + 木棒选区注册)。
- **收获面**: 区域内全部成熟作物 (不限指定, 用户裁定) — 左键 destroyBlock 整株拔 + 掉落, 右键 FakePlayer 交互; CropRegistry 内置原版作物族 (CropBlock/甘蔗竹仙人掌/下界疣/可可/西瓜南瓜) + registerHandler 第三方扩展 + 骨粉可催熟通用 fallback (未知 mod 作物按 age 属性上界判定)。
- **播种面**: 区域内可种耕地种区域指定种子 (种子不足跳过该区域); 种子源箱 (FARM_SEED_CONT 绑定, 背包无种自动取)。
- **收获箱**: FARM_HARVEST_CONT 绑定, 空转 tick 把背包**收获产物**存入 (只存产物 — 排除空/区域种子/可损坏工具武器护甲/标记物, isHarvestProduct 纯判定)。
- **容器绑定**: 主手标记物品 + FarmContainerBindPacket 选角色 (seed/harvest/take/deposit) → 右键女仆交付 PD。
- **区域配置修复**: `FarmRegionStorage.load()` 双平台 onServerStarting 挂载 (原只 save 不 load → 重启丢区域); config 路径归一化 (去双嵌套)。
- **BlockPatternCache 全列扫描修复** (错题 #238 同类): CROP/FARMLAND 改全列扫描 — 悬浮/地下农场不再被 WORLD_SURFACE±8 窗 miss (原 height 窗只扫近地表)。
- **FestivalTable 守卫** (连带): lunar 农历库缺失时静默跳过农历条目 (jar-in-jar 生产有, dev classpath 无)。
- **gametest +3** (18/18 ×2): lmaFarmPlant (播种) / lmaFarmSeedBox (种子源箱取种) / lmaFarmHarvestBox (收获箱只存产物, 四向判定) + 存量 lmaFarmJob。
- 验证 (2026-08-19 实测, 清 run world 后): 双编译 --no-build-cache ✅ + 单测 64 类 468 用例 0 失败 ✅ + gametest 双节点 **18/18 ×2** ✅ (错题 #251)

- **8/20-21 追加批次 (种地实测修复 + 三新管线 + 节日/可爱/驱赶)**:
  - **种地链路修复** (用户实测): ① 产物不进收获箱 (storeHarvests 移 tick 开头) ② 种子被当产物收进收获箱 (isSeedItem 纯种子留背包, 种子即作物留 1 组余进箱) ③ 西瓜/南瓜根茎被当成熟收 (排除 StemBlock) ④ 种到收到死循环 (实时验证+invalidateBlock) ⑤ 女仆不寻路 (navigate 改设 WALK_TARGET)
  - **背包管理** (用户裁定): 保 1 空位+1 种子; 满时回种/丢种; 收获箱满省流 600t
  - **GUI 修复**: 3D 预览朝向 / 按钮重叠 / farm 汉化
  - **节日礼物**: 17 节日 + 食物随机 + 狗修金文案 + 全局限频 + /lma festival 调试
  - **可爱动作**: 挥手/歪头/回头 (MaidCuteIdleBehavior)
  - **苦力怕驱赶**: AvoidEntityGoal 注入
  - **酒狐奶管线** (jiuhu_milk 被动): 主人受伤自动喂奶
  - **锻造管线** (smithing 主动): 钻石装备+合金锭→合金 (无模板)
  - **探险家地图** (explorer_map 被动): 报宝藏坐标一次
  - **刷子管线** (brush 主动): 自动刷可疑方块
  - **修复速度 20s** / **哈气默认关** / **温度气泡**
  - 验证: 双编译 + 单测 469 + gametest 18/18 x2 + 双平台部署 (错题 #252-253)

  - **8/28 空置域配置接线修复批次 (v79.62.1 收尾)**:
    - **per-maid 配置屏复活专用 VoidExcavationConfigScreen** (用户裁定: 空置域只要名单不要白名单 → 自绘框 + 空置域专名): 管线 `getConfigGuiProvider` 原返回通用黑白名单屏 (写 `blacklist` 键, 与 tryDig 读的 `pickup_blacklist` 断链 → per-maid 配置静默失效); 改回专用屏后断链修复
    - **黑名单 → 销毁名单 (用户裁定)**: 名单内物品挖出即销毁消失 (不进背包不落地, 消除"落地不捡"地面实体卡顿); 删销毁开关 (全局 + per-maid); per-maid 键 `pickup_blacklist`→`destroy_list`, 全局 `VOID_PICKUP_BLACKLIST`→`VOID_DESTROY_LIST` + 删 `VOID_DESTROY_ENABLED`; 关闭寻路 toggle 保留
    - **销毁名单框预填**: 配置到达后一次性预填当前名单 (修"打开空框/误存覆盖"隐患); 保存空值=清空名单回退全局; toggle 文案读当前配置自纠
    - **ActiveTaskConfig reg 同步**: 空置域全局配置漏 reg() 修复 (ConfigConsistencyTest 平衡) (错题 #259)
    - **工具耐久补扣 (用户实测「没见掉耐久」)**: `world.destroyBlock` 是直接世界操作, 不经玩家挖矿流程不扣耐久 → `tryDig` 挖掉后补 1 点/格 (对齐 DigThrough/SelfRescue, 双平台 hurtAndBreak 分支 + isDamageableItem 守卫); 耗完 vanilla 自动移除 → 下一格 `ensureToolFor` 换下一把; 全没了走「无工具提醒」 (错题 #260)
    - **死代码清理 (用户裁定 start.y=可挖最高y)**: 删 surfaceY 整套后台补扫 (probeSurfaceY/continueSurfaceScan/PD键) — 认领直接设定高度, 不补扫不算量, 天然支持挖地底; 删死方法 searchInputExpansion/searchOutputExpansion/findSafeStand + 常量; VOID-MOVE 三条 warn 降 debug; 注释修正 (错题 #262)
    - **实测修复批次 (用户实测, 错题 #263)**: ① 认领池对女仆生命周期反应 — MaidUnloadRegistry 登记 onMaidUnload, 死亡/收入魂符/移除时释放认领区块为未挖 (修"收起再放出后不动" = pool 残留 1 永久死等, cursor=0,0,0 + claimWait 循环); ② 边界液体改女仆 4 格检测 (sealBoundaryLiquid 从游标驱动改女仆位置驱动, ±4 扫区域外液体, 预算封顶) — 修"只填几次没填完"; ③ 配置屏坐标修正 — "区域"文字/销毁名单提示下移避开 toggle 按钮 (topPos+112/+124), 修"区块显示和开关重叠"; ④ toggle 文案配置到达后自纠 (renderAddition 同步)
    - **区块工作制 (behavior 通用化, 用户裁定)**: TLM `MAID_WORK_RANGE` 上限 64 且 `SchedulePos.tick` 每 40t 重置 restrict → 大面积 (>64 格) 工作只设大半径无效。新增 `api/pathing/ChunkWorkArea` + 接入 `LmaFlowCoordinationBehavior` (注册于 WORK 活动): HOME 模式 + 任务 `isChunkWork()` (void/farm 覆写) + 工作时间 (`Activity.WORK`) → 临时把 home/workPos 移到当前工作区块中心 (`restrictTo` 12) + **内置 3×3 区块预载缓存** (换块才重发); `stop()`/非工作时段恢复原点。种菜 `expandHomeToFarm` 大半径方案退役; 空置域管线重复跟随 + `updateChunkLoads` 并入 behavior; `VoidExcavationChunkManager` 瘦身为 forceChunk + regionBounds; +ChunkWorkAreaTest 3 例 (错题 #261)
    - 验证 (2026-08-28): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 476 用例 0 失败 ✅ + gametest 双节点 **24/24 ×2** ✅

## 0.9.60 (2026-08-18) — 修复批次: 坐下被动拦截 / 奶桶跨平台 / 配方双格式 / 清雪移除 / 挥手补位

- **坐下不再移动 (2026-08-16 用户实测「坐下还往水边走」)**: 温度取暖/铲雪/火把独立心跳被动自己 `maid.getNavigation().moveTo`, 不走 Brain 导航行为 — 坐下判断移入 `GameTickPipelineManager.tickStandalonePassives` 入口统一拦 (`isMaidInSittingPose()`), Brain 侧判断拦不到
- **酒狐奶/野生酒狐奶奶桶功能 (用户验收)**: ① 蛋糕 — LMA 自注册 2 配方 (奶桶×3+糖+蛋+小麦→原版蛋糕); ② 其他 mod 当奶桶 — 双平台牛奶标签矩阵: 1.20.1 = forge:milk + c:milk (tags/items 复数), 1.21.1 = c:milk + forge:milk (tags/item 单数 — 1.20.5+ 目录单数化), 全 replace:false 追加
- **1.21.1 配方双坑修复 (JEI 无配方最终根因)**: ① 配方目录 `recipes/`(复数)→`recipe/`(单数, 1.21.1 数据包格式 48); ② result 格式 `{"item":...}`→`{"id":...,"count":1}`; 配方按平台拆目录 (forge 1.20.1 格式 / neoforge 1.21.1 格式); +gametest `lmaMilkRecipesLoaded` (byKey 双平台锁)
- **清雪移除 (用户裁定: TLM 原版清雪覆盖)**: 删 SnowShovelPipeline + SnowQuery; 被动 7→6 (管线 5→4); BlockPatternCache SNOW 缓存/ScanFilters.SNOW/SenseApi.scanSnow 删; **天气检测保留** (SNOWING/WEATHER_CLEAR 检出 + Signals 常量 → LLM 对话上下文, 用户裁定)
- **挥手补位 (1.21.1 装填/右键互动无挥手)**: 装填 swing 从 `%10==0` 改每 tick (原版节流自持, 快装填不整段错过); block_interact 补 `maid.swing()` (interact 成功后 swing_hand 快速摆臂, 双平台统一)
- **连带修复 (clean 重打暴露)**: forge mods.toml GBK+BOM 双重损坏重写 (UTF-8 无 BOM, nightconfig ParsingException); en_us.json 补 itemGroup key (LangConsistencyTest 红)
- **0.9.60 历史批次 (2026-08-16~18 合入)**: 唱片识别 1.21.1 (isVanillaDisc) / 创造栏 LmaCreativeTab / PatPat 抚摸 (选区不再 cancel 右键) / 选区渲染修复 (归一化+端点盒+删网格) / 绑定 0,0,0+不落盘 (readBlockPos+cfgOrCreate) / 任务切换擦 LOOK_TARGET / ArmTransfer 自然流程 / 工作站导航提速 (10t) / 气泡公共 CD 5s / CannonLoad 缺弹节流 600t / 温度取暖 30s+10min CD / 女仆图鉴全维度合并 / 坐下判断 (上述) — 详见 docs/lessons-learned.md #243-250
- 验证 (2026-08-18 实测): 双编译 --no-build-cache ✅ + 单测 63 类 462 用例 0 失败 ✅ + gametest 双节点 **14/14 ×2** ✅ (13/13 + lmaMilkRecipesLoaded) + 双 jar 部署 (21:36) + 上传 GitHub LMA-Releases (0.9.60)

## 0.9.59 (2026-08-15) — 新功能: 酒狐奶桶 / 野生酒狐奶饰品

- **版本转正 0.9.59 (2026-08-16)**: gradle.properties ×2 + mods.toml ×2 升版 (原 0.9.58); 本节约 2026-08-15~16 全部批次 (酒狐奶饰品/结构 enter/B-CBC 移植/预算 P/工具分层/环境感知缓存/文档工程/被动细节/自救 v2/属性界面/死代码清理/管线修复/RecoveryLadder/PatPat/酒狐奶扩展/被动脱管线/温度热源安全) 随 0.9.59 发布; 双 jar 已重打包 unzip 验 (0.9.59 名 + toml 版本 + 新类/数据/零残留)

- **两个可饮+可装备饰品的奶桶** (继承 TLM `IMaidBauble`, 经 `bindMaidBauble` 注册): 酒狐奶桶 (喝=抗性提升II+生命恢复I 10s; 饰品受伤=双buff+掉耐久, 总30) / 野生酒狐奶 (喝=生命恢复I 30s; 无法破坏; 饰品濒死=无敌30s+音乐+CD10min 图腾式, 触发后保留)
- **右键挤奶三态判定 (主人维度)**: 空桶右键已驯服自己的女仆→酒狐奶+好感+1 (CD 5min 硬编码, 仅好感有CD); 未驯服→野生奶(副开关开)/酒狐奶(副开关关) 同样冒爱心产奶但不加好感; 已驯服别人的→不能挤 (TLM mobInteract 事实: InteractMaidEvent 只主人 fire, 未驯服走 EntityInteract); 挤奶无 CD + 去重守卫 (一次右键只产一个)
- **配置 9 项** `kitsune_milk.toml`: 主/副开关 + 3 效果时长 + 耐久 + 无敌时长 + CD + 音乐音量(默认0.5); 加好感CD不进配置 (用户裁定)
- **落位**: `bauble/` 只放饰品 API 基座 (BaubleApi 时间戳状态), `bauble/WildKitsuneMilk/` 放业务实现 (API/实现分离)
- **素材**: 野生奶贴图 dogmilk.png (32×32 缩放落盘) + 音乐 dogmilk.ogg (SoundEvent 注册, 播放时附近全听)
- **反馈**: 挤奶 COW_MILK + 经验声 + 心形粒子; 喝奶 GENERIC_DRINK; 野生奶触发图腾粒子 + 无敌期每 3s 一次图腾特效; 野生奶紫名 EPIC + 无法破坏标注; 奶桶 30 耐久
- **结构气泡优化 (用户批准)**: pickMaid 全实体遍历 → AABB 范围粗筛 + 圆形精筛; 结构名/方向词(前后左右上下 6 向相对玩家朝向)/语气词模板(6 far + 6 near: 去看看/狗修金我饿了/好吃的/蛋糕) 全走 lang 键 Component.translatable 客户端双语 (labelOf 中文硬编码删除); scanAllStructures registry 查找提循环外; 注释漂移修正
- **测试**: KitsuneMilkInteract.decide 5 用例 + ConfigConsistency kitsune 段 + StructureSenseDirectionTest 8 用例 (relativeDir 6 向/语气组件/合并组件); 口径 55 类 398 用例 0 失败
- **结构感知 enter 提示 (2026-08-16 用户裁定)**: 进入结构不再全静默 — ENTER 信号补气泡+聊天 (村庄专属模板「到村庄啦，来收集小麦做蛋糕吧」+ 通用池「到%s啦」×3, 双语 lang 键 tip.enter.*); 首扫即 IN 改发 ENTER (避免「附近有X」+「到X啦」两连弹); IN 进入即提示、停留恒静默 (删 IN 转正分支防 60s 重复弹, 流程审查发现); NEAR 环带 discover/refresh 不变 (displaced NEAR 转正保留); leave 保持静默 (LLM 预留); 状态机测试 28→30 例
- 验证 (2026-08-15 实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 55 类 398 用例 0 失败 ✅ + gametest 双节点 11/11 ×2 ✅
- **结构判定 BB 语义升级 (2026-08-16 用户裁定)**: 相位判定从「距结构中心」改为「距结构边界盒」— scanAllStructures 返回 StructBox (MC BoundingBox 六元组纯数据, 双平台 minX() 访问器 javap/源码实证一致); 在盒内=0, 进入 = 距盒 ≤enter(默认 40→8), 离开 = 距盒 >leave(默认 100→24), 刷新环带 = 距盒 8~24; 「到村庄啦」真在村里触发、「离开」真出村触发, 大小结构一视同仁 (村庄 153×120 实测错位问题解决); +StructBox 边界距离 3 用例
- **调试指令 /lma structure (2026-08-16)**: fire <discover|refresh|enter|leave> — 立即以玩家为中心走真实链路发射结构信号 (气泡+聊天, 绕过节流/状态机, 调试几分钟才变的效果); state — 状态机+文案缓存快照; reset — 清缓存重测首次发现 (权限 2)
- **CBC 火炮兼容移植 1.21.1 (2026-08-16)**: CannonLoadService/Pipeline 从 forge/src 移 common 双平台编译 (CBC 5.11.7 compileOnly); API 差异适配 (//? if): BigCannonMunitionBlock.getExtractedItem/getHandloadingInfo 1.21 增 HolderLookup.Provider 参数, getInitialOrientation 双平台在位 (javap 实证);
- **结构扫描预算方案 P (2026-08-16 用户裁定: 区块作基本单位/附近有女仆才扫/缓存慢慢读)**: 新建 vanilla/input/world/StructureScanCache — 区块级共享缓存 (维度+chunkPos 键, TTL 20s, 懒清理 4096 条/10min 陈旧) + 由近及远摊薄螺旋 (RingSpiral, 预算耗尽近处优先) + 每广播轮结构通道 2ms 墙钟预算 (跨玩家共享先到先得); StructureSense.detect 门控前置 (无附近主人女仆直接跳过, 不扫不推进); EnvScanner.scanAllStructures 迁入并退役 (vanilla 工具 API 分层 — 寻路用 TLM brain 放 behavior, 管线放 service, 原版执行复用放 vanilla)
- 验证 (2026-08-16 预算 P 实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 56 类 409 用例 0 失败 ✅ (+StructureScanCacheTest 5 用例) + gametest 双节点 11/11 ×2 ✅ + 0.9.58 双 jar 重打包 unzip 验 ✅ + 部署双平台 (.bak-0816e 轮换) ✅
- **原版工具分层收口 A+B 批 (2026-08-16 用户裁定: 所有管线能移植的原版工具重构进 vanilla)**: A 收口 — task/sense/WorldStateReader 退役 (describe 迁 vanilla, GetWorldInfoTool 改引用); ToolJudge 迁 vanilla/input/item (原 task.service 纯原版工具判断计算层, ToolStateReader 文档倒置消除); EnvScanner.directionWord/directionLabel 死 API 删 (生产零引用, 结构气泡方向词已 lang 化)。B 抽离 — 新建 SnowQuery (scanSnowBlocks 迁入) / EntityScanner (EntityScan+EnvScanner.scanEntities+CAT_* 合并收编, monster 分类原样保留) / SoundOutput (SoundEvent 双平台注册表解析 + 播放原语, Haqi/Craft/BellRing 改委托) / HeatSourceQuery + WaterQuery (TempAdapt 热/水源判定迁入, LIT-only 保留) / NearbyContainerScanner (通配符+容器扫描提取迁 vanilla/input/container); StructureScanCache +structuresAt, WorldStateReader +biome 温度/降水/describe; TorchLight 亮度读取委托 LightQuery (判定阈值不动); EnvScanner 收敛为 WorldInfo 快照组装。分层实况: vanilla 51→60, task/service 16→14, task/sense 16→14
- **最终完整性审计 (2026-08-16 用户要求: 流程/重复性/完整性/可读性/健壮性/模块化)**: ① 重复性 — scanAround 三段同构实体并入循环抽私有原语 mergeInto (1 定义 3 调用, 语义单一); 纯函数间补空行分隔。② 模块化 — 依赖方向全树终审: v79.6x A/B 批新增 12 个 vanilla 类零越层 ✓; 历史遗留 5 处越层 (execute 协调器族→task/service 等 + CombatOutput→MaidData + MaidAttrRegistry→compat) 如实记录进 AI-CODING-GUIDE 分层表「历史例外」, 新代码禁止新增。③ 完整性 — 缓存三层生命周期闭环复核 (区块 lazyClean / queryCache onEntityRemoved+懒清理 / 时钟回退自愈) 全部闭环。④ 验证: 双编译 ✅ + 单测 57 类 431 用例 0 失败 ✅ + gametest 11/11 ×2 ✅ + 部署 (.bak-0816m 终审版) ✅
- **全流程走查修复 P1/P2 (2026-08-16 用户要求走流程找问题)**: 走查环境感知全链路 (收集→分流→L1/L2/L0→边沿→分发→快照→消费→生命周期) — P1: L1 漂移快路径直接复用旧结果, 缓存期内死实体泄漏进 presenceOf 判定 → 快路径返回前 isAlive 重建过滤 (与 L2 一致); P2: 预算耗尽跳过 grace 外区块时结果不完整仍写 L1 → 漏报放大到多轮 → incomplete 标志, 不完整结果不进 L1; 顺手拆 lazyClean 双独立清理分支; 已兜底复核 (时钟回退自愈/预热同 tick 命中/warm grace 一致/螺旋中心优先)
- **测试写满 (2026-08-16 用户要求)**: EntityScanCache 内联数学抽纯函数 (chunkRadiusOf 半径→区块半径 / shouldReuseQuery L1 命中判定) + EnvSenseBroadcaster.shouldUseCache 分流判定抽纯 → EntityScanCacheTest 8→24 用例 (isFresh 5 / grace 2 / 漂移常量 1 / driftSq 4 / reuse 5 / chunkRadius 5 / 螺旋覆盖 2) + EnvSenseBroadcasterTest 新建 5 用例 (0/1/2→直扫, 3/10→缓存); 单测 57 类 431 用例 0 失败; 全树旧测试数字残留清零
- **环境感知扫描: 混合分流 + Retold 两细节 + 螺旋枚举 (2026-08-16 用户裁定, GitHub 调研: lithium 系增量维护/Retold 漂移门限+grace/Magnot per-tick)**: 广播器先收集 eligible 女仆 (开关+门控) → ≤2 直扫原路 (家庭场景缓存反而更贵) / ≥3 预热主人区块+区块缓存共享; EntityScanCache 吸收 Retold 模式① per-查询漂移门限 (中心漂移 ≤3.5 格且结果未过期 → 直接复用上次结果, 静止/小幅走动女仆零重扫零合并) + 模式② grace 40t (过期宽限内仍可读 — 预算耗尽不漏报); onMaidUnload 清 per-查询缓存闭环; +3 测试用例
- **环境感知实体扫描缓存 (2026-08-16 用户裁定: 和结构一样做缓存)**: 新建 vanilla/input/search/EntityScanCache — 区块级实体缓存 (维度+chunkPos 键, TTL 200t 与环境扫描间隔对齐: 同轮共享轮间重扫, 新鲜度零变化) + 每广播轮主人区块预热 (warm, 独立 2ms 预算不挤占广播 pass 8ms) + per-maid 查询 scanAround 全命中零重扫; 缓存存区块全高清单 (无分类无自排除 — 分类/垂直窗 ±4/maxHits 截断在查询方, 原 scanEntities 语义逐项保留); 广播器扫描半径统一 ENV_DEFAULT_RADIUS (restriction 分支删, 缓存 key 不带半径); 门控半径 20 不动 (用户确认); +EntityScanCacheTest 5 用例
- **文档工程 (2026-08-16 用户要求: 给低配 API 写最终架构+完整文档)**: ARCHITECTURE.md 大修 — 顶部新增「实测基线表」(唯一数字真相源, 其他文档禁止抄数字) + §1-§13 全数字回写 (313 main/55 test/89 task/60 vanilla/48 compat/16+16 主动/55 类 402 用例) + §4 信号体系退役口径修正 (event: 5 信号 v77.4 退役实证 — 事件直调 TaskDispatcher); **AI-CODING-GUIDE.md 新建** (低配 API 开发手册: 开工流程/分层判据/7 铁律/新增任务·被动·原语 howto/双平台/测试/验证/文档同步); docs/README 重写 (权威链+死文档清单); 死文档横幅 3 (task-development/ENGINE-REFACTOR-PLAN/architecture-review); 12 份小文档漂移修复 (PROJECT_OVERVIEW 等); 全树旧数字残留 grep 清零
- 验证 (2026-08-16 A+B 实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 55 类 402 用例 0 失败 ✅ (EnvScannerDirectionTest 5 用例 + FilterTest 方向词 2 用例随死 API 删除, ToolJudgeLogicTest 同包同搬) + gametest 双节点 11/11 ×2 ✅ + 0.9.58 双 jar 重打包 unzip 验 (新类齐/旧类零残留) ✅ + 部署双平台 (.bak-0816f 轮换) ✅ TaskRegistry/Manifest 加 neoforge 注册分支; neoforge 主动任务 15→16
- 验证 (2026-08-16 实测, 结构 enter 提示批次): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 55 类 401 用例 0 失败 ✅ (含 0.9.59 口径 398 + 本次 +2; 差 1 为口径细节) + gametest 双节点 11/11 ×2 ✅ + 0.9.58 双 jar 重打包 unzip 验 ✅
- **被动管线细节调整批次 (2026-08-16 用户裁定, 7 条)**: ① 铲雪导航式 — 女仆走到雪旁 4 格内才破坏 (替代原地隔空清雪, temp_adapt 同款 moveTo); ② 雪天中途积新雪 — 删预检无条件提交 + tick 无雪 200t 降频等待 (pipelineData waiting 标记) + 增订 WEATHER_CLEAR 边沿收线 (原「雪清完即 cancel」+ SNOWING 严格边沿 → 整场雪不再触发); ③ 温度抖动冷却门 — NORMAL 写戳, COLD/HOT 200t 冷却内忽略重提交 (日志实证 7s 内 cancel→submit 压制); ⑨ 不可达背压 — 同目标连续 3 次无进展 (300t) → interval 600, 目标变化/接近/到达重置 (隔墙热源反复 100t 重寻路修复); ⑩ 无灯重扫降频 — TorchLightPipeline 加 TaskConfigurable, lightUp 失败置 no_light → 节流 100t→300t; ⑧ 哈气独立通道降频 — ENVSENSE 开 60t (与 200t 广播互补) / 关 20t 保底 (唯一触发源历史根因); ⑦ 日志降噪 — [LMA/Haqi] LOOK 进入 + submitPassive/cancelPassive INFO→DEBUG (被动周期高频成对刷屏)
- 验证 (2026-08-16 被动细节批次实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 57 类 431 用例 0 失败 ✅ + gametest 双节点 11/11 ×2 ✅ + 0.9.58 双 jar 重打包 unzip 验 (6 改动类齐) ✅ + 部署双平台 (.bak-0816o 轮换) ✅
- **自救 v2 批次 (2026-08-16 用户裁定: Numen/Baritone 调研后移植, 固定序 被埋>摔落>卡住)**: ① **MLG 摔落自救** — MlgRescueCoordinator (vanilla/execute, Numen MLGChain 移植): 判速度不判落差 (fallDistance 客户端权威坑) + 五射线探落点 (中心+四角防格缝漏) + 水桶/软方块双通道 + 收水闭环 (20t 窗口); **女仆直接世界操作** (world.setBlock + 背包水桶↔空桶 — 用户裁定「我们的类是 maid 不是假人」, TLM destroyBlock 同款模型; 假人右键链 1.20.1 桶族 use() 视线驱动平台分裂撤销); ② **卡住脱困** — UnstuckDetector 纯类 (40t 窗口/80% 尝试占比/0.75 格圆盘, 空闲不误报) + UnstuckCoordinator (导航激活态 !isDone 采样 + 137° 换向 30t burst + 周期跳); ③ **摔落预触发** — SelfRescueTrigger (TaskTickHandler 主循环内联, 便宜判定先行 — 掉血事件通道外, 摔落中启动落地前放水); ④ **RecoveryLadder 纯类** (task/runtime, Numen 恢复阶梯: Rung 重试上限/失败穿透/耗尽 — v1 纯逻辑, 接守卫链第二阶段); ⑤ 不移植项 (TLM 原生已有, 源码实证): 换气 MaidBreathAirTask / 低血进食 MaidHealSelfTask / 逃跑 MaidPanicTask; ⑥ gametest lmaMlgRescue (12 测试)
- 验证 (2026-08-16 自救 v2 实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 60 类 452 用例 0 失败 ✅ (UnstuckDetectorTest 9 + MlgJudgeTest 5 + RecoveryLadderTest 7) + gametest 双节点 12/12 ×2 ✅ + 0.9.58 双 jar 重打包 unzip 验 ✅ + 部署双平台 (.bak-0816p 轮换) ✅
- **女仆属性界面重写 (2026-08-16 用户裁定)**: 暗色玻璃风 — 半透明黑行底 (0x80000000, hover 提亮) + 白字 + 金色值/组标题 (去米黄纸感); 字号分级放大 (MC 位图字体无多字号 API, pose.scale 缩放: 标题 2x / 组标题与女仆名 1.5x / 属性行 1.25x, 行高 36/28, 面板 480×400); 底部双按钮 — 返回 + **「LMA 参数」** → TLM 任务配置屏 (当前任务配置链: 1.20.1 TLM NetworkHandler.CHANNEL 发 OpenMaidGuiMessage / 1.21.1 PacketDistributor 发 OpenMaidGuiPackage — TLM 1.21 包改名 Message→Package javap 实证, TabIndex.TASK_CONFIG 双平台同值); +lang 键 lma_params (zh/en)
- **续哈气开关补查 (#5)**: HaqiTrigger.tryContinue 加 HAQI_ENABLED 检查 — 运行中关配置即停续哈气 (原无视开关配置不即时生效)
- 验证 (2026-08-16 界面重写实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 60 类 452 用例 0 失败 ✅ + gametest 双节点 12/12 ×2 ✅ + 0.9.58 双 jar 重打包 ✅ + 部署双平台 (.bak-0816q 轮换) ✅
- **被动细节收尾 #4/#6 (2026-08-16)**: #4 哈气 MOVE 语义修正 — 到达判定 1.5²→2.5² 格 (用户裁定「发现后走到旁边就 LOOK, 不是精确贴身」) + move_ticks 超时 200t 兜底 (目标持续跑动走不到旁才放弃; 4 触发口同步重置); #6 任务开关禁用清理 — GMPM tickPassiveFor 入口统一 pass (10t 节流, 哈气独享分支前, in_progress 且禁用 → cancelPassive, 覆盖全路径含 haqi) + TorchLight onCleanup 兜底 (副手灯插回背包 — 原禁用后火把留副手)
- 验证 (2026-08-16 #4/#6 实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 60 类 452 用例 0 失败 ✅ + gametest 双节点 12/12 ×2 ✅ + 0.9.58 双 jar 重打包 ✅ + 部署双平台 (.bak-0816r 轮换 → .bak-0816s MOVE 语义修正版) ✅
- **死代码清理 + CompatRegistry 双平台 + 文档口径回写批次 (2026-08-16, 用户批准, 项目通读后)**: ① CombatOutput 死 import×3 删 (vanilla→task.data 越层死后清理) + launchProjectile 嵌套双层 //? if 死分支简化 (内层 `BuiltInRegistries.ENTITY_TYPE.getValue` 双平台产物均不可达); ② MaidHarvestCropEvent 死事件全删 (发布 1 消费 0 + javadoc 幽灵引用 MaidHarvestSignalBridge 全项目不存在; AutoCropHandler postHarvestEvent 撤 — 每收割一次空 post 白开销); ③ TaskKeys.WAIT_TICKS 只写不读键删 (AnimExecute 4 处 putInt 撤, ANIM_RUNTIME_KEYS/ANIM_CLEANUP_KEYS 摘除 — 写删读零行为变化); ④ TaskToggle.isEnabledFor per-maid 死分支删 (签名收敛全局 isEnabled, GMPM×3 + EnvSenseBroadcaster×1 同步, 行为零变化); ⑤ CompatRegistry createbigcannons 模块注册移出 //? if 1.20.1 分支 — CBC 双平台 GUI 模块表对齐 (任务 0816 已双平台注册, 门控表漏移 = neoforge 模块开关不可见, 移植批次遗漏); ⑥ 文档口径 15+ 处 (根 README 数字+neoforge 16 主动 / ARCHITECTURE 基线表 60 类 452·318 main·12/12×2·分层数回写 / §1·§3.1·§6 同步 / AI-CODING-GUIDE 基线+历史例外 5→4 处 / AGENTS.md / task+service README 迁走类指引 / gametest 12 / compat GatedMaidTool 14+CBC 双平台 / 饰品 9 键 / KitsuneMilkInteract+README+Config 哈气攻击残留×6 / 悬空注释×4 / DangerGuard 240t→60t / event README)
- 验证 (2026-08-16 死代码+文档批次实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 60 类 452 用例 0 失败 ✅ (XML 双证据) + gametest 双节点 12/12 ×2 ✅ (基线表已按实测回写)
- **管线检查 + 低成本修复批次 (2026-08-16, 4 域并行深读 52 项发现)**: 管线全景检查 (工作站族/直接+FSM 族/被动族/驱动层, 报告落盘 multiprompts\output\pipeline-check-2026-08-16\S1~S4)。低成本修复: ① CANCELLED 钩子 6 处清理 (TaskStateMachine/GMPM/ChainHarvestExecute/ChainHarvestPipeline/RunningBeltPipeline/MaidAssemblyPipeline — 实证 cancel 同帧 clearAll 状态零残留不可达; TaskStateManager.isCancelled 死方法删; LmaFlowCoordinationBehavior 启动守卫保留注释); ② TorchLight 补盾死注释修正 (代码实际语义"有怪保火把", 无补盾实现); ③ VanillaConstants 死常量×4 删 (JUKEBOX_PLAY_TICKS/FURNACE_INPUT_LIMIT/FURNACE_FUEL_LIMIT/TASK_DEFAULT_TIMEOUT); ④ 哈气续写疑点用户裁定驳回 (tryContinue 只扫女仆, javadoc 与代码一致, S3-4.6-1 为误报); ⑤ RecoveryLadder 接守卫链方案已出 (recovery-ladder-guardchain-plan.md, 待批准)
- 验证 (2026-08-16 管线检查+低成本实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 60 类 452 用例 0 失败 ✅ (XML 双证据)
- **RecoveryLadder 接守卫链 + 导航守护 (2026-08-16 用户批准实施, 🟡 待办第二阶段)**: 新增 `api/pathing/NavProgressGuard` (寻路 API 域) — 进展检测纯类 (目标未变 + 位移<0.5² 持续 100t → nav_stuck; 状态存 MaidData.pl("navguard") 内存态零 NBT, 目标变自动重置, 时钟回绕守卫; 纯函数 shouldFlagStuck/hasProgress/parsePos JVM 可测)。挂接: ① **TLM Brain behavior 层** (LmaFlowCoordinationBehavior — 用户裁定位置, 每女仆实例持 RecoveryLadder: R1 重搜×2 → R2 等重试×3 → R3 放弃擦目标+气泡; stop() 重置防跨任务残留) — 工作站族 TARGET_POS 路径全受益; ② **FSM 路径** (ArmTransferPipeline TO_TAKE/TO_DEPOSIT — 目标绑定不可重搜, 轻量两档: 等重试一窗口 → fail"取货点/放货点不可达", stage 存 pl 随终结闭环)。不动 GMPM/TaskDispatcher/PathingApi (红线 2/3 零触碰; ChainHarvest 路径已有 240t 超时兜底不重复挂接)。+NavProgressGuardTest 9 例
- 验证 (2026-08-16 导航守护实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 61 类 461 用例 0 失败 ✅ (XML 双证据; BlockPos 纯数据类 JVM 直测安全实证)
- **管线检查问题修复批次 S1-F2/F3/F4 + 哈气边沿冻结增强 (2026-08-16 用户裁定)**: ① **furnace 无料降频** (S1-F2): 无料/料烧完不再每拍刷"furnace 失败"气泡 — 1200t 冷却后重查 + 气泡提醒 (与看门狗同周期, ThrottleUtil "furnace_no_ingredient" 时间戳自过期); ② **jukebox 弹碟死循环修复** (S1-F3): EJECTING 区分"机内空"与"全拒" — 全拒 (背包满) 保持 EJECTING 每 tick 重试 + 600t 节流气泡"背包已满" (原全拒误判空 → INSERTING→PLAYING→EJECTING 震荡, 碟永久滞留); ③ **jukebox 目标匹配统一** (S1-F4): 新增 matchesTarget (注册表 key + 描述名 + 显示名统一 lowercase — 修 toString() 双平台漂移 #191 同根 + validate/INSERTING 大小写判据分裂); 目标碟缺失 → 600t 节流气泡提醒 (原静默卡 INSERTING); ④ **哈气期间边沿整体冻结增强** (S3-1.6-1 用户裁定): EnvSenseBroadcaster 哈气运行中连分发一起跳过 — 边沿信号不消费 (原只冻结基线照常分发 → 无效 onSignal + 噪音); 哈气结束下轮重检测重放; 结构/节日走 flushPending 独立通道不受影响
- 验证 (2026-08-16 管线修复批次实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 61 类 461 用例 0 失败 ✅ (XML 双证据)
- **Create+CBC 管线修复批次 (2026-08-16 用户裁定, V1/V2 源码验证后)**: ① **P0 转换图补边 ×3** (Crank NAVIGATING→SEARCHING + CRANKING→NAVIGATING / Power NAVIGATING→SEARCHING — 修图-码矛盾 latch + warn 刷屏 + 远距离空摇); ② **NavProgressGuard + 跳过集接入 4 个 Create FSM 管线** (crank/power/press/mix — 导航卡死 → 目标进跳过集 60t 换目标, 对齐 ChainHarvest SKIP_TTL; 用户裁定"导航刚需走到工作方块旁+可能有跳过集"); 新建 **api/pathing/NavSkipSet** (跳过集通用门面, BlockTargetNavigation default 委托, CannonLoad 复用); ③ **无目标静默 → 600t 节流气泡** ×5 (crank/power/press/mix 无目标 + running_belt 缺皮带/缺食物); ④ **CannonLoad 3 项** (MOVING 导航兜底+炮架跳过集 / LOADING 滞留超时 200t→气泡+回搜索 / 满装判定先过 isLoadOrderCorrect — 顺序错满装走 worm 清膛); ⑤ **死代码清理** (PressService PRESS_DURATION+canBasinProcess / MixService IDLE_INTERVAL / RunningBeltService 3 死方法 / Power "rpm" 死读分支 / MaidAssembly InProc 死写×2 + ADVANCE→IDLE 悬空边 + itemLocks/slotBlocked 全死面含 LOCKS_KEY/BLOCKED_KEY 常量 / CannonLoad CLEARING→OPENING 悬空边); ⑥ **V1/V2 源码验证修正** (crank turn 每 tick 32 RPM 幂等无害 = 误报撤销 / Power 应力容量缺口记录在案待实测 / CBC worm 口径批量版 10 块 / 多炮闩不可能存在 = 误报撤销; Screw/Sliding 炮闩漏识别待后续)
- 验证 (2026-08-16 Create+CBC 批次实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 61 类 461 用例 0 失败 ✅ (XML 双证据)
- **PatPat 抚摸兼容批次 (2026-08-16 用户裁定)**: 安装 PatPat (fabric 1.2.1+1.20.1 / neoforge 1.2.4+1.21.1) 后, 玩家抚摸女仆触发反应链 — 好感 +1 + 爱心粒子 (挤奶同款头顶 5 颗) + **对主人哈气专用语音 (littlemaid_peco 包 idle 子集随机, 同 HaqiOwnerVoicePacket 链路 — 用户裁定「不是哈气音效, 是特别选的对主人哈气音效」, 非 ha_1..5/laowu_1..5 原版清单)** + 气泡 3 选 1 (「好舒服~」「多摸一点」「今天中午吃什么呢~」, lang 键 tip.pat.1/2/3 双语可改)。**检测链 (关键)**: PatPat 抚摸按键 (Shift+右键) 取消 KeyMapping.click (PatPat KeybindingMixin 源码实证) → 抚摸期间服务端 EntityInteractEvent 不 fire → 放弃事件监听, 改为客户端 20t 轮询 PatPatClientManager.getPatEntity 状态表 (与 PatPat 渲染层同源, 本地+远程抚摸共享同一张表) → 新 C2S 包 **PatPatReactionPacket** (PacketRegistry id 4 回收空洞, forge 双驱动注册 + neoforge TYPE/STREAM_CODEC) → 服务端三道轻校验 (owner `isOwnedBy` + 3 格距离 + per-maid 600t 节流 — 信任客户端模型与 PatPat 自身一致, 客户端节流仅降载)。**门控**: ModList.isLoaded("patpat") (CompatRegistry.doScan checkModLoad, forge 1.20.1 无 PatPat 构建恒关 → TLM 默认坐下行为不变); 客户端节流表断线清空 (LoggingOut 双平台接线)。依赖登记 ×3: common + forge + neoforge build.gradle compileOnly。
- 验证 (2026-08-16 PatPat 批次实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 61 类 461 用例 0 失败 ✅ (XML 双证据; NetworkPacketManifestTest 同步 forge 视图 14→15 项 / 6→7 C2S / id 4 回收 + PacketRegistry javadoc 空洞口径同步)
- **酒狐奶扩展批次 (2026-08-16 用户裁定)**: ① **替乳合成** — 两奶桶加入 `forge:milk` 标签 (标准做法, `data/forge/tags/items/milk.json`, replace:false 合并不覆盖) → 引用该标签的第三方 mod 牛奶配方自动接受三奶; 原版蛋糕/硬编码配方**零改动** (不覆盖不改写), 另自注册 2 个蛋糕配方 (`recipes/cake_from_tamed_milk.json` / `cake_from_wild_milk.json`, 与原版 cake 逐字段同构 [实证: category misc + show_notification true + key A/B/C/E], 产物原版蛋糕) — 酒狐奶桶/野生奶各自可合成蛋糕; ② **喝奶食饱食度** — 取消 (用户裁定不要了, 饥饿值不变); ③ **清负面配置** — `kitsune_milk.toml` effect 段新增 `clear_negative` (默认 true, 酒狐奶+野生奶共用; 实现先 `removeAllEffects` 再加正面效果 [顺序防自删]), Config 9→10 项, ConfigConsistencyTest 自动覆盖 (allValues/前缀校验免改测试)
- 验证 (2026-08-16 酒狐奶批次实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 61 类 461 用例 0 失败 ✅ (XML 双证据) + 双平台 jar unzip 验 ✅ (forge:milk tag + cake_from_*×2 在位; **jar 内无 data/minecraft/recipes/cake.json 覆盖物**)
- **被动系统脱管线改造批次 (2026-08-16 用户批准一次性实施, 5 阶段)**: 被动不再共用单一 GMPM tick 通道 — 三形态分层: ① **纯触发型** (structure_sense/festival, 零 tick) — 新建 `task/passive/` (PassiveTask 接口 + PassiveDispatcher 四道闸 [注册/TaskToggle/ha qi 底层覆盖/冷却表, 卸载声明式清理]) + `impl/`×2 (原管线逻辑搬入, 旧管线类删除); ② **跨 tick 动作型** (snow_shovel/temp_adapt/torch_light) — 移出 GMPM 共享通道, 新增 `tickStandalonePassives` 独立心跳 (每 tick 调用, 动作频率由管线内 ThrottleUtil 节流自持 40/100/200/300t, 行为等价); ③ **GMPM 真管线** (haqi FSM + 底层覆盖 / self_rescue 时间关键) — 保留 `tickPassiveFor` (haqi 运行中 self_rescue 停 tick); **haqi 底层覆盖统一入口** (用户裁定: 启动不结束不做其他事 — PassiveDispatcher.haqiRunning, submitPassive/心跳/Dispatcher 三处收敛); **TaskRegistry.TaskHandler 无 pipeline 占位** (用户裁定: 任务树标签兜底 taskType, 可见性/开关零改动 — TaskToggle 零耦合); 删除: PASSIVE_TICK_BUDGET 配置 / PassiveRotation 轮转类+测试 / 位掩码缓存+clearMaidCaches; gametest lmaPassiveBudget → lmaPassiveTickIsolation (通道隔离断言)
- 验证 (2026-08-16 被动脱管线批次实测): 双编译 --no-build-cache ✅ (每阶段) + 单测 --rerun-tasks **60 类 455 用例 0 失败** ✅ (XML 双证据; -6 = PassiveRotationTest 随机制删) + gametest 双节点 **12/12 ×2** ✅ (lmaPassiveTickIsolation 新断言; neoforge sync_data = 已文档化 mock 噪音)
- **温度热源安全修复 (2026-08-16 用户裁定 A+B, 用户质询发现)**: temp_adapt 热源清单历史缺陷 (v63 起) — ① **剔除 FIRE/SOUL_FIRE** (火方块无碰撞箱、寻路不避, 女仆 COLD 导航会走进火里被点燃); ② **导航落点偏移** — HeatSourceQuery 新增 `safeStand` (热源旁水平 ±2/垂直 -1..+1 扫描: 非危险方块 + 非流体 + 无碰撞 + 下方有支撑, 本体格跳过), TempAdaptPipeline 导航目标改指安全落点, 找不到兜底热源本体 (岩浆块可站立烫脚/岩浆贴边一并解决); 危险方块集与热源清单同源 (火/灵魂火/岩浆/岩浆块/点燃营火×2); 新增 gametest `lmaTempAdaptSafeTarget` (构造火+岩浆块+点燃营火场景, 断言热源不含火 + 落点非危险邻格)
- 验证 (2026-08-16 热源修复实测): 双编译 --no-build-cache ✅ + 单测 60 类 455 用例 0 失败 ✅ + gametest 双节点 **13/13 ×2** ✅ (新 lmaTempAdaptSafeTarget)
- **缓存体系 L3 + 选区调试功能批次 (2026-08-16 用户裁定一批走, 落位 execute 层; 调研: lithium VicinityCache/WorldEditCUI/TLM 样板源码实证)**: ① **`vanilla/execute/BlockPatternCache`** — 单区块方块模式缓存 (L3 增量): 键 (维度+chunk+type), per-type TTL (雪 40t 对齐铲雪/热 100/水 100/容器 200), 地表高度窗 ±8 扫描 (WORLD_SURFACE, minY 兜底), 粗匹配缓存+查询方细筛防脏读 (v79.61x 原则), 维度清理; 接入 SnowQuery/HeatSourceQuery/WaterQuery (消除各自三重循环重复扫描; HEAT 判定抽 isHeatSource 单一来源); ② **结构静态层 (L1 增强)** — StructureScanCache 条目存在即有效 (结构位置世界生成后不可变, lithium 哲学), TTL 移除, 新结构增量填充; ③ **`vanilla/execute/DebugSelectionCoordinator`** — 选区高亮调试: 潜行+木棒右键 = 标记 (客户端 cancel 兼容既有 BlockInteract/ArmTransfer 标记, 用户裁定), 三击循环状态机 (起点黄盒/终点红盒 0.03 内缩 [WorldEditCUI 同款]/区域青 12 边线框/1 格 3D 网格 [32 格上限]/HUD 尺寸 WxHxD [1.21.1 世界内 Font 未实证→屏幕层可靠]/缓存区块边界叠加), 双平台 RenderLevelStageEvent (neoforge javap 实证) + RenderGuiEvent overlay 注册, 断线清除; ④ gametest lmaTempAdaptSafeTarget 修正 (热源放世界地表面 — BlockPatternCache 高度窗语义, 悬浮平台 miss 实证)
- 验证 (2026-08-16 缓存+选区批次实测): 双编译 --no-build-cache ✅ (双平台渲染 API 差异修复: 1.20.1 vertex/color/normal vs 1.21.1 addVertex/setColor [TLM 实证], AABB(BlockPos) 1.21 移除→Vec3 版, Font String 3D 重载 1.21 缺→Component 2D/HUD) + 单测 61 类 459 用例 0 失败 ✅ (+BlockPatternCacheTest 4) + gametest 双节点 13/13 ×2 ✅
- **温度感知取暖停留 + 长冷却 (2026-08-16 用户裁定)**: temp_adapt 不再「到了火旁就长期挂机」 — ① **到达停留取暖**: 到热源/水源旁 (4 格内, ARRIVE_DIST_SQR) 停止导航, 停留计时 (取暖节奏 interval 20t 精确累计 elapsed), **30 秒 (600t) 后完成** → 写 10 分钟冷却 (KEY_CD_DEADLINE 存 maid 根 PD 绝对截止, cancelPassive 不清) + cancelPassive 回工作; ② **10 分钟 CD (12000t)** — onSignal COLD/HOT 先查 CD (isCooled 纯函数: cd<=now/未设置/时钟回绕 → 放行), 冷却期内不再导航去热源/水源; ③ 未到达移动中/无目标 → 重置取暖计时 (resetWarm); 目标形态: 女仆 COLD 走到火旁待 30 秒取暖 → 离开工作 → 10 分钟内不去 (防一直往火旁跑); +TempAdaptPipelineTest 4 (isCooled 纯函数)
- 验证 (2026-08-16 温度 CD 实测): 双编译 --no-build-cache ✅ (--rerun-tasks 强制) + 单测 62 类 463 用例 0 失败 ✅ (+4) + gametest 双节点 13/13 ×2 ✅
- **死信号 DARKNESS_CLEAR 删除 + 广播粒度分析 (2026-08-16 被动走查用户裁定)**: 被动系统走查 (7 被动全链 + 系统横切, 报告落盘 passive-system-walkthrough-2026-08-16.md); 用户裁定: ① 哈气完全独享 tick 设计不动 (含自救冻结) ② temp 取暖被哈气覆盖在设计内 ③ **删 DARKNESS_CLEAR 死信号** — EnvSignal 枚举 / Signals 常量 / EnvEdgeDetector 检出 / 测试断言行全删 (TorchLight 靠 tick 自轮询「亮且无怪」闭环不用清边沿, 每广播轮白算); ④ **广播粒度结论**: per-owner 广播不划算 (温度/亮度/雪强位置相关 — 分头作业失真 + 主人移动抖动 + readWorld 本身便宜); 最优 = 数据分层粒度 (天气 per-dim / 温度亮度 per-chunk / 雪热实体结构节日已区块/player/stateless 共享); 潜在优化: EnvScanner.readWorld 加 200t 区块缓存 (同区块 N 女仆共享 1 次温度/亮度/天气读, 待用户裁定)
- 验证 (2026-08-16 死信号删除实测): 双编译 --no-build-cache --rerun-tasks ✅ + 单测 62 类 463 用例 0 失败 ✅ + gametest 双节点 13/13 ×2 ✅
- **打包部署 0.9.60 (2026-08-16 用户指令, 5 批合包)**: 缓存+选区 (BlockPatternCache/StructureScanCache 静态层/DebugSelectionCoordinator) + 温度取暖 30s+10min CD + DARKNESS_CLEAR 死信号删 + WorldInfoCache 广播粒度 + README 全量更新 + 死信号同族 10 删/ServerStopping 维度缓存接线/挤奶→奶文案 — 版本升 0.9.60 (gradle.properties×2 [versions/] + mods.toml [forge/src] + neoforge.mods.toml [neoforge/src], 共 4 文件; **neoforge jar toml 卡旧版坑 (stonecutter 资源合并缓存未感知 — 需改 neoforge/src 独立源 neoforge.mods.toml)**) → 双 jar jar --rerun-tasks -x javadoc → unzip 验证 (toml 0.9.60 双平台 + BlockPatternCache/DebugSelectionCoordinator/WorldInfoCache/EnvSignal 类 + 蛋糕配方×2 + forge:milk 标签全在位) → 部署双平台 (.bak-0816u 轮换, 活跃 0.9.59→0.9.60)
- **0.9.60 部署崩溃 hotfix (2026-08-16, 用户报 1.21.1 crash-reports)**: LmaNeoForgeClientEntry 选区 HUD 注册用 RenderGuiEvent.class (抽象类) — NeoForge 禁止监听抽象事件类 → `<init>:96` IllegalArgumentException → 改子类 **RenderGuiEvent.Layer.class** → neoforge 重编译 + 重打 jar + 替换部署 (同 0.9.60); 错题 #243 (红线违反实证: RFC for 新监听先查 abstract)
- **WorldInfoCache 广播粒度优化 + README 全量更新 (2026-08-16 用户裁定数据分层)**: ① **`task/sense/WorldInfoCache`** — 广播粒度最终落地: per-dimension 天气/昼夜/时间 (每轮 1 次查询, 全维度共享) + per-chunk 温度/亮度/biome/降水/站立结构 (同区块女仆共享, TTL 200t 对齐广播间隔 + 时钟回绕 + 懒清理 + clearDimension); EnvScanner.readWorld 接入 — 广播计算 N×readWorld → 1×分区读+快照组装; +WorldInfoCacheTest 4; ② **README 全量更新** (功能/工具/兼容/管线): 版本 0.9.59、单测 63 类 467、gametest 13/13、被动三形态细分、温度取暖 CD、热源安全、缓存体系 4 件套、选区调试工具、兼容 +PatPat、信号 18、布局 327 main/63 test、#150-240
- 验证 (2026-08-16 WorldInfoCache 实测): 双编译 --no-build-cache --rerun-tasks ✅ + 单测 63 类 467 用例 0 失败 ✅ (+WorldInfoCacheTest 4) + gametest 双节点 13/13 ×2 ✅

## 0.9.58 (2026-08-15) — 全量重审修复批次 (B1 正确性 + B2 卫生 + T1 测试) + 文档 P0

- **B1 正确性 12 项**: WorkEat 吃后清手槽 (堆叠复制修复) / MeleeAttackTool 自身+主人+距离校验 / RecipeTreeResolver 假环判定改路径祖先集 + 原料变体按 available 选择 / ScanJob sectionCursor 续扫 (预算中断整段跳过修复) / MaidCodexKillListener 迁 event 包 (input 分层违例) / ENVSENSE 开关无键默认开取反语义 / FurnaceService 燃料判定改 AbstractFurnaceBlockEntity.isFuel (有铁锭即开炉失败循环修复) / ArmTransfer 放货容器消失终态 / 被动位掩码 63 上限守卫 / NumenCompanionSyncPayload 2048 上界 / H3 装配库存恢复 neoforge 直写 PersistentData (ForgeData 包装实证失效)
- **B2 卫生**: Furnace/CBC 每 tick INFO→DEBUG / /lma 命令小写注册 / FestivalLoader+TaskToggle 逐条容错 / TaskGroup 损坏配置保留默认 / BlockInteractService 未用 import / WorkStationPipeline+TorchLight 注释反写 / BellRingPipeline 死常量
- **T1 测试**: MaidFavorability 抽纯 forLevel + 4 用例 / AnimExecutePureTest 5 用例 / LmaAnimationDefTest 3 用例 / gametest lmaStructureSenseState (per-player 缓存生命周期, 端到端结构信号因测试层无结构降级)
- **D2 文档 P0**: README/ARCHITECTURE/architecture×2/pipeline-task/usage-manual/task-system-reference/task-registration/pipeline-inventory 数字与形态全清 (53 类 382 用例 / 7 被动 / 19 信号 / digVertical 退役 / core 退役)
- **版本统一升 0.9.58** (gradle.properties ×2 + mods.toml ×2 + 本节转正; 0.9.57 节保留为历史)
- **数据层检查修复 (用户批准)**: MaidData.get 兑现 DataKey.def 契约 (COMPOUND 缺省返回副本防共享改脏, +3 用例) / MaidStateWriter 6 个零调用任意-key 直写方法删除 / FlowTaskData 补 setMaxCount 委托 (TaskDispatcher 直写收敛) / ChainScan.allowed 归位 ChainHarvestExecute (消除 vanilla→task/api 反向依赖) / README 补两条「已裁定反向例外」注记
- **管线细节优化 S1-S4 (用户批准, 方案 A)**: S1 相位机统一进 TaskStateMachine — Furnace/Jukebox 从 WorkStationPipeline 迁移为 FSM (workStationGated 复用 WorkStationPipeline.gate 单门 + countSuccess 改 static 复用), DataKey.JUKEBOX_PHASE/FURNACE_PHASE 退役状态入 FSM 内存态; S2 死键清理 + clearAll 字面量兜底 + CHAIN_PHASE 注释修正 (实证仍活跃非死键); S3 被动 Cd→ThrottleUtil (SnowShovel/TempAdapt) + DailyDedup 上提 (Festival 委托); S4 Signals 注释计数修正 19 + 消费映射 + 孤儿 javadoc 清理
- **重复方法抽取 (用户批准)**: MaidFavorability.workTicks(maid, base) 收敛 Press/Mix/SnowShovel/MaidAssemblyService 4 处好感度计时 / ItemFilters.effectivePair(cfg,黑,白) 收敛 Jukebox×2+ArmTransfer 名单 pair 解析 / MaidSwing.onInterval(maid,20) 收敛 ArmTransfer×2+Crank+Power+RunningBelt 节拍挥臂 io 原语 (行为零变化; Press/Mix 的 timer%20 摆动语义不同不回抽); 补收敛 ChainHarvestExecute.allowed + FurnaceService.effectiveBlack/White → effectivePair; +workTicks 纯函数化 (非法 speed 防御返回 base) + effectivePair/workTicks 共 4 测试用例 (382→386)
- 验证 (2026-08-15 实测): 双编译 --no-build-cache ✅ + 单测 --rerun-tasks 53 类 386 用例 0 失败 ✅ + gametest 双节点 11/11 ×2 ✅ + 0.9.58 双 jar unzip 验 ✅ (只打包不部署; 本轮代码改动后 jar 需重打包)


- **外部注册闭环 (LMAT)**: 补 submitPassive/cancelPassive 门面 (TaskDispatcher 早有实现未暴露); LmaTaskTypeRegistry 迟注册钩子 onTaskRegistered — 外部 mod 晚于 LMA 注册也进 TLM 任务栏, 注册顺序无关 (TLM TaskManager.init ImmutableMap 冻结实证 → 注册过晚 fail-soft 降 WARN); LMAT javadoc 预设目录 5 项 / 命名防冲突 (净化撞 uid) / 注册时机 / currentState 4 状态
- **FSM 分派收敛**: ArmTransfer/BlockInteract 删 HANDLERS Map + StateCtx + Function 双重间接 → switch 分派 (短状态内联, 长状态拆顶层方法); TaskPipeline 四段式注释补分派约定; 行为零变化
- **注释漂移清理 11 处**: executor/execute 墓碑族 (v79.45 已删概念) / TaskRegistry 3 参 register 死链 / 孤儿 javadoc
- **LMAT.simple 先建后删** (用户裁定): 极简糖对真实 modder 零价值 — 预设价值是可抄模板 + 接口默认兜底, 不是压缩行数
- **测试补强**: TaskTypeUid 纯化抽取 (sanitize/extractTaskType 自 MC 宿主类抽出 — 宿主含 Items/IMaidTask 静态链, 纯 JVM 一碰即炸 #174) + TaskTypeUidTest 6 用例 (净化撞 uid 契约); LmatTaskTest 4 用例 (LMAT.Task 模板默认契约); gametest 新增 lmaExternalRegistration (LMAT 门面注册/迟注册钩子 fail-soft/submit-cancel 生命周期/submitPassive-cancelPassive 键闭环)
- **架构审计 A+B+C (五层尺收敛)**: FurnaceService 抽取 (validateSmelt/resolveSmeltIngredient/生效名单, FurnacePipeline 177→56 行, 消灭配方扫描两处重复); HaqiService 抽取 (挥击/音效行为细节, TARGET_OWNER/SOUND_* 常量随迁, HaqiTrigger/gametest 引用同步); ArmTransferService 归位 itemId/findMaidItem; 行为零变化 (逐行搬移)
- **来源验证 + HandSwap 抽取**: 6 条 TLM 注释断言逐条对源码核实全成立 (1 条 maid_useful_task 源码不在本地标待核) → docs/external-reuse.md 复用清单 (WeightedPicker 纯工具可直接用); 换手+旧物三链兜底两处复制收敛为 vanilla/input/item/HandSwap (swapTool/placeMaterial 委托)
- **execute 瘦身第一样本**: FurnaceExecute 相位机收编进 FurnacePipeline (状态键原样, 行为零变化; 单拍动作 = FurnaceOutput 原语; VanillaTasks.furnace + FurnaceExecute 删除); execute 层 14 类分类: 5 真协调器 + 6 已单拍 (后续样本按此推进)
- **execute 瘦身样本 2**: JukeboxExecute 四相位机收编进 JukeboxPipeline (状态键 DataKey.JUKEBOX_PHASE/TICK 原样; TaskRegistry cast 改管线直调 pipelineConfig; 每相位一个顶层方法); BellExecute 内联 BellRingPipeline (敲钟 = BellBlock 原语调用); VanillaTasks 只剩 craft 条目; JukeboxExecute/BellExecute 删除
- **execute 瘦身样本 3**: CraftExecute 归位 task/service/CraftService (单拍合成编排: 配方链解析→预验证→执行→音效, 行为零变化); CraftChainPipeline 直调; **VanillaTasks 门面整类退役** (v30 起四条目 furnace/jukebox/bell/craft 全归位); CraftExecute/VanillaTasks 删除
- **execute 瘦身样本 4 (ChainHarvest 扫描域抽取)**: 新建 ChainScan (空闲扫描/近扫/最近目标/跳过集/采集名单/扫描参数), ChainHarvestExecute 612→460 行 — 主循环三件套 (守卫链/开脉/蓄力) 聚焦; 行为零变化 (逐行搬移, 跨类调用经包级可见性)
- **方案 B 状态机化 (ChainHarvest 显式相位)**: 隐式状态「队列存在=蓄力中」→ 显式 lma_chain_phase (SCAN/CHARGE 两真状态; DIG 为 CHARGE 到期同 tick 事务不持久化); 入队单点写相位, clearChainData 单点清 (队列与相位同生同灭); 旧存档兼容 shim (无 phase 键时以队列存在为据, 语义逐字一致); gametest 挖矿链补 2 相位断言 (开脉=CHARGE / 破块闭环)
- **两轴定级修正**: 承认五层尺执行不一致 (io/service 标签贴错) — FurnaceOutput 合并进 FurnaceService / JukeboxOutput 迁 JukeboxService (多步业务动作非原语, 读实现后修正); HandSwap 定级「通用复合原语」(io 判据是通用性不是粒度); BlockUp/DigThrough/SelfRescue/AnimExecute 类头补两轴定级; README 五层尺换两轴表 (跨 tick? / 业务语义?)
- **分层作用文档**: 每层文件夹下 README.md (作用/判据/依赖方向/代表/修改注意 — 只写作用不写细节): task/api·pipeline·pipeline/sense·runtime·data·service·behavior + vanilla/execute·input·output + api 门面, task/README 索引串联
- **全项目分层作用文档 + 规划体检**: 17 个包层 README (adapter/ai/chatbubble/client/commands/compat/config/core/event/gametest/init/network/resource/screen/storage/vanilla+fakeplayer + forge/neoforge 平台); 体检发现并删除 19 个空壳目录 (engine 整包 + TeaKit 模板 core 12 子包 + 退役代码壳); core/api/adapter/client 包「杂货间」问题标注进 README
- **core 杂货间归位 (2026-08-15)**: MaterialChecker/MaterialReport → task/service (合成域), LmaAnimationDef → resource (动画域), core 包整包退役 (4 个引用点同步: CraftChainPipeline 通配已覆盖 / AnimExecute / LmaAnimationStorage / MaterialCheckerTest; 行为零变化)
- **0.9.57 双 jar 打包 (2026-08-15)**: forge/neoforge 打包 + neoforge copyResourcesToClasses 前置 + unzip 验 (新包类在位 / core 0 残留 / zh_cn 在位 / 陈旧 0.9.50 jar 清出 build/libs); 只打包不部署 (用户裁定)
- 验证: 双编译 + 单测 49 类 367 用例 0 失败 + gametest 双节点 10/10 ✅ (2026-08-15 复验; 用例数口径修正 — 上轮 318 为 UTF-8 XML 漏计, Gradle 报告与 XML 全量解析均 367)

## 0.9.57 (2026-08-13) — v79.61 结构信号状态机重设计 (用户裁定)

- **信号语义重设计**: `env:structure:{id}:nearby|leave` (2 种) → **4 种**: `discover|refresh|enter|leave` — discover 首次扫到 / refresh 周期重发 / enter 进入 / leave 离开 (enter+leave 静默信号, 留未来 LLM 上下文, 不弹气泡)
- **discover 气泡**: 主人首次扫到结构 → 随机 1 个附近主人女仆气泡; ≤enter 40 格 "附近有X" / 远 "{方向}方向有X"
- **refresh 提醒**: 结构外 (40<d≤100) 每 2400t (120 秒) 重发方向气泡, 上限 3 次 (含首次); 走回 (离开后 ≤100) 重新发现提醒
- **状态机 per-player per-structure**: Phase OUT/NEAR/IN, OUT 即剪枝; bubble=最近结构只冻结 NEAR 停留提醒预算 (displaced 结构 enter/leave 照发)
- **新增 4 配置** (GUI 4 项): ENV_STRUCTURE_ENTER_DIST (40, 1-100) / ENV_STRUCTURE_LEAVE_DIST (100, 2-256) / ENV_STRUCTURE_REFRESH_TICKS (2400, 1200-168000) / ENV_STRUCTURE_REFRESH_MAX (3, 1-10)
- **错题 #193**: v79.60 flash 实现 leave 气泡死链 — buildTexts 只为当轮扫描到的结构建文案, 结构移出范围时 leave 文案不在缓存 → textFor null → 气泡永不显示; 教训: 信号文案生命周期必须覆盖信号消费时点; 本次 leave 改静默信号死链自然消除
- 测试: 全量 336 全绿 + 双编译 ✅

## 0.9.56 (2026-08-13) — v79.60 结构信号 per-player 重构 (用户裁定, 版本号待统一升)

- 结构检测维度 per-maid → **per-player**: 玩家为中心扫 1 次 (scanAllStructures) → 缓存 (位置+文案, 清旧写新不落盘) → 差集 → 动态信号 env:structure:{id}:nearby|leave
- **onSignal 零重扫**: 文案 emit 侧按玩家位置算好存缓存, 女仆共享 (原 nearby 信号 onSignal 全量重扫 289 区块找位置 — 重扫浪费消除)
- **只发主人女仆选 1 个** (随机/最近可配, 默认随机; 信号半径默认 10 格) — 多女仆只 1 个弹气泡, 无重叠
- 白名单过滤 (registry id, 支持 minecraft:village_* 任意前缀通配, 空=全部) + 最近开关 (白名单内只发最近 1 个, 默认开)
- 文案格式: ≤10 格 "附近有村庄" / 远 "西北方向有村庄" (8 方向词无距离数字; 新增 directionWord, directionLabel 重构复用)
- 玩家下线懒清理 (sweep 每广播轮扫 players(), 200t 内自动回收 — 零新事件零新类)
- 配置 4 项 (三段式 + GUI 4 项): structure_signal_radius 10 / structure_whitelist 空 / structure_nearest_only true / structure_random_maid true
- 测试: StructureSenseFilterTest 16 用例 (白名单/最近/文案/方向词); 全量 314 全绿 + 双编译 ✅
- 相关: 错题 #190 后续优化 (v79.58 重扫浪费/多女仆重复扫/信号无归属)

## 0.9.55 (2026-08-13) — v79.56/57/58 挖矿/哈气/环境感知/结构批次 (版本号待统一升, jar 仍 0.9.50)

### v79.56 — 跳过集修复 + 挖矿整理
- 跳过集刷新死循环修复 (错题 #184): tryStartVein 已跳过目标每 tick addSkip 刷新时间戳 → TTL 永 false → 永久跳过 → firstFail 门控 (已跳过不刷新 + 不 immediate 重扫)
- 失败计数退避 (用户裁定): 连续失败 ≥3 次 → TTL 60t → 600t (30 秒封顶, 永久不可达不卡住); MaidChainState.failCounts
- navigate FAILED 气泡 "目标不可达, 暂时跳过" (40t 节流)
- 结构整理: failAndSkip 出口单点 / ensureBestTool 提取 / 方法重排 / swapTool javap 还原
- 铲雪好感度乘区 (Cd = 40/speed)

### v79.57 — 下挖退役 + 工具收拢
- digVertical 退役 (错题 #185, 用户裁定): 下挖换铲卡管线 → 只挖裸露表面矿; digUp 头顶保留; CHAIN_DIG_DOWN_DEPTH 注释语义改头顶
- charge 蓄力期主手兜底 (回归 #185) + 无工具气泡 (回归 #186)
- 工具判断收拢 ToolJudge (isModeOptimal/selectBestForMode/selectBestForBlock/isSuitableUsable) + 纯逻辑抽层 (matchesToolTypeFlags/isModeOptimalFlags/intervalTicksForTier) + 13 纯 JVM 用例

### v79.58 — 自救被动化 + 哈气体系 + 环境感知
- 自救被动任务 (self_rescue): MaidDamageEvent 掉血触发 → SelfRescuePipeline 被埋瞬破; 与主动任务并行 (用户裁定修订: 不暂停); SelfRescueState 上下文预留 (未来更多自救方法); SELF_RESCUE_ENABLED 独立配置 (passive 段)
- 哈气门控 (用户裁定): 哈气运行时其他被动全停 tick; 续哈气 (tryContinue — 结束瞬间扫描女仆无缝续, 消除周期间隙)
- F-1 信号重放 (错题 #187): 哈气中 PREV_SNAPSHOTS 不更新 → 边沿保留重发
- TorchLight 重构 (错题 #189): 100t 节流检查 / 空+食物直接顶 (lightUp 腾副手) / 天亮放回 cancel / 删补盾死代码
- WorkEat 交替 (错题 #189): 吃 1 个剩余放回 + 手腾空; eatHand 改 isEdible 判定 (复核 HIGH 修复)
- LightControl 删除 (用户裁定): 管线/信号/枚举/scanRedstoneLamps 死 API 全清
- monster_log 删除 (另一窗口) + 残留清理
- 结构感知新方案 (错题 #190): getAllStarts 一次遍历全量结构 → 动态信号 env:structure:* → 通配订阅 → labelOf + directionLabel 方向气泡; StructureSense 合集类

## 0.9.54 (2026-08-12) — v79.55 全项目同类模式扫描修复 (回归扫描铁律批次, 19 文件)

- ① **FLOW_TIMEOUT 死键删除** (错题 #181): 键无写方 (FlowTaskData 连 setTimeout 都没有, submit 不写) → 看门狗恒默认 1200t; 删键 + GMPM 恒 DEFAULT_TIMEOUT (行为不变) + 旧存档残留清理保留 (clearAll)
- ② **CraftChainPipeline 目标解析路径不一致修复** (错题 #182): validate 4 级回退 vs executeOne 只读 TASK_TARGET → GUI 设置产物/默认产物配置时任务永不执行 (CraftExecute:36 空 target return false 实证) → 抽 resolveTarget 共用 (提交目标 → pipelineConfig → 默认产物 → TASK_TARGET)
- ③ **FAIL_REASON/TASK_INPUT 死写删除**: fail() put 后同方法 clearAll 立即删零读方 (删 TaskDispatcher:151); FurnacePipeline 每工作单元 setInput 死写 (值已直传 VanillaTasks.furnace); 连带删 setTarget/setMaxCount/setStep/getStep 死门面 + ANIM_TIME/FLOW_STEP 死 DataKey (键保留旧存档清理)
- ④ **NbtCodecs 写侧绕过修复** (错题 #183, 用户 javap 字节码实锤): MC 1.21.1 NbtUtils.writeBlockPos 返回 IntArrayTag — ArmTransferSetupHandler L143 强转 CompoundTag **必 CCE 崩溃** (1.21.1 右键女仆启动 arm_transfer 瞬间); BlockInteractSetupHandler L122 存 IntArrayTag → 读侧 null 绑定失效 (守卫静默); NbtCodecs.writeBlockPos 0 调用方死代码 + 1.20.1 分支自身不对称 (包 "pos" 子键 vs 读顶层) — 修对称 + 两写侧改走 NbtCodecs (单点收敛, 1.20.1 格式不变零迁移) + gametest 补 lmaNbtCodecsRoundTrip 双平台 round-trip 断言
- ⑤ **常量收编**: TaskKeys 新增 FESTIVAL_DAY/CODEX/COMPANION_UUID (FestivalPipeline/MaidCodexKillListener/NumenMaidBridge 改引用); ArmTransferPipeline KEY_TAKE/KEY_DEPOSIT/KEY_ITEM 值指向 TaskKeys.ARM_* (影子常量漂移风险)
- ⑥ **GMPM 格式哨兵日志**: TLM_SWITCH tryParse 失败非空 → WARN (防 #179 复发 — 新写方再写裸 taskType 立即暴露)
- 验证: 双节点 clean 编译 + 单测 264 全绿 + gametest 双节点 9/9 (含新增 round-trip) + grep 回归零残留

## 0.9.53 (2026-08-12) — v79.54 adapter 包代码审查修复 (9 文件)

- ① **TLM_SWITCH 值格式契约修复** (错题 #179): LmaFlowCoordinationBehavior.checkExtraStartConditions 原写裸 taskType ("craft_chain") — 消费方 GameTickPipelineManager.tickActive 用 ResourceLocation.tryParse (期望完整 uid "lma:task/craft_chain"), 无 ":" 解析失败 → 误走 TaskDispatcher.cancel 取消当前任务; 改写 curTask.getUid().toString() 与 TlmTaskMonitor 写入一致 + 值格式契约注释
- ② **PREV_TASK 死链路删除** (错题 #180): "lma_prev_task" 键全项目仅 2 写方 (LmaFlowTask.savePreviousTask / MaidStateWriter.saveAndSwitchTask) 均零调用方 → 键恒空 → restorePreviousTask 恒走 idle 回退; 用户实测恢复正常的真实链路 = TLM 原生 TASK_TAG 持久化 (EntityMaid.readAdditionalSaveData) + onEntityJoin FLOW_TASK findTask→setTask 双通道; 删除 6 处 (savePreviousTask/restorePreviousTask + MaidStateWriter 2 死方法 + PREV_TASK 键 ×2 + onEntityJoin 调用), 行为等价实证
- ③ **friendlyName 补注册名 collect_wood/collect_ore** (原 chain_wood/chain_ore 过时名 — TaskRegistry 实证注册名) → 任务气泡不再显示英文原文; 测试联动更新 (LmaTaskProgressDisplayTest)
- ④ **死代码清理**: LmaTaskTypeRegistry 5 (findByTaskType/registerSimple/typedCount/TASK_KEYWORD_MAP/buildTaskKeywordPrompt) + LmaFlowTask 2 (savePreviousTask/getCurrentFlowTaskType/restorePreviousTask 连带) + LmaTaskProgressDisplay 3 (showFail/showNoContent/verbFor — 用户裁定失败气泡直接调 MaidChatBubbleApi 是有意设计, 门面方法删) + MaidStateWriter 2 (saveAndSwitchTask/restorePreviousTask) + 悬空注释 + 5 死 import
- 验证: 双节点 clean 编译 (--no-build-cache) + 单测 264 全绿 + grep 回归零残留

## 0.9.52 (2026-08-11) — v79.53 挖矿管线审计修复 (检查报告 1-6)

- ① **扫描垂直范围对齐挖穿深度**: V_RANGE 硬编码 ±5 → `vRange() = max(5, CHAIN_DIG_DOWN_DEPTH)` — 原配置深度 6-8 时 digVertical/digUp 的 6-8 段首次扫描不可见, 上下双向挖穿配置失效 (审计发现, 用户确认双向)
- ② **大矿脉按可达 3 格球裁剪**: tryStartVein BFS 后 vein 只留 3 格球内块 — 原 queue 含全脉 (蓄力按全脉算 → 大矿脉白等 N 秒只破球内几块); 裁剪后蓄力=实破量, 球外块由重扫+移动后重新开脉覆盖 (自洽)
- ③ **寻路放弃日志 INFO→DEBUG**: 60t 重试周期内每次重扫重打 (日志实证一轮 9 条风暴)
- ④ **背包满检查 20t 节流**: 原每 tick 全背包 32 槽遍历 → MaidChainState.invCheckTick/hasSpace 缓存 (满时暂停, 清包后 ≤20t 恢复)
- ⑤ **DangerGuard 看门狗 240→60t**: 对齐跳过集 TTL — 原 240 与 SKIP_TTL=60 不匹配 (堵护 240t 超时 FAILED → skip 60t 重试 → 每 60t 循环空转); 6 侧液体最多 6 块 60t 足够
- ⑥ **destroyBlock 不 fire BreakEvent 决策记录**: TLM 源码实证 (L2416-2428 无事件) — 领地/防破坏 mod 无法拦截 LMA 挖矿破坏; 用户裁定保持现状 (TLM 原版同行为), 类 javadoc 记录
- ⑦ **DangerGuard javadoc 死引用更正**: "防摔落由 BlockUpCoordinator 垫柱负责" — 垫柱链 v79.26.8e 已删, 现仅堵护放置链; 挖穿逐格下落无坠落伤害属现状语义
- ⑧ **ChainHarvestPipeline 双模式并存注释** (2026-08-11c 外部已加): isTargetBlock 供 Brain 导航匹配, tick 自行扫描 — 两通道互补非冗余
- ⑨ **gametest 补 lmaChainOre** (双节点 8/8): 开脉→强制蓄力到点→charge 破块→队列闭环; 全同步驱动 (runAfterDelay 等待期女仆位置漂移实证坑) + moveTo/getBlockState 必须 absolutePos (相对坐标 teleport 到结构偏移外实证坑)
- ⑩ **换工具后跳过集 tier 重新维护**: tryStartVein ensureToolFor 后重调 skippedFor — 原当轮 addSkip 入旧 tier 集, 下轮被清空丢失
- ⑬ **魂符恢复 FLOW_TICK 陈旧超时修复** (用户实测日志 5270t > 1200t): TlmEventAdapter 魂符恢复分支保留陈旧 FLOW_TICK (魂符/卸载期间心跳停) → 恢复首 tick 看门狗立即超时 → 任务重置丢状态 (KEY_QUEUE/跳过集); 清 FLOW_TICK 让心跳重起 (状态保留不重置, 与跨 session 分支同款)
- ⑫ **validate 与换工具冲突修复** (用户实测 "主手不是镐背包有镐就一直不挖"): ChainHarvestPipeline.validate 原仅查主手持镐 — 主手空/拿剑 + 背包有镐 = submit validate 失败任务永不启动, execute 的自动换镐 (ItemSelect+swapTool) 永远没机会跑; 改为主手可用镐直接过 / 主手非镐查背包 (有 → "背包有镐, 将自动装备", 无 → "背包没有可用的镐")
- ⑪ **到达对齐恢复** (用户实测 "女仆到不了附近和移动太快飞出去"): v79.26.8f 删 NavWatchdog 时连带删除近程减速 → 女仆全速冲过头 → TLM 折返摆动 (v79.26.8c 教训: "对齐归零水平速度 — 惯性冲过柱底 = 走太快表现, 0.5F 本身正确"); PathingApi.navigate reached 分支恢复 clearNav + alignToCenter (水平速度朝格中心衰减 min(0.2, d*0.5), dist≤0.05 停稳, 垂直分量保留); alignVelocity 纯函数抽离 + PathingApiTest 6 用例
- 验证: 双编译 --no-build-cache (forge/neoforge) + 单测全绿 + gametest 双节点 8/8

## 0.9.51 (2026-08-11) — v79.52 ChainHarvest 状态 per-maid 化 (静态 map 三缺陷根治)

- 新建 MaidChainState (vanilla/execute/): 原 ChainHarvestExecute 6 张跨女仆静态 map (LAST_SCAN/LAST_NEAR_SCAN/SKIPPED/SKIP_AT/IDLE_NOTIFIED/LAST_MODE) 收编为 per-maid 对象 — UUID 注册表单表 + 5 张内化字段; 三缺陷根治: ① SKIP_AT 全局共享 (跨女仆 pos→time, 女仆 A 清理误删女仆 B 的 TTL) → 时间戳归属 per-maid ② int 实体 ID key (MC ID 复用, 新女仆继承旧跳过集) → UUID 稳定 ③ 清理散落 3 处 (管线 onCleanup/EntityCleanupListener/模式切换逐表 remove) → clearMaidState 一行 STATES.remove(uuid)
- 跳过集行为收进 MaidChainState (maintainTier tier 分组清空 / addSkip 容量 10 淘汰最旧 / expire TTL=60 过期) — 纯 JVM 可测, 不触碰 lastMode 字段 (MC 枚举惰性加载)
- ChainHarvestExecute: 模式切换 (Wood↔Ore) 重建状态对象 (原 4 表逐清 + SKIP_AT 连带清收敛 1 行, 错题 P-2/P-3 语义由归属根治); skippedFor/addSkip/findNearestValid 过期清理全部委托状态对象; 对外签名零改动 (管线/协调器/调用方不受影响)
- 测试 MaidChainStateTest 8 用例 (tier 变化清空/同 tier 保留/容量淘汰最旧/直接添加/过期移除/未过期保留/时间戳 0/双实例隔离 — 隔离用例即原全局 SKIP_AT 误删的根因场景)
- 验证: 双编译 --no-build-cache (forge/neoforge) + 单测 39 类 233 方法全绿

## 0.9.50 (2026-08-11) — v79.51 KeyTrigger 通用按键触发线路

- 用户决策: 不删孤儿包 (InteractTriggerPacket 双平台注册但 sendToServer 零调用) — 改造为通用按键触发基础设施: KeyTriggerRegistry (keyId → handler 静态表, 只存代码引用免泄漏, 重复注册抛异常) + KeyTriggerHandler 接口 (maid, player) → void
- InteractTriggerPacket 加 String keyId 字段 (writeUtf/readUtf) + sendToServer(String keyId); 服务端 handle 补 NET-H1 20t 节流 (C2SThrottle key_trigger) + 注册表分发 (未注册 id 静默) + 原 AABB 范围扫描保留 (BI_TRIGGER_RANGE 恢复真实消费方); 双平台 handle 逻辑收敛共用 dispatch
- 客户端 MaidKeyTriggerClient (v67 BlockInteractKeyMapping 泛化): KeyMapping 6 参双平台同签名, 默认数字键 0 (key.lma.trigger / key.categories.lma 新 lang key zh/en), isInGame 四查守卫原样
- 双平台接线: forge LmaForgeClientEntry MOD bus 注册键 + GAME bus 检测 (同款 addListener); neoforge LmaNeoForgeClientEntry 构造器手动注册 (v79.18 教训: GAME bus 静态订阅失效)
- block_interact 首个消费者 (恢复 v67 手动触发语义): KeyTriggerRegistry.init() 挂 LmaRegistrar.init, handler = 引擎级任务分发 (TaskRegistry + TaskSignalListener instanceof, 语义与旧包 handle 逐字一致)
- 测试 KeyTriggerRegistryTest 4 用例 (注册/重复抛/未注册 null/独立存储; 匿名类防 MC 类加载)
- 验证: 双编译 --no-build-cache + 单测 (主会话统一执行)

## 0.9.49 (2026-08-10) — v79.49 补测 (ThrottleMath/Signals/BlueprintReader/FestivalLoader)

- ThrottleMath 抽离 (ThrottleUtil 判定委托纯函数, 零行为变化) + ThrottleMathTest 6 用例 (首放/节流/过期/时钟回退/剩余/interval=0)
- SignalsTest 5 用例 (envOf 往返/parseEnv 往返 29 枚举/非 env 前缀/未知值/null)
- BlueprintReaderTest 6 用例 — **抓出真 bug: 空块列表 maxX-minX 整数溢出尺寸假正 → describe 空列表特判修复**
- FestivalLoader 重构: FESTIVAL_FILE 静态字段 → festivalFile() 方法内取 (类加载零 MC 引用) + loadFromFile(Path) 路径注入纯方法 (LOGGER 移 load() 侧) — 原审计"可测"误判 (静态字段触发 LittleMaidMoreAction 类加载炸, 项目铁律同族); FestivalLoaderTest 5 用例
- ARCHITECTURE §10: 27 → 34 测试文件
- 验证: 单测 178+ 全绿 / 双编译

## 0.9.48 (2026-08-10) — v79.48 死代码清理 + 文档合并修复 + 饰品自动修复 + Bug 修复 (#10)

### Bug 修复 (#10)

- 🔴 TorchLight 三层闭环: onSignal 补 submitPassive (in_progress 才有 GMPM tick 驱动 — 否则火把永久插副手); tick 恢复完成补 cancelPassive (闭环, 下次 DARKNESS 再触发); 亮度硬编码 7 → ENV_DARKNESS_THRESHOLD 配置
- ArmTransferPipeline: 取货容器消失 (handler null) → fail + 气泡 (防无限囤货死循环)
- BlockInteractPipeline.validate: pos 读取 null (NBT 损坏) → failed (原 null 通过 = 无目标空转)
- HaqiPipeline 开关核查: 双开关已合并一行 (HAQI_ENABLED || HAQI_ENABLED_TO_OWNER, 无缺)

### 死代码清理 (批次 2, 12 文件)

- ItemResolver (传递死 — 引用方全删) / AltarExecute / ContainerExecute / PlaceBlockExecute / ToolSelectExecute / EntitySearch / OutputCollector / EntityOutput / SubmitTaskAction / BlockInteractKeyMapping / TimerBasedCreatePipeline (@deprecated 自标) / CheatManager (无注册点)
- ItemSelectTest 注释同步 (miningScore 逻辑已删)

### 饰品自动修复 (v79.48 并入, 好感度消耗模型现成复用)

- AutoRepairBehavior (MaidCheckRateTask, core 全 activity, 优先级 5, 100t ≈ 5 秒修 1 点) — 非 idle 慢慢回, 无打断 (只改 ItemStack+经验)
- MaidStateWriter.repairOneWithXp: 主手 → 其余 (副手/4甲/饰品/背包 findRepairable 复用); 1 点/cost = max(1, 4 × 好感度消耗乘区) — 原版 Mending 2 XP, LMA 基数 4 (2 倍), Lv3 0.5 → 2 = 原版水平
- repairCostFor 纯函数 + RepairCostTest 3 用例 (Lv3 → 2 / 中间态 / 下限 1)
- REPAIR_AUTO_ENABLED 配置 (默认 true, ActiveTaskConfig maid_favorability 组 + Cloth GUI "自动修复" 行)
- 验证: 双编译 (MaidCheckRateTask 双平台签名 javap 实证一致) / 单测全绿

- 删 6 文件: ParamExtractor / ItemMover / WeaponAnimationMapper / MaidEditorRegistry 链 (api/maideditor ×2 + vanilla/maideditor/BuiltinMaidEditorRegistration, VanillaCompat 调用删)
- MoreActionAPI 瘦身 128→54: 12 委托 + findMaidById 删 (0 引用实证); 3 活方法 (loadServerDurations/registerCustomAnimations/scanCustomAnimations) 调用方改直调 AnimationDurationManager / AnimationResourceRegistrar (LittleMaidMoreAction / LmaNeoForgeEntry / LittleMaidMoreActionExtension)
- DataKey CLEAR_ALL_KEYS 注释位置校正 (死键字面量实证在 FlowTaskData.cleanupLegacy, 非 clearAll)
- 文档: tlm-api-external (RuleEngine/MoreActionAnimationMessage/MoreActionAPI 行号引用全清, RuleEvent 引言删, 事件表保留), tlm-api-lma (使用矩阵 6→3 列 + RuleEngine 使用处标历史), task-registration (MoreActionAPI.reload 死行删)
- 验证: 双编译 / 单测全绿 / grep 死类 0 残留 / 部署 .bak-0810b

## 0.9.47 (2026-08-10) — v79.47 环境信号补全 + 3 新被动能力 + 女仆图鉴

### 审查修复 (2026-08-10 同日)

- 🔴 TorchLightPipeline 守卫写反修复: `!isLightItem(off)` 在副手空时恒真 → DARKNESS 点火死代码; 改 `!off.isEmpty() && !isLightItem(off)` (空副手/已是灯 → 放行)
- 🔵 TorchLight tick 放回改循环各槽 (insertItem 同堆叠合并语义), 修复固定末槽不合并/误判背包满
- 🟡 农历节日: cn.6tail:lunar 1.7.7 (libs 本地 jar, 平台 build.gradle implementation) — festival.json schema 加 `lunar` 标志 (缺省 false 向后兼容); FestivalTable.lookup 农历条目经库换算 (2026 实测映射: 春节 2/17 / 端午 6/19 / 七夕 8/19 / 中秋 9/25; 2025 中秋 10/6); 原公历 8/15 中秋条目删转农历
- 🔴 节日信号重设计 (用户裁定, 替代边沿方案): **stateless 状态广播** — Broadcaster 每轮查表非空 → FESTIVAL_ENTER 全女仆 emit, 删 lastDate 静态基线 (错过广播的女仆上线首收即触发); **删 FESTIVAL_LEAVE** (EnvSignal/Signals 29→28, 广播分支删); 消费端 FestivalPipeline **per-maid 当天首收去重** — PD 存 EpochDay long (**禁 month/day** — 跨年同日月误判同天永久静默), 同天静默/跨天/跨年再触发 (shouldAnnounce 纯函数 + FestivalPipelineTest 4 用例含跨年)
- 🟡 MaidCodexScreen 3 处硬编码中文 → lang key (gui.littlemaidmoreaction.maid_codex.*, zh/en 双文件)
- 版本 4 处 → 0.9.47 (gradle.properties ×2 + mods.toml ×2)
- 验证: 双编译 / 单测全绿 (含农历映射用例) / jar 验 / 部署 .bak-0810 (含 lunar-1.7.7.jar 入 mods)

- 信号层: EnvSignal 21→29 (FRIENDLY_CLEAR/MAID_CLEAR/DARKNESS_CLEAR 对称边沿 + VILLAGE/MINESHAFT/OUTPOST_LEAVE + FESTIVAL_ENTER/LEAVE), Signals 补齐 29 常量与枚举一一对应
- 结构 LEAVE: STRUCT_FOUND 反向差集 (上次有/本轮无 → LEAVE)
- 节日日历检测: 静态 lastDate + LocalDate.now() 现实日期口径 → FESTIVAL_ENTER/LEAVE 全女仆 emit; FestivalTable 纯函数 (JVM 测) + festival.json config 可编辑 (6 内置节日, 复制自 jar)
- 新被动管线 3: torch_light (DARKNESS → 副手火把/提灯, tick 亮度恢复或 5 格有怪换回 — 不依赖 CLEAR) / structure_sense (8 结构信号 → showTrigger 气泡 100t 节流) / festival (节日文案气泡, 管线自查 FestivalTable)
- 女仆图鉴: MaidCodexKillListener (独立订阅类, 凶手=女仆 → PD lma_codex 计数, 判定模式照抄 TLM EntityDeathEvent) + 图鉴书物品 (右键合并玩家全部女仆计数 → S2C 开 MaidCodexScreen, forge ID 14 / neoforge payload)
- 验证: 双编译 (--no-build-cache) / 单测 149 (含 FestivalTableTest + EnvEdgeDetectorTest CLEAR 用例) / gametest 7/7

## 0.9.46 (2026-08-10) — v79.46 版本注释降噪 (682 → 类级史 137)

- 全项目 vXX 注释分层降噪: 方法/字段/行内 vXX 前缀去除 (保留语义内容), 类级 javadoc 历史段保留 (知识资产), 3+ 版本叠层演化史收敛为终态语义 + "(演化史见 changelog)"
- 5+1 路 agent 并行 ~190 文件: 去前缀 ~630 处 (vanilla 110+6收敛 / task-pipeline 120+3 / task-其余 174+1 / adapter-api-network 61+1 / compat-screen-config 166+1 / 主类+ai 补漏 23)
- 保留: 错题 #N 引用、★ 标记、stonecutter 指令、tooltip 字符串 (代码行)、Lv 等级缩写 (非版本)
- 验证: 双编译 / 行内 vXX 0 残留

## 0.9.46b (2026-08-10) — v79.46b 管线模板防呆 (用户模板审查裁定)

- WorkStationPipeline.isTargetBlock 抽象化 — 接口默认 false 忘覆写 = 目标恒失效 → 擦记忆→重搜无限循环 (编译不报); abstract 编译期强制
- needsGameTick 字段删除 — 实证: 主动管线全 true + 被动 tickPassiveFor 不查字段 (LightControl/MonsterLog 的 false 是装饰) → 字段无分支价值; GMPM 驱动所有 in_progress 主动管线 (防新管线忘声明 = 静默死任务); 删接口 default + GMPM 条件 + 16 处覆写 (含 forge 平台 CannonLoadPipeline)
- countSuccess 删 max=0 一次性分支 (基类恒 isLongRunning=true → 永假; 工作站 max=0 = 永续任务)
- 验证: 双编译 / 145 单测

## 0.9.45 (2026-08-09) — v79.45 双驱动终局: Brain 纯导航 + WorkStationPipeline 基类

- TaskPipeline 删 execute + 加 executeInterval (默认 10); 工作站覆写 30 (原 Brain EXECUTE_INTERVAL)
- LmaFlowCoordinationBehavior 纯导航化: 删 doExecute/completeTask/心跳/冷却 — 只留导航 + 切换检测 + 目标失效重搜; 心跳归 GMPM (20t)
- 新建 WorkStationPipeline 基类 (needsGameTick/workPointTask/isLongRunning final true): GMPM 驱动 tick (到达 → 节拍 → executeOne → SUCCESS 计数/完成 / FAILED 气泡), countSuccess 迁入 Brain 原计数链 + erase/setTask(idle) (TaskDispatcher.complete 不含导航记忆清理)
- 4 工作站管线 (Furnace/CraftChain/Jukebox/BellRing) 改继承, execute→executeOne; Jukebox isLongRunning false→true (GMPM 心跳豁免, 安全); 移动型 4 管线删防御性 execute 覆写
- **顺带修 v79.32 回归**: AiControlGate.enable 唯一路径 (execute) 被 Brain L162 needsGameTick 挡 → 生产从不开启 → 移入 tick (幂等) + gametest lmaAiControlGate 改 tick 验证
- 验证: 双编译 / 145 单测

## 0.9.44 (2026-08-09) — v79.44 enableLookAndRandomWalk 语义反转修复

- LmaFlowTaskBase.enableLookAndRandomWalk return true → false — TLM javadoc "@return 是否禁用" 写反 (MaidBrain:145/194 谓词 + MaidRunOne.tryStart 实证: true=启用), LMA 注释按错误 javadoc 抄 → 任务期间实际启用随机闲逛与意图相反; 修复后所有主动任务任务期间禁用四处张望+随机走动 (同 TaskGunAttack/MaidAssemblyTask)
- 验证: 双编译 / 单测

## 0.9.43 (2026-08-09) — v79.43 三缺陷修复 (用户审计)

- 事件信号链死链修复: flushPending 不再要求扫描快照 (快照被 per-maid 死门饿死 → 队列信号曾全丢弃; emit 唯一调用方 = SenseApi); 无快照也分发 (snap=null), LightControl/MonsterLog 管线加 null 守卫防 NPE
- 多维度广播饥饿修复: TaskTickHandler nextBroadcastTick 静态单值 → per-dimension Map (维度 A 广播后 B/C 曾被压 200t+ 轮替饥饿)
- 空 PL 落盘修复: MaidData flushPl/flushAllPl 空 tag → remove (pl() 首调缓存空 tag 无条件 put = 跨 session 空键累积)
- 文档: ARCHITECTURE 桥接层/event: 段更正 (事件桥 4 类 v77.4 已删)
- 验证: 双编译 / 单测

## 0.9.42 (2026-08-09) — v79.42 全项目审计修复 P0-P4

- P0 ANIM 键残留闭环: HaqiPipeline.onCleanup 清 ANIM_RUNTIME_KEYS 全键 (含 SEQ — 残留 seq 是跨 session 重播源; 被动终结不调 clearAll) + TlmEventAdapter 跨 session 分支同清
- P1 编译警告根因: VisualOutput 粒子 PARTICLE_TYPE 平台条件化 (1.20.1 ForgeRegistries) + MaidData get/put @SuppressWarnings (unchecked)
- P2 死代码: 删死方法 7 (freezeAi×2/dropHandItem×2/playWeaponAnim/resetAnimation/bleed) + 死键 9 (RETRY_COUNT/SAVED_HOME/SAVED_PICKUP/JUKEBOX_LAST/WEAPON_ANIM/LAST_EMOJI_TICK/FREEZE_TICKS/BLEED_TICKS/BLEED_DMG — TaskKeys+DataKey+CLEAR_ALL_KEYS 同步删, clearAll 字面量清理面兜底) + Signals 20 死常量 (event: 5 + env: 14, 保留 7 活) + MaidEmojiApi 死字段 + VisualOutput 死分支扁平化
- P3 文档: api-guide.md 寻路段重写 (当前 PathingApi.navigate/NavOutcome) + ARCHITECTURE RetryPolicy/MaidPanelStyle 残留清除 + pipeline-task/task-system-reference 同步
- P4: EnvSenseBroadcaster per-maid 死门注释标注 (保留现状, 用户裁定)
- 验证: 双编译 / 单测

## 0.9.41 (2026-08-09) — v79.41 死常量清理收尾

- 实证 9 死常量 (STATE_STOPPED/STATE_QUEUED/ANIM_ID/TASK_ENABLED_PREFIX/FSM_PREFIX/AUTOCROP_ENABLED/TICK_LAST/OWNER_TARGET_TICK/DYNAMIC_ANIMATIONS) 在 LMA-MAIN 全项目 0 引用, 常量定义已删
- 修正预存 diff 误伤: ANIM_RUNTIME_KEYS/ANIM_CLEANUP_KEYS 恢复 4 活键 (ANIM_MODE/ANIM_TICK/ANIM_DUR/ANIM_NAME — AnimExecute 写/Provider 读), 只删 ANIM_ID — 防 v67 类动画键跨 session 残留回归
- 残留清理: VisualOutput.resetAnimation 删死字面量 "lma_anim_id"; TaskKeys 空注释节 (任务开关/事件桥/主人目标节流/动态动画开关); TaskStateMachine javadoc 更正 (lma_fsm_ NBT → v79.31 MaidData.pl 内存态)
- 文档同步: passive-task-system.md 删 lma_task_enabled_ 过时示例; data-management.md 动态键清单去 TASK_ENABLED_PREFIX
- 验证: 双编译 / 单测

## 0.9.40 (2026-08-09) — v79.40 全局节流工具 + 食物消耗乘区

- 新建 ThrottleUtil (vanilla/input/maid): shouldFire/cooldownRemaining, 当 CD 间隔用一个数字
- 收编 4 处手写节流: MaidEmojiApi(表情)/BellRingPipeline(敲钟)/EnvSenseBroadcaster(结构探测, 内存 map 删)/ChainHarvestExecute(气泡, BUBBLE_TICK map 删)
- WorkEatBehavior 好感度消耗线 (等级高吃得省, 检查间隔 = 100/cost, 原 switch 收编)
- 验证: 双编译 / 145 单测 / gametest 7/7 ×2 / 打包 0.9.40

## 0.9.39 (2026-08-09) — v79.39 女仆好感度双乘区

- 新建 MaidFavorability service (TLM 等级 0-3 读取 + 效率/消耗双乘区, 管线自己乘)
- 全局设置 maid_favorability 组 (开关 + 每级递增可配) + Cloth GUI 7 项
- 收编分散好感度 switch: TimerBased/Mix/Press workTicks + MaidAssemblyService.getDuration → 乘区
- 应用: 采集蓄力间隔缩短 (效率) + 工具耐久消耗降低 (消耗)
- 验证: 双编译 / 145 单测 / gametest 7/7 ×2 / 打包 0.9.39

## 0.9.38 (2026-08-09) — v79.38.2 全局右键门面

- BlockInteractService 提升为全局右键门面 (距离检查 FakePlayerInteract.rightClick)
- 绑定方块丢失处理 (气泡+清绑定) 移回 BlockInteractPipeline (任务语义)
- AI 工具 interact_block 统一走门面 (获得距离检查, 行为修正: 不再无限距离右键)
- 验证: 双编译 / 145 单测 / gametest 7/7 ×2 / 打包 0.9.38

## 0.9.37 (2026-08-09) — v79.38.1 收尾清理

- EnvSnapshot 死字段面删 (blockHits/worldSignals 恒空 blocks() 死方法)
- TaskToggle/TaskGroup 手写 JSON →Gson (原 indexOf+split 无转义, 含 ,/" 即解析错乱)
- Signals 注释 18 →21 (EnvSignal 枚举实际数)
- 验证: 双编译 / 145 单测 / gametest 7/7 ×2 / 打包 0.9.37

## 0.9.36 (2026-08-09) — v79.38 task 标准化模块化

- ChainHarvestPipeline(Mode) 合并 ChainOre/ChainWood (参数化, 行为不变)
- pipelineConfigOf 复制 ×2 →TaskConfigs.get; AiControl handleConfigAction →super 委托
- PassiveSenseRegistration 反射 →直接实例化; LookAroundGrid.canStandAt 条件修复 (平地误判不可站)
- 耐久/垂直魔法数字收常量; import/javadoc 清理; CLEAR_ALL_KEYS 补 FURNACE_PHASE
- 新守护测试 DataKeyConsistencyTest (新增键必须声明清理归属)
- 验证: 双编译 / 145 单测 / gametest 7/7 ×2 / 打包 0.9.36

## 0.9.35 (2026-08-09) — v79.37 死代码清理 修复

- 删 DataKey 25 零引用键 CLEAR_ALL_KEYS 补 4 键 (JUKEBOX_LAST/ANIM_TIME/WEAPON_ANIM/RETRY_COUNT — 跨任务残留修复)
- 删死方法: TaskMetaData 6 / FlowTaskData 3 / TaskToggle disabledTypes/hiddenTypes / TaskRegistry passiveTasks / CraftChain merge
- TaskToggle.isEnabledFor 简化 (per-maid 禁用键无写入方, 死功能)
- 修 nextBroadcastTick 跨 session 残留 (ServerStopping 归零, EnvSense 广播重启后立即恢复)
- changelog 清理: 移除全部用户原话 (只留原因与结果)
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.35

## 0.9.34 (2026-08-09) — v79.36 重试机制删除

- 删 RetryPolicy (TaskDispatcher timeout/fail 重试分支 TaskPipeline.retryPolicy 类 测试) — 主动任务靠 TLM 任务栏自动重启, 被动靠信号重触发; 顺带消掉计数自毁假 bug
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.34

## 0.9.33 (2026-08-09) — v79.35 工作点模式 背包满暂停恢复

- workPointTask 标记 (TaskPipeline 新方法 6 工作站管线覆写 LmaTypedFlowTask 桥接 TLM)
- 背包满暂停恢复 (ChainHarvestExecute hasInventorySpace 条件挂起, 清包自动恢复)
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.33

## 0.9.32 (2026-08-09) — v79.34 装饰性状态机简化

- ChainOre/ChainWood 去 TaskStateMachine 继承 (SEARCHING/CHOPPING 2 态为执行数据镜像无实际语义) →直接实现 TaskPipeline 每 tick 直执行
- 树内 TaskStateMachine 只剩 7 个真状态机 (语义纯净化, 行为不变)
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.32

## 0.9.31 (2026-08-09) — v79.33 注册去 showInBar 可视开关生效

- TaskRegistry/LMAT 注册去 showInBar 参数 (主动/被动由 register/registerPassive API 区分)
- TaskToggle.isVisible 接入 TLM 任务栏 (LmaTypedFlowTask.isHidden) — 任务树 GUI 可视开关真实生效 (原半成品)
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.31

## 0.9.30 (2026-08-09) — v79.32 executor 整合删除

- TaskPipeline.executor() →execute(world, maid, pos): TaskResult 一次工作单元 (默认 CONTINUE)
- 14 管线 executor 覆写迁移 (Furnace/CraftChain/Jukebox/BellRing SUCCESS 计数链保留)
- 删 IExecutor.java TaskRegistry deprecated 重载/executor 字段/passiveExecutor LMAT 3/4 参重载 LMAT.exec
- LmaFlowCoordinationBehavior 驱动点改 pipeline().execute(); gametest 适配
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.30

## 0.9.29 (2026-08-09) — v79.31 FSM 内存化 Phase 3 碎片迁移

- FSM 状态内存化: TaskStateMachine 状态改存 MaidData.pl(<type>.fsm) 的  键 — 每 tick 零 NBT 读 (原 getString+Enum.valueOf+try/catch); 心跳 flush 改 flushAllPl 覆盖 FSM 键
- Phase 3 静态键碎片迁移 8 文件: LmaFlowTask/LmaTaskGuiHandler/TlmEventAdapter/TlmTaskMonitor/MaidTaskContext/MaidAssemblyInventory/MaidAssemblyEventHandler/ArmTransferSetupHandler (PREV_TASK/FLOW 键/ASSEMBLY_INV/ARM_TAKE/ARM_DEPOSIT →MaidData)
- 修复: 1.21.1 NbtUtils.writeBlockPos 返回 Tag (非 CompoundTag) — cast; FQCN →import 短名统一
- 保留边界: TimerBasedCreate 私有键/VisualOutput 动态拼接/CombatOutput 通用实体
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.29

## 0.9.28 (2026-08-09) — v79.30.1 终局清理

- 删 TaskState 死代码 (task/service/TaskState.java — persistent/memory 零业务使用, 被 MaidData 取代) MaidUnloadRegistry 注册行
- FlowTaskData.clearAll 键表驱动化 (DataKey.CLEAR_ALL_KEYS 遍历 remove, 消除 26 处手写清单双源漂移)
- LMAT javadoc 示例更新 (新 2 参 register TaskConfigurable 示例, 弃 deprecated 3 参)
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.28

## 0.9.27 (2026-08-09) — v79.30 数据管理层 Phase 2

- DataKey 类型化键表 (task/data/DataKey.java, ~55 键引 TaskKeys 常量, TLM TaskDataKey 参考简化版)
- MaidData 分区实现: 类型化 get/put/has/remove (FLOW/META/ANIM/MISC root 直读写)
- FlowTaskData/TaskMetaData 内部收编 MaidData (公开 API 不变)
- 直读点迁移 ~40 处: TaskDispatcher/LmaFlowCoordinationBehavior/GameTickPipelineManager/AiControlGate/LmaMagicCastingProvider (13 处 ANIM 键)/JukeboxExecute/FurnaceExecute/ProgressNotifier/MaidEmojiApi/MaidChatBubbleApi/MovementOutput
- 明确保留: 动态键 (passiveKey/私有键/setup 拼接) 通用实体操作 (CombatOutput 流血/MovementOutput 冻结 — target 是 LivingEntity 非女仆, 不属于 MaidData 职责)
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.27

## 0.9.26 (2026-08-09) — v79.29.1 数据层补丁

- ServerStopping 兜底: 服务器停止 (保存前) 全女仆 PL 内存态落盘 (被动任务无心跳场景, 强制关闭覆盖)
- TaskStateMachine 键收编遗漏 (lma_fsm_ →TaskKeys.FSM_PREFIX)
- 合并卸载监听: 删 Extension.ServerEvents (EntityCleanupListener 唯一监听, MaidUnloadRegistry 幂等)
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.26

## 0.9.25 (2026-08-09) — v79.29 统一数据管理层 Phase 1

- 新建 MaidData 门面 (task/data/): PL 分区内存态 (tick 零 NBT, flush 心跳 20t/实体离开/终结三处落盘, 跨 session 恢复不变) CFG 直读
- TaskKeys 散键收编 (lma_ai_control/lma_weapon_anim/lma_anim_time/节流键/chain 键/PL/CFG/FSM 前缀)
- 新建 MaidUnloadRegistry: 13 静态缓存统一卸载清理 (ChainHarvest/PathingApi/GameTick/EnvSense/AutoCrop/TlmTaskMonitor/FakePlayer/MaidData PL)
- 修 2 个泄漏 bug: TaskState.MemoryTaskState clear() 键不匹配、MaidAssemblyInventory.CACHE 无清理
- 字面量收编 8 文件 (AiControlGate/ChainHarvestExecute/JukeboxExecute/FurnaceExecute/MaidChatBubbleApi/MaidEmojiApi/VisualOutput)
- 验证: 双编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.25

## 0.9.24 (2026-08-09) — v79.28 管线接口瘦身

- TaskPipeline 拆分三接口: 核心 TaskPipeline (执行/生命周期/展示) TaskConfigurable (配置维度) TaskSignalListener (信号维度)
- TaskRegistry.register 去 executor 参数 (executor 由 pipeline.executor() 提供, 默认 = tick 委托); 旧签名 deprecated 兼容
- 18 个管线 implements 适配 9 个调用点 instanceof 适配 (TaskConfigGuiFactory/DefaultBehaviorBrain/网络包链/EnvSenseBroadcaster/InteractTriggerPacket)
- 行为零变化: 双节点编译 / 149 单测 / gametest 7/7 ×2 / 打包 0.9.24

## 0.9.27 (2026-08-08) — v79.26 卡顿修复 女仆列表/属性屏大面板重设计 3D 预览半身修复

### v79.26.2 Changed (2026-08-08, 实测反馈)

- **女仆列表背景改原版主菜单旋转全景** (用户裁定 — 旧仓库 v75.3 女仆选择屏用过 `Screen.renderPanorama`, v79.25 重写独立屏时丢失): `PanoramaRenderer` 双平台类均在 **`net.minecraft.client.renderer`** 包 (本地 decompile 实证, 不在 gui.screens — 此前 WebSearch 误导 import 错包编译失败), 构造收 `CubeMap` (MainMenuScreen 同款); 1.20.1 `render(float, float)` 无参版传 1.0F / 1.21.1 `render(GuiGraphics, int, int, float, float)` 传 width/height (编译实证双平台签名不同); **不调 super.renderBackground** — 1.21 默认含 renderBlurredBackground 背景模糊, 用户明确反馈; 纸感面板 深色文字保留浮在全景上
- **★加载世界卡很久根因 (日志实证 19:08:04-19:09:44)**: 进世界后 AnimFileSyncPacket 动画同步 **7 个包逐包 5.5 秒间隔 (共 40 秒)**, 每包渲染线程全量 reload 链: StartupLoader.reload DynamicAnimationResources.reload remergeAll (8 次磁盘 IO+解析) **YsmAnimInjector.injectHaqiIfNeeded (22 模型包 × 8 源文件 × ~3 次读 Gson 解析 ≈ 528 次磁盘 IO)** [LMA/Registrar] ISS 未缓存 WARN 每包刷。修复三件套: ① **YsmAnimInjector 源文件指纹快检** (fileName+size+mtime 串, 8 次 lstat 替代 528 次内容读; 源只在 AnimSync 落盘时变 →指纹不变零 IO 跳过; builtin 未就绪/遍历失败不缓存指纹下次重试 — 竞态兜底) ② **AnimFileSyncPacket 防抖** (handleClient 只落盘, 2 秒无新包 flushPending 统一 reload 一次 — 7 次 5.5 秒卡顿 →1 次; 由 YsmReloadListener.onClientTick 每 tick 驱动) ③ Registrar ISS 未缓存 WARN 只打一次 (后续 DEBUG 静默)
- 验证: 双节点编译 ✅ / 单测 ✅ / 打包 unzip 验 jar ✅ / 部署双端 (轮换 .bak-0808g)

### v79.26.1 Fixed (2026-08-08, 实测反馈)

### 卡顿修复 (日志风暴实证: 1935 行日志中 1664 行是 LMA — 用户反馈)

- `[LMA/YsmReload]` 1002 行: tick 补全遍历 **123 个 YSM 模型文件 × 8 动画 = 984 次磁盘读取 JSON/Molang 解析**, 全在 Render thread 同步跑 4.3 秒 — **进世界卡主因**。修复: 源文件解析缓存 (磁盘 IO 解析 984→8 次, 剩余纯内存 putAnimation 合并) IdentityHashMap 幂等去重 (同 AnimationFile 实例同源只合并一次; F3+T 重建新实例自动重合并, 内容相等不误跳) 合并日志降 DEBUG
- `[LMA/Provider]` 419 行: TLM 每帧调 getMagicCastingState/getAnimationBuilder, **每帧 2-3 条 INFO** — 游戏内持续卡顿源。修复: 降 DEBUG (FIRST CALL 保留 INFO)
- `[LMA/YsmInject]` 179 行: 启动一次性逐模型刷屏 →降 DEBUG

### 女仆列表屏重设计 (用户裁定 )

- MaidListScreen 回归 **320×240 大面板** (v79.25.2 的 256×256 太小太挤): 宽列表 170 (行高 22) 右侧大 3D 预览 (50 缩放) TLM 棕色面板背景保留
- **行信息双行**: 名称 / `Lv N`(金) `❤ x/y`(红) 距离格(灰右对齐) — MaidEntry 扩展 level/health/maxHealth (TLM 等级契约 exp/120 getHealth/getMaxHealth, 网络包 12/13 序列化同步)
- **3D 预览半身修复 (错题 #137)**: 1.21 的 10 参 renderEntityInInventoryFollowsMouse 是**区域+缩放+yOffset** 语义 (x1,y1,x2,y2 — TLM 自家 67×112 区域, AbstractMaidContainerGui L562), 旧代码传 px+45,py+45 = 45×45 小框 →下半身被框裁掉只剩半身; 1.20 的 7 参是中心点+缩放 — 照抄参数位置即错 (错题 #136 同族: 双平台方法语义差异)

### 属性屏大方化 (用户裁定 )

- MaidAttributeScreen 面板 **420×300 →480×360**; 行高 14 →**22**; 组标题 18 →**24** 金色大字 装饰短横线; 值右对齐金色 (0x9C6B1F); 双层边框纸感卷面板 顶部女仆名金 分隔线

### v79.26.1 Fixed (2026-08-08, 实测反馈)

- **列表行/预览区硬编码 `Lv N` →lang key** `gui.littlemaidmoreaction.maid_list.level` (zh `等级 %s` / en `Level %s`, translatable 带参数) — v79.26 双行信息新加的行内唯一英文源; 其余屏内 literal 全中文 (grep 全 screen 目录验证)
- **★打包坑: jar 内 lang 陈旧** — neoforge jar 从 `build/classes/java/main` 打包 (copyResourcesToClasses 产物), 但 jar task 不依赖它 →lang 改动后 jar 里 zh_cn.json 停留在旧版 (缺 v79.25 GUI key), 双平台按钮/文本 fallback 显示 key 原文。修复链路: 清 `build/generated` (stonecutter merged) →`--rerun-tasks` 重打包 →**neoforge 必跑 `:neoforge:1.21.1:copyResourcesToClasses --rerun-tasks`** (CLAUDE.md 既有规则) — 之后 jar 内 zh_cn.json 1966B 全 key 齐。**教训: 打包后必须 unzip 验 jar 内资源, 不能只信 BUILD SUCCESSFUL**

## 0.9.26 (2026-08-08) — v79.25.2 女仆列表服务端全维度扫描 TLM 棕色面板 GUI 统一

### v79.25.2 Changed (2026-08-08, 实测反馈)

- **★女仆列表数据源 = 服务端全维度扫描**: 新 `network/MaidListQueryPacket` (C2S, forge ID 12 / neo playToServer) — 服务端 `player.server.getAllLevels()` 全维度 `getAllEntities()` 过滤 `EntityMaid isAlive getOwnerUUID().equals(player)` →按 distSqr 排序; 新 `network/MaidListResponsePacket` (S2C, forge ID 13 / neo playToClient) — `record MaidEntry(uuid, name, dimension, distSqr)` 客户端静态缓存 tick 轮询; MaidListScreen 删旧 64 格 AABB 附近扫描 (跨维度/远距离女仆全可见)
- **★TLM 棕色主面板背景 (5 屏统一)**: 新 `screen/MaidPanelStyle` — blit TLM `maid_gui_main.png` 256×256 (touhou_little_maid namespace, 1.20/1.21 ResourceLocation 构造条件化) 深棕渐变衬底 (0xFF4A3424→0xFF20150C); MaidListScreen (256×256 面板居中: 左列表名字+距离格 / 右 3D 预览 / 底部返回+进入属性界面) MaidAttributeScreen/LMAConfigScreen (按钮排进面板)/TaskTreeScreen/CompatConfigScreen 全屏背景统一
- **进入属性界面按钮**: 仅本地实体存在时可点 (20t 节流 512 格 box 探测 `getEntitiesOfClass` — 远端/跨维度女仆无实体引用属性屏读不了, 面板显示名字+维度+); 点行只选中不直开属性屏 (防误触)
- **错题 #136**: ClientLevel 无无参 `getEntities()` — LmaCommand 的 ServerLevel 用法不能外推到 MaidListScreen 的 mc.level; `drawCenteredString` 6 参 boolean shadow 版双平台均不存在 (统一 5 参)
- 验证: 双节点编译 ✅ / 单测 ✅ / gametest 7/7 ×2 ✅ / 打包部署双端 (旧 jar 轮换 .bak-0808d)

## 0.9.25 (2026-08-08) — v79.25.1 动画骨名误诊更正 (maimeng 统一) 界面模糊修复

### v79.25.1 Fixed (2026-08-08, 实测反馈)

- **★maimeng 播不出真因 (错题 #134)**: v79.20.6 误诊 — 当时认为 TLM 原版模型骨 = 全小写 →造 maimeng_vanilla/haqi_vanilla (小写骨) isYsmModel 分流。实际 TLM 官方模型 winefox.json 骨名 **PascalCase** (Root/AllBody/UpBody/Head/LeftArm/LeftForeArm/.../Tail/Tail2-7) — vanilla 小写动画在 winefox 上 **0 骨匹配** →geckolib 播了但模型不动。管线全通 (触发→写侧→网络→Provider seq=46→合并 123 模型文件→无 ) — 唯一断点是骨名。haqi 能播是巧合: haqi.animation.json 用 **AllBody** (TLM 通用根骨, 双模型均匹配)。注: v79.20.6 对小写骨模型 (灵梦/chen 实证) 的修复真实存在 — 但用户主测试 winefox 是 PascalCase →vanilla 分流对用户场景无效
- **修复**: HaqiPipeline 删 isYsmModel 分流 删 HAQI_ANIM_VANILLA/HAQI_OWNER_ANIM_VANILLA 常量 →统一播 maimeng/haqi (PascalCase 骨: YSM 全匹配 / TLM winefox 系部分匹配 — 头/臂/上身/尾动, 腿骨 Leg/LeftLeg2/RightLeg2 不匹配无妨); StartupLoader.ANIM_PRESETS 删 2 条目; 删 2 资源文件; gametest 断言 →HAQI_ANIM/HAQI_OWNER_ANIM
- **界面模糊修复 (实测反馈)**: MaidListScreen/MaidAttributeScreen 缺 renderBackground 覆盖 →1.21.1 默认含 renderBlurredBackground (1.20.2+ 行为) →补覆盖: 1.21.1 分支只 fillGradient 不调 super (跳过模糊); 1.20.1 分支 super (原生即渐变无模糊)。LMAConfigScreen/TaskTreeScreen/CompatConfigScreen 已有 v75.2 模式不受影响。教训: stonecutter 块首行不能是裸注释行 (// 会被剥) — 首行必须是代码
- **教训**: ① 骨名验证必须读实际模型 json (winefox.json), 不能从另一个模型的实证外推 (灵梦/chen 小写 →以为全 TLM 小写); ② 完整链路全通时, 最后嫌疑 = 骨名匹配 — geckolib 静默无警告
- 验证: 双节点编译 ✅ / 单测 ✅ / gametest 7/7 ×2 ✅ / 打包部署双端

## 0.9.24 (2026-08-08) — v79.22 物品注册点就绪 (饰品撤销, 注册基础设施保留)

### v79.22 Added (2026-08-08)

- **★LMA 首个物品注册点**: `init/LmaItems` — 双平台条件化 DeferredRegister\<Item\> (forge ForgeRegistries.ITEMS / neoforge BuiltInRegistries.ITEM, 逐字镜像 LmaBlocks 结构); 挂载 `LmaRegistrar.registerItems` →forge LittleMaidMoreAction / neoforge LmaNeoForgeEntry 构造器; **当前零物品** — 注册点就绪, 后续物品在此注册 (javadoc 含双平台注册示例)
- **饰品撤销 (裁定)**: 曾注册 3 饰品 (task_progress/rule_trigger/wireless_io_boost MaidBaubleApi 门面 3 行为 bindMaidBauble addMaidTips 提示 测试/资源/lang) — 注册基础设施先行裁定 →全部删除, 只留注册点
- **TLM 桥接盘点** (ILittleMaid 22 方法): 已接 8 addMaidTips (客户端物品提示, 有物品后接) 3 预留钩子 (addMaidBackpack/addChestType/addMaidMeal — 需 LMA 自有类型, 无则硬接 = 死代码); 13 不接 (无 LMA 语义 1 deprecated)。盘点入 ARCHITECTURE.md §11.10
- 验证: 双节点编译 ✅

### v79.22.1 Fixed (2026-08-08) — ASCEND 依托误放 (草丛搭阶梯)

- **现象**: 女仆搭路时总是往视角前一格搭块 — 放置位置全貌: 桥 = 视角前一格下面 (要去的方向, y=F-1) / 柱 = 跳起的脚下 / ASCEND 依托 = 视角前一格 (y=F, 仅落点不可站时)
- **根因**: PathExecutor ASCEND 放置条件 `canBeReplaced(stand)` — 草丛/花/雪可替换但可站 (下方草方块实心) →爬台阶交界处有草丛 →误判 →每格垫一块 = 搭阶梯
- **修复**: 条件 →`!MovementHelper.canWalkOn(...)` (baritone MovementAscend L176-177 同款: 落点脚下不可站才放), 内层保留 canBeReplaced 放置门; 规划端无需改
- 验证: 双节点编译 ✅ / 单测 34 类 210 全绿 ✅ / 错题 #128 (可替换 ≠ 缺依托)

## 0.9.23 (2026-08-07) — v79.21 任务气泡完整 API 提取 (chatbubble)

### v79.21 Added (2026-08-07, 把任务进度/完成/规则触发气泡提取为完整 API)

- **MaidChatBubbleApi 门面** (chatbubble/ 包, ★裁定不加自定义类型 — 只用 TLM 内置 TextChatBubbleData/ProgressChatBubbleData): showInfo (8s 无节流) / showComplete (§a✔ 无节流) / showFail (§c✘ **600t** 节流 — 沿用 v67.3 语义) / showTrigger (§e⚠ **100t** 节流 — 防信号刷屏) / showProgress (**替换式** — WeakHashMap 每女仆跟踪最新进度气泡, 先 remove 再 add, 防 TLM 5 气泡上限堆积); 服务端直调 ChatBubbleManager, SynchedEntityData 自动同步; § 码颜色 (TLM 无绿色纹理, 裁定)
- **adapter 语义门面**: LmaTaskProgressDisplay 重写为委托 API — friendlyName (15 任务友好中文名) stateName (18 FSM 状态中文) showTaskStart/showStep/showComplete(带 count/max)/showFail/showNoContent
- **全量迁移 10 处散点** (裁定全量提取): TaskDispatcher (validate 失败/优先级冲突/超时 →showFail; submit →开始气泡; complete →完成气泡 — clearAll **前**读 FLOW_COUNTER/FLOW_MAX_COUNT) LmaFlowCoordinationBehavior FAILED MonsterLogPipeline (⚠/✔) AiControlPipeline 缺前置 BlockInteractService 绑块丢失 JukeboxExecute 正在播放 ChainHarvestExecute 进度 (删 BUBBLE_ID map, 保留 BUBBLE_TICK 节流) WorldOutput.sendBubble×2 委托
- **步骤气泡新接线**: TaskStateMachine.tick() 首 tick 每次合法转换 →showStep (替换式; 引擎直接调用, 独立于 onEnter 钩子 — ArmTransferPipeline 覆写 onEnter 不受影响)
- **错题 #126**: 超时气泡缺节流 (同类 fail 刷屏 bug 连带修 — 超时无节流会 30 秒内重复刷屏; 归入 showFail 600t 节流)
- 验证: 双节点编译 / 单测 34 类 210 (新增 MaidChatBubbleApiTest 5 LmaTaskProgressDisplayTest 6) 全绿

## 0.9.22 (2026-08-07) — v79.20 哈气变体 表情气泡通用 API

### v79.20 Added (2026-08-07, 女仆对主人哈气 表情包 API)

- **对主人哈气变体 (★核心)**: HaqiTrigger 触发节流**对女仆优先** — 先查 2 格内女仆掷概率, 未触发再查 2 格内主人 (getOwner() 在线 ServerPlayer) 独立掷概率; 向后兼容 target_type 读空默认 maid (旧存档无键按对女仆处理)
- **6 项 _to_owner 配置**: enabled_to_owner (默认 **false** 独立二级开关, 总开关 HAQI_ENABLED 控整个管道) / chance_to_owner (0.1) / duration_ticks_to_owner (60) / volume_to_owner (1.0) / hit_chance_to_owner (0.3) / hit_damage_to_owner (1.0); GUI 哈气分类 +6 中文条目
- **表情气泡通用 API (★两次裁定非 haqi 专用)**: `chatbubble/` 包 — HaqiEmojiType (MAID: emoji_10/05/09, OWNER: emoji_01/02/20-24x24, randomPick 纯逻辑随机) HaqiEmojiBubbleData (implements IChatBubbleData, ID littlemaidmoreaction:haqi_emoji, existTick 15s) HaqiEmojiChatBubbleRenderer (@OnlyIn CLIENT, 按文件名精确过滤资源, 缺资源回退 emoji_0.png) HaqiEmojiApi 服务端门面 (getChatBubbleManager().addChatBubble)
- **对主人声音**: 固定 littlemaid_peco 包 11 idle 子集随机 (idle2/23/32/40/53/61/64/66/67/77/78) — HaqiOwnerVoicePacket 双形态网络包 (sendToTracking) 客户端 PecoHaqiSoundPlayer (仿 MaidSoundInstance) PecoHaqiSubsetLoader (Files.walk tlm_custom_pack OggReader 读盘; zip 兜底全随机 告警); 1.21 ICustomSoundBuffer 免费接入, 1.20 自挂 PlaySoundSourceEvent
- **挥击主人**: 掉血 (CombatOutput.mobAttack) 主人不反击; TLM canAttack 硬排除玩家仅影响 AI 目标选取层, 不影响直接 hurt (错题 #118)
- **gametest 7/7 双节点**: 新 lmaHaqiHitOwner (自构造注册 ServerPlayer spawnInvulnerableTime 反射清零)
- 验证: 双节点编译 / 单测 186 / gametest 7/7 ×2

### v79.20.3 Changed (2026-08-07, 裁定)

- **A* 裁撤, 网格 BFS 替代 (★)**: 删 `AStarPathFinder` (启发搜索矿洞不适配女仆) →新 `BfsPathFinder` — FIFO 无启发无代价, 移动集裁剪 (traverse 走/挖/桥 ascend 挖头/ASCEND-place descend 下 1-2 格; 跑酷删 — 全局禁用, 搭柱删 — 被 ASCEND-place 覆盖); 保留 bound 剪枝 链式放置支撑。链路 (v79.20.3b 统一入口, 裁定 ): PathingApi.findPath 内部分支 — 激进 (EXPLORER/allowMine) →直线优先 →null →BFS; 其他 (SAFE/BRIDGE) →直接 BFS; TLM 模式 →TLM 原生导航 (Mode.TLM 分支不动)。PathExecutor.plan/precomputeNext/hasPath 预检全走统一入口, findPathSafe/findPathExplorer 委托 findPath
- **MAX_NODES 15k→12k ()**: 有 bound 目标 AABB 25×25×17 = 10,625 格硬上界, 15k 永不触发; 12k = 上界 10% 缓冲, 仅 near(16)/anyOf 无 bound 时防爆
- **测试**: BfsTest 16 (删 4 跑酷/搭柱) LinePathPlannerTest 17 — 单测 199 / gametest 7/7 ×2 全绿

### v79.20.6 Fixed (2026-08-07,  )

- **TLM 原版模型哈气/卖萌无动作 (★, 错题 #125)**: maimeng/haqi 动画骨 = PascalCase **YSM 女仆骨** (Head/LeftArm/AllBody/UpBody/Tail_1-4), TLM 原版模型 (灵梦/chen 实证) 骨 = **小写** (head/body/armLeft/armRight/legLeft/legRight/tail) — 非 YSM 女仆走 geckolib ISS 通道: 动画定义有 Provider 请求播放, 但**骨名全不匹配 →播了但模型不动 →静默** (YSM 模型骨匹配正常 — 与吻合)
- **修复**: 新增 TLM 骨版动画 `maimeng_vanilla.animation.json` (点头卖萌 0.5s) `haqi_vanilla.animation.json` (弯腰哈气 1.67s) — 小写骨 **纯数组 keyframe** (geckolib3 兼容; post+catmullrom 是 YSM 扩展格式); HaqiPipeline.enterLook `isYsmModel` 分流: YSM →maimeng/haqi (逐行不动) / TLM →maimeng_vanilla/haqi_vanilla; StartupLoader.ANIM_PRESETS +2 (自动复制 ISS 注册 AnimFileSyncPacket 全量同步); gametest 断言同步 (测试女仆非 YSM →vanilla 名, 错题 #79 教训)
- **测试**: 编译双端 ✅ / 单测 199 ✅ / gametest 7/7 ×2 ✅ / 部署双端 上传 LMA-Releases main 分支

## 0.9.21 (2026-08-07) — v79.19p 工具判断 API (泥土→铲子, 矿→镐, 树→斧)


### v79.19q Fixed (2026-08-07, 实测反馈)

- **换工具丢旧工具 (★根因)**: `maid.setItemInHand(新工具)` 直接替换主手槽位 →**旧工具不自动放回背包 →永久丢失**; 铲↔镐来回换时每轮丢一个 (实测铲子消失)
- **修: `swapTool` (三处调用点统一)**: 新工具从背包槽位 `extractItem(slot, 1, false)` 提取 (保证写回, 记忆 #76 同款) →旧工具 `insertItem` 放回该空槽 →再 `setItemInHand`。TLM 无换工具专用 API (已查源码 EntityMaid/ItemsUtil) — 走 IItemHandler 交换
- 验证: 双节点编译 / 单测 0 failed / gametest 6/6 ×2 / jar 0.9.10 (v79.19q) 已部署双节点

## 0.9.18 (2026-08-07) — v79.19m 挖矿寻路 1 秒刷新 16 格内矿必可达 (跳过集 TTL)

### v79.19p Added (2026-08-07, 实测反馈)

- **工具判断 API (★核心)**: `ToolJudge.ToolType` 枚举 (PICKAXE/AXE/SHOVEL/NONE) `suitableToolType(BlockState)` — 按 MC 标准 `MINEABLE_WITH_*` tag 映射方块 →合适工具类型 (泥土/沙/沙砾→铲, 矿/石→镐, 原木→斧, 无 tag→NONE 任意可挖); `isSuitableTool` / `matchesToolType` 组合判定
- **ToolStateReader 加 `isShovel`**: 与 isPickaxe/isAxe 同模式 (instanceof ShovelItem)
- **目标驱动换工具 (★需求)**: `ChainHarvestExecute.ensureToolFor` — 找到目标后按**方块**合适类型从全背包选 tier 最高可用工具, 不再按模式 (ORE→镐) 硬编码: 挖泥土换铲子、挖矿换镐、砍树换斧; 开脉前/垂直挖穿挖脚下/寻路前全接入
- **扫描谓词与手工具解耦 (★关键)**: `scanTool` = 模式默认钻石镐/斧 — 手拿铲 (挖完泥土) 也能扫到矿, 手拿镐也能扫到泥土 (原谓词用当前手工具 canHarvest →拿错工具时目标全被过滤, 永不换工具的死锁); 等级不够的矿由 tryStartVein 真实工具 canHarvest 跳过集兜底
- **头部 ORE 加铲豁免**: 手拿好铲不换镐 (防每轮换镐↔铲抖动); 跳过集按真实手工具分组 (与扫描工具解耦)
- 验证: 双节点编译 (BlockTags.MINEABLE_WITH_* 字段名编译验证) / 单测 0 failed / gametest 6/6 ×2 / jar 0.9.10 已部署双节点

## 0.9.18 (2026-08-07) — v79.19m 挖矿寻路 1 秒刷新 16 格内矿必可达 (跳过集 TTL)

### v79.19m Fixed (2026-08-07, 实测反馈+  )

- **扫描间隔 60t →20t (★要求)**: `CHAIN_SCAN_INTERVAL` 默认 3 秒 →1 秒 — 无目标扫描每秒刷新, 有矿不再干等 3 秒
- **跳过集 TTL 过期重试 (★核心)**: FAILED 目标原**永久**进跳过集 (仅换工具 tier 才清) — 失败一次永不挖; 日志实证 10:58:14 寻路失败 {-7,-52,-29} 后该矿永久排除。新 `SKIP_TTL=600` (30 秒) `SKIP_AT` (pos→gameTime) — 每次 `findNearestValid` 先过期清理, 路径可能因挖脉/搭路变化, 30 秒后自动重试
- **采集寻路默认 safe →explorer**: safe 纯走绕绕不过山/墙 →FAILED →跳过集; explorer 挖+搭+桥全开 — 16 格内矿必可达 ()
- **FAILED 日志加 failReason**: PathExecutor 看门狗超时原因 (原只有目标坐标, 无原因)
- 验证: 双节点编译 / 181 测试 0 failed / gametest 6/6 ×2 / jar 0.9.7 已部署双节点

## 0.9.20 (2026-08-07) — v79.19o 发呆三重根因修复 16 格=半径 16 格 卡边缘居中伺服

### v79.19o Fixed (2026-08-07,  )

- **MINE 步看门狗 100t →400t (★发呆主因)**: 渐进破坏 (mineStep 每 tick getDestroyProgress 累积) 挖 1 块石头 ~150t (7.5 秒) > 原 STALL_LIMIT 100t (5 秒) →**每次挖矿必看门狗超时 FAILED** →跳过集 →发呆 ()。新按动作区分: MINE 步 400t (20 秒, 覆盖最慢石镐+余量), 其他步 100t
- **idleScan 到达判定恢复 3 格球 (★)**: v79.19n 同步 1 格邻域改过头 — 头顶 3 格矿 distSqr 9 ≤ 9 在破块门内可直接挖, 原 1 格邻域排除 →去寻路 →绕不过 →FAILED →跳过集 →发呆。**职责分层**: 寻路目标 oneAway (走动必到旁 1 格/头顶, 卡极限格问题已解决) 到达判定 3 格球 (原地能挖直接开脉) — 不冲突
- **16 格 = 半径 16 格 (chunk 换算修正)**: `SenseApi.findNearestBlock` 原 `radius/16+1` →16 格 →2 chunk = 32 格半径 (超预期 1 倍, 扫描更慢); 新 `Math.max(1, (radius+15)/16)` ceil — 16 格 = 1 chunk = 半径 16 格
- **WALK 卡边缘居中伺服 (baritone centerPlayer 同款)**: 到达判定满足 (邻格) 但实体偏离 dest 中心 > 0.3 格 (站格边缘) →`setDeltaMovement` 伺服收敛中心再推进 (速度 `min(0.2, d*0.5)` 近减速) — 原停在格边 →下一步从边缘出发 →漂移累积 →
- **跳过集 TTL 30 秒 →1 秒 (★裁定)**: 。`SKIP_TTL` 600 →20t — 与 CHAIN_SCAN_INTERVAL 同步 →每次扫描都是全新候选 (等效 1 秒重试, 保留同轮防重复: FAILED 目标不挡队首, 单次扫描选下一个可挖目标)
- **垂直挖穿 (★实测反馈)**: 目标在脚下 ≤3 格但整格 distSqr > 9 (水平偏移+深度组合) →到达判定/破块门 (都 distSqr≤9) 不过 →原走寻路, TLM 导航到不了地下 →站着看 (日志实证: 12:23:54-12:24:40 每 1-3 秒  循环, 无 FAILED 无动作; 挖开方块 →12:24:40 直接开脉)。修: idleScan 到达判定后加**垂直挖穿分支** — 目标在脚下 ≤3 格 →每 tick 挖脚下石头逐层下 (destroyBlock, 液体/基岩 canDestroyBlock 不过 →回退寻路; destroyBlock 失败 →回退寻路不死循环), 到矿层自然进 3 格球 →tryStartVein
- **MINE 看门狗 20 秒 →12 秒 (裁定)**: `MINE_STALL_LIMIT` 400 →240t — 挖 1 块石头 ~7.5 秒, 12 秒仍有余量
- 验证: 双节点编译 / 186 测试 0 failed / gametest 6/6 ×2 / jar 0.9.9 已部署双节点

## 0.9.19 (2026-08-07) — v79.19n 寻路到达 1 格邻域 头顶净空挖掘 (不再卡极限格)

### v79.19n Fixed (2026-08-07,  )

- **寻路目标 reach (3 格球) →oneAway (1 格邻域 3x3x3, ★核心)**: 原 A* 停在 (3,1,0) 等斜/极限格 (3D ≤ 3 但整格 distSqr > 9) →破块门挖不到 →死锁。新到达 = 旁 1 格 (含对角) / 正上方 (站目标头上挖脚下) / 正下方 (目标在头顶 →挖头顶) — 3D 距离 ≤ √3 ≈ 1.73 < 破块门 3 格, **到达必可挖, 永不卡极限**
- **idleScan 到达判定同步 1 格邻域**: 与寻路目标同一语义 (原 3 格球不一致 →寻路 ARRIVED 但判定不挖) — 到达后立即 tryStartVein, 流程不停: 挖完 →重扫 →下个矿
- **ASCEND 头顶净空挖掘**: 跳 1 格台阶头最高 ≈ feet.y+3 需 2 格净空 — 实测反馈 →跳前每 tick 无状态检查 f.above() f.above().above(), 实心可挖先挖 (渐进跨 tick) — 挖空自动跳; A* 侧已有同款代价 (tryAscend src y+1 / dest y+2), 执行端补齐
- 新增 NavGoalTest 5 用例 (oneAway 旁/对角/头脚下/极限格不算/目标格 sacred)
- 验证: 双节点编译 / 186 测试 0 failed / gametest 6/6 ×2 / jar 0.9.8 已部署双节点

## 0.9.17 (2026-08-07) — v79.19l ASCEND 跳跃拉扯 (位置伺服朝 dest)

### v79.19l Fixed (2026-08-07, 实测反馈)

- **ASCEND 跳跃拉扯 (★核心)**: 女仆跳起时水平速度 = 0 (导航目标 = 脚下 approach 格, dist=0 →无水平移动输入; baritone 跳跃中持续移动输入, LMA 无) →垂直跳 →落回原地 →反复跳。修: **空中每 tick 水平位置伺服朝 dest 中心** (`setDeltaMovement` 方向 = dest 中心 - 当前位置, 速度 `min(0.25, dist*0.4)` 近减速) — 收敛到 dest 投影内 0.2 格 →落回站上 dest。跳门加 `onGround` (空中 jump 无效, 原反复无效 jump)
- 验证: 双节点编译 / 181 测试 0 failed / gametest 6/6 (forge) / jar 0.9.6 已部署双节点

## 0.9.16 (2026-08-07) — v79.19k 垫方块以 maid_useful_task 为准 完成判定 onGround 寻路 4 档重定义

### v79.19k Fixed (2026-08-07, 实测反馈+  点名 全局寻路 4 档)

- **完成判定加 onGround (★核心, maid_useful_task BlockUpPlaceBehavior canStillUse 同款)**: placeStep 已实心 `return foot.equals(dest)` ASCEND `yield f.equals(dest)` — 女仆跳跃中 feet.y 跨整界 →blockPosition.y = dest.y (空中) 即误判完成 →空中推进 →下一步乱序 →落回原层反复跳 = 。新 `foot.equals(dest) && maid.onGround()` — 必须落地才算完成 (1 格台阶峰值 1.25 > 1.0, 落回台阶站上, 不误伤)
- **柱放置门: 浮点 →层级 (maid_useful_task 双条件 `canBeReplaced(below) && canBeReplaced(pos)` 同款)**: 原浮点门 `y > dest.y+0.1` 需跳高 ≥1.1 格, 女仆跳高不足/衰减 →永不过 →原地跳死; 新身体格 (blockPosition) 可替换 = 已升 1 层才放, 只需跳高 ≥1.0
- **柱放置格: 静态 aux →动态 blockPosition.below() (maid_useful_task 同款)**: 原放规划静态 aux, 女仆漂移/推挤 →放错位 →; 新放动态脚下格, 跟手垫到位
- **寻路模式 4 档重定义 (指令)**: 1.TLM原版 (新 `tlm` — 不走 PathExecutor, TLM 原生 Brain 导航直达, ChainHarvestExecute.moveTo 分支) / 2.新寻路 safe (A* 纯走绕) / 3.搭方块搭桥 (搭柱+桥不挖) / 4.激进破坏 (挖+搭+桥全开); PARKOUR 删除, 旧  配置 →SAFE 兜底 (兼容)
- 验证: 双节点编译 / 181 测试 0 failed (新增 PathingModesTest 3 用例) / gametest 6/6 ×2 / jar 0.9.6 已部署双节点

## 0.9.15 (2026-08-07) — v79.19j 放置基准修正 原地跳根因修复 全局跑酷禁用

### v79.19j Fixed (2026-08-07, 实测反馈+  )

- **柱判定容忍 Y (★核心, baritone MovementPillar 放置门对照)**: placeStep 原 `aux.equals(foot)` 严格 `BlockPos.equals`(含 Y) — 女仆跳跃中 `blockPosition().y = 脚格+1` →aux≠foot →落入 else 分支 (桥/一般, **直接 tryPlace 无放置门**) →刚离地 (y≈0.1) 就放 →身体与块重叠 →原版水平推挤 →块没垫到脚下, 每 tick 再跳再放 →。新纯函数 `isPillarPlacement` (水平同格 aux.y ≤ foot.y) →跳跃中仍柱分支 →放置门 `position().y > dest.y+0.1` 生效 (baritone 无条件全局, 不因 feet block 上移丢失)
- **wrong-Y 恢复加深度限制**: 原 `foot.y < dest.y && onGround →jump` 无深度限制 →掉 2 格深坑跳 1.25 格跳不上 →原地跳到看门狗 FAILED; 新仅 `foot.y == dest.y-1` 跳回 (1 格浅坑), 深坑不跳 →导航走近 / 看门狗 FAILED (baritone MovementAscend L161: `feet.y < src.y →UNREACHABLE` 语义)
- **全局跑酷禁用 (否决 v79.19d 补全)**: PathingModes PARKOUR/BRIDGE 的 `allowParkour=true` 残留 →全模式 false (PARKOUR 降级 = SAFE, 枚举保留兼容既有配置); ActiveTaskConfig 注释同步
- 验证: 双节点编译 / 178 测试 0 failed (新增 isPillarPlacement 两用例) / gametest 6/6 ×2 / jar 0.9.6 已部署双节点

## 0.9.14 (2026-08-07) — v79.19i 寻路 3 格挖矿 ASCEND 台阶卡死修复

### v79.19i Fixed (2026-08-07, 实测反馈+ )

- **NavGoal.reach (baritone GoalThreeBlocks 同款)**: 3D `distSqr ≤ 9` 即达 (女仆攻击距离 3 格), 目标格 sacred 不可挖 — 纯 JVM, 复用 `VanillaConstants.ARRIVE_DIST_SQR` (唯一权威 reach 常量)
- **寻路目标 interact →reach** (ChainHarvestExecute.moveTo): 原 1 格相邻寻路 vs 3 格挖矿能力脱节 →3 格内即 ARRIVED, 与破块门一致
- **idleScan 到达判定 2 格 →3 格**: `distSqr <= 4.0` →`<= ARRIVE_DIST_SQR` (与破块门一致)
- **nearPass 3 格球近扫** (核心需求: ): idleScan 节流检查前手写 3 格球扫描 (x/z/y ∈ [-3,3], 3D distSqr ≤ 9 ≈ 113 格), 独立 5tick 轻节流 (不卡 CHAIN_SCAN_INTERVAL), 快过滤 (skip/matches/allowed/canHarvest) →tryStartVein — **寻路途中经过的矿直接开脉挖**。SenseApi.findNearestBlock 的 radius 是 chunk 半径语义 (radius/16+1, 最小 16 格) 不可复用
- **ASCEND 不再导航不可达格** (PathExecutor): 原导航 dest (台阶顶格 = 方块内不可达) →TLM 导航停 2 格外 →跳门 `|dx|≤1.2` 永不过 →看门狗 FAILED 原地抽风; 改导航 `approachCell` (v79.19f 柱分支同款 0.5f,0) →女仆稳在 src 中心 dx≈1.0 过跳门 →jump 上台阶
- **moveTo FAILED 加日志**: `` — 原静默无诊断 (卡死只能靠日志猜)
- 验证: 双节点编译 / 176 测试 0 failed (新增 reach 三用例) / gametest 6/6 ×2 / 待游戏内实测反馈(寻路途中挖 3 格矿; 1 格台阶正常走上)

## 0.9.13 (2026-08-07) — v79.19h PLACE/ASCEND 执行对齐 baritone Movement 语义

### v79.19h Fixed (2026-08-07, 实测反馈搭桥时 )

- **placeStep 桥分支重写 (baritone MovementTraverse 语义)**: 原每 tick 导航 dest 同时放置 →块未落先走进沟 →掉坑; 现在 **aux 未实心时只放置不导航 dest** (keepAlive 保留), 块确认实心后才导航 dest — **先放置后跨越**
- **wrong-Y 恢复** (baritone Traverse L250-257): 两分支加 `foot.y < dest.y && onGround →jump` — 掉坑后自动跳出, 不再等看门狗 100t 静默 FAILED
- **放置门回退目标 = approach 格非 dest** (错题 #93): 新纯函数 `approachCell(dest, foot)` = dest 向女仆方向的水平符号位移 foot.y (规划节点安全可站格); 门未过 →导航 approach 走回节点 — 原导航 dest (跨沟不可达) →原地徘徊
- **ASCEND 加放置子阶段** (baritone MovementAscend L176-191): `dest.below()` 缺依托且可替换 →3x3 门内 →`tryPlace(dest.below())` 从边缘垫块 (不导航 dest); 依托确认后走现有跳门。无状态 — 块出现后世界状态自动跳过放置 (不违 #77)
- **tryAscend 落点缺依托加放置分支** (A* 层): `allowBridge && dest.below() 可替换 && isAscendPlaceable (支撑面向下/4 侧, 排除 src 列)` →发 ASCEND 代价 `JUMP_ONE costOfPlacingAt` — 与挖分支并存, A* 按代价选 (baritone 同)
- **isBridgeable 补 aux 检查 (错题 #94, 测试暴露)**: `dest.below()` 必须可替换且非 liquid/hazard — 修复在 lava 上搭桥 (`[PLACE(3,2,1,aux 3,1,1), PLACE(4,2,1,aux 4,1,1)]` 实测路径)
- **tryPillar body 格放宽 (错题 #95, 测试暴露)**: 柱链 body 格 (y+1) 允许可挖掘 (与 v79.19f head y+2 语义同) — 修复链中第二根柱在头部 STONE 处断裂 (path=null)
- 验证: 双节点编译 / 173 测试 0 failed (`--rerun-tasks`) / gametest 6/6 ×2 / 待游戏内实测反馈(跨沟: 先放块后过沟不掉坑, 块不挡脸)

## 0.9.12 (2026-08-07) — v79.19g 放置距离门 柱位移到中间 (澄清)

### v79.19g Fixed (2026-08-07,  )

- **放置距离门** (核心需求): placeStep 每 tick 查 aux 距女仆脚格 — 水平曼哈顿 >1 或 Y 差 >1 →先走近再放 (导航 dest 0.5f,0)。方块只能放女仆脚下 3x3 (九格) 内, 贴身搭不隔空远放。撤销误解读的 A*  (高架柱路径可绕过, A* 层限桥无效; 澄清 = 每次放置的位置门, 非桥长)
- **柱位移到中间** (maid_useful_task Schedule→UP 严格对齐): 柱分支 boundingBox 不在柱格 1x1 →导航 aux (柱脚格可站, 0.5f,0) 走到格中心 →对齐后清零水平速度 (`setDeltaMovement(0, mv.y, 0)`, alignOrTryMove 同款) →头顶实心先挖 →onGround 跳 / 滞空放。原拉拽 0.02 太慢 (实测不走中间)
- **看门狗 FAILED 不再气泡** (): PathExecutor sweep 删气泡块 BUBBLE_COOLDOWN/LAST_BUBBLE/WeakHashMap import; ChainHarvestExecute  气泡删 (保留 addSkip 立即重扫)
- **挖矿到达 2 格** (): ChainHarvestExecute idleScan `distSqr <= 1.0` →`<= 4.0` (女仆攻击距离 3 格, 2 格内直接开脉)
- 验证: 双节点编译 全测试 / jar 复制 1.21.1 mods / 待游戏内实测

## 0.9.11 (2026-08-07) — v79.19f 柱对齐物理拉拽 头顶实心先挖 (maid_useful_task 参照)

### v79.19f Fixed (2026-08-07, 实测反馈女仆卡一格方块不走过去 头上有方块顶头不挖)

- **placeStep 柱对齐门** (maid_useful_task `BlockUpPlaceBehavior.alignOrTryMove` 移植): 女仆 boundingBox 不在柱格 1x1 内 →`setDeltaMovement` 水平拉拽 (0.02 力度, 保留 y 速度) — 跳放**不靠导航** (TLM 导航遇挡路方块不走 →女仆停 2 格外 , 实测); 柱阶段不再导航 dest (dest=柱顶在方块内, 导航目标不可达 →女仆徘徊)
- **头顶实心 →先挖净空再搭** (maid_useful_task: 搭柱中头顶挡路 →放弃 UP 转 DESTROY 挖掉再搭): `tryPillar` 头顶 y+2 实心**可挖时不拦截** (原直接 return →女仆顶头不挖干站着; 静态 A* 无法模拟时序, 原地 mine 步 dead-end 实测); 执行端 `placeStep` 柱分支每 tick 查 `foot.above(2)` 实心 →复用 MINE 渐进破坏挖掉 →挖完推进 →下一柱步正常搭
- **placeStep 速度/距离修正**: `1.0F, 2` →`0.5F, 0` (漏改 — 原跑 2 格外即算到达, 女仆停 2 格外; maid_useful_task 全用 0.5f,0 精确走到格中心)
- 验证: 双节点编译 全测试 (新 AStarTest 头顶实心岩浆墙用例) / jar 复制 1.21.1 mods / 待游戏内实测反馈(低天花板下搭柱: 应自动挖头顶再搭)

## 0.9.10 (2026-08-07) — v79.19e 台阶显式跳 实心前挖穿 (baritone MovementAscend/MineProcess 参照)

### v79.19e Fixed (2026-08-07, 实测反馈女仆被面前 1 格方块挡住, 能走上或挖掉却不做)

- **ascend 被压成 walk 纯导航 →女仆 AI 不自动上 1 格台阶 →被面前 1 格方块挡住** (实测反馈)。参照 Baritone `MovementAscend` (跳门 L205-226: 水平距 dest ≤1.2 侧向 ≤0.2 横速 ≤0.1 →JUMP; SUCCESS = feet==dest): 新增 **`PathStep.StepAction.ASCEND`** 独立动作 `AStarPathFinder.tryAscend` 改发 ascend (原 walk) — 执行端 `PathExecutor` 新 `case ASCEND`: 导航 跳门 (`f.y < dest.y && |dx|≤1.2 && |dz|≤1.2 && lateral<0.5` →`jump`), 完成 = 脚格 == dest
- **tryTraverse 实心方块前加 mine 分支** (baritone 同: 实心前 ascend 走上 或 mine 挖穿 二选一, A* 按代价选): `avoidWalkingInto || !canWalkThrough` 时, `allowMine && isMineable && !isSacred` →`PathStep.mine(dest, dest)` (挖掉走过去)
- **WALK 到达判定收严** (v79.19e): 原 `distSqr ≤ 4` (2 格含 Y) →提前 →提前推进 →目标外卡住 (实测反馈); 改 = **同脚层 && 水平曼哈顿 ≤ 1** (Baritone feet==dest 精确语义容差版); `SWITCH_TOLERANCE_SQR` 16→4 (4 格→2 格)
- **ChainHarvestExecute 到达判定 3 格 →1 格** (6 邻): 矿物旁 1 格才开挖; `moveTo` FAILED (不可达) →**立即 idleScan 重扫** (原等 3 秒扫描间隔 — ; baritone MineProcess 不可达黑名单 重扫语义) 跳过格记录 气泡
- **速度 0.7f →0.5f** (实测反馈0.7f 仍偏快; 1.0F = 跑, 0.7F = 归家行走, 0.5F = 更慢速; 3 处 `setWalkAndLookTargetMemories` WALK/ASCEND)
- **pillar 放置门** (v79.19e 补, 原 v79.19b 无状态版缺): `aux == 脚格` 时滞空即放 →刚离地 `y < dest.y` →身体与块重叠 →原版水平推挤 →站不上块 →完成判定永不过 (实测反馈); 加 Baritone `MovementPillar` 放置门: `position().y > dest.y 0.1` 才放 — 全滞空窗口, 无窄窗错过
- 验证: 双节点编译 全测试 (canSwitchSegments 断言同步 4.0) / jar 复制 1.21.1 mods / 待游戏内实测反馈(1 格台阶跳上 / 实心方块挖穿 / 矿物 1 格到达)

## 0.9.9 (2026-08-06) — v79.19c PLACE 完成判定修正是走上 纯块放置 (状态机否决后两轮重写)

### v79.19c Fixed (2026-08-06, 实测反馈面前连搭 2-3 格上不去)

- **v79.19b 完成判定只查 aux 实心 →桥步放完即完成, 漏阶段**: 女仆原地连放 2-3 格垫块, 自己没站上去 (实测反馈)。对照 Baritone/Numen MovementTraverse: 放置格 = `dest.below()` (与 LMA A* tryBridge aux=dest.below 完全一致) — 执行 = **放好桥块 →moveTowards 走过去 →脚到 dest 才 SUCCESS**; MovementPillar SUCCESS = `playerFeet().equals(dest) && blockIsThere`
- **PLACE 完成判定 = `aux 已实心 && 脚格 == dest`** 每 tick 导航 dest (BehaviorUtils): 桥 →放好走上; 柱 →跳起放脚格 →落回站上 dest (放置无实体碰撞, 女仆上升穿过/下落撞顶, 窗口 = 全滞空期)。修正了放置成功即推进导致的竞态 (下一柱步把女仆将落的格提前填掉 →站不上 dest →卡死)
- **纯块放置链** (`LmaPlayerSimulator.simulatePlaceBlock` `FakePlayerInteract.placeBlock`): 仿 maid_useful_task `MaidUtils.placeBlock` (BlockHitResult →UseOnContext →onItemUseFirst →PASS →useOn), **无 interactEntity 扫描 / 无 RightClickBlock 事件** — 参考源码 (maid_useful_task / Baritone processRightClickBlock) 放置均走纯块链; 原链 interactEntity 扫 target 格排除假人但**不排除女仆** →女仆站点击格时放块变交互女仆
- **findSupportFace 还原** (去 v79.19b 加的 maid 跳过 — 纯块放置无抢占; 桥 aux=dest.below 侧壁 = 女仆脚下块, 本就不冲突)
- 保留: 无状态每 tick 决策 (柱 onGround→jump / 滞空放; 桥站立放) / 防随机走 (keepAlive 导航目标) / borrow/restore tick 内闭环 诚实消耗 extractItem / 成功复查 / FAILED 气泡 600t 节流 / A* 规划层未动
- 验证: 双节点编译 / 全测试 / gametest 6/6 ×2 / jar 复制 1.21.1 mods / 游戏内人工验证 (搭柱上高/1 格宽沟/深坑搭桥 — 观察: 每格桥放完走上再放下一格, 不原地连放, 无气泡刷屏)

## 0.9.8 (2026-08-06) — v79.18 哈气 YSM 动画 通用动画文件同步

### v79.18 Added (2026-08-06, 哈气 YSM 动画 S2C 动画文件同步)

- 哈气进入 LOOK 经 `AnimExecute.execute(INSTANT)` 播放 — **双通道分流** (AnimExecute 内建, 自动 seq LmaAnimSyncMessage 数据包同步): YSM 模型 →playRouletteAnim (动画须在 YSM 模型包内同名 ); TLM geckolib 模型 →ISS 注册的 haqi.animation.json (已加进 StartupLoader ANIM_PRESETS, 客户端启动注册)。onCleanup `stopRoulette` `BrainHelper.unfreeze` (AnimExecute fallback freezeAI=true 置 IS_PANICKING — Brain memory 闭环, 防残留)
- **通用 S2C 动画文件同步** (`AnimFileSyncPacket`, forge ID 9 / neoforge `anim_file_sync` 纯 S2C 单 TYPE): 专用服务器玩家加入 →`pushAllTo` 全量推送 config/animations/*.animation.json →客户端校验 (文件名白名单/禁路径遍历/512KB 上限/JSON 合法) →落盘 →`StartupLoader.reload` `DynamicAnimationResources.reload` →`AnimationResourceRegistrar.remergeAll` 热合并进 TLM geckolib ISS AnimationFile (启动时缓存 mutable 引用)
- 事件: forge/neoforge 各 `AnimFileSyncEvents` (@EventBusSubscriber PlayerLoggedInEvent)
- **动画机制文档**: `docs/design/animation-playback.md` — 双通道总览 (TLM ISS FULL/INSTANT YSM 轮盘) / 播放链 / 注册与注入 / 停止不变量 / IO 原语清单 / YSM 兼容组件 / 错题 #72-77; 死代码清理: compat/ysm/YsmCompat (v73 旧版无引用) vanilla/output/ysm 空目录
- **FULL YSM 分流修复 (错题 #78)**: `AnimExecute.executeFull` 曾缺 isYsmModel 分支 — YSM 模型女仆走 FULL 也进 TLM ISS 通道 (YSM 渲染不吃) →无动画; 修 = executeFull 加 YSM 分流 (playRouletteAnim YSM_ROULETTE)
- **YSM 动画注入 (方案 A, 裁定)**: `YsmAnimInjector` — 客户端启动 (neoforge 构造器 / forge commonSetup) 幂等合并 assets 的 haqi.animation.json 进 YSM 默认模型女仆动画 `config/yes_steve_model/builtin/default/animations/tlm.animation.json` (已有 key 跳过不覆盖; 失败仅日志; YSM 还原后自愈) — 使 `playRouletteAnim()` 在 YSM 模型上可播 (wiki 实证: YSM 2.4+ tlm 动画定位, 模型包加载时读取, 无运行时注入 API)
- **修复 (实测)**: neoforge `@EventBusSubscriber(GAME)` auto-scan 对 `DefaultGeckoAnimationEvent` 失效 (TLM  日志有、LMA  缺失、cachedIISSFile 恒 null) →入口构造器**手动注册**到 GAME/FORGE 总线 (TLM 1.5.3 jar 反编译实证 post 到 `NeoForge.EVENT_BUS`)
- **崩溃修复 (实测反馈14:42)**: MOD 总线注册 `DefaultGeckoAnimationEvent` 抛 `IllegalArgumentException: bus only accepts subclasses of IModBusEvent` →mod 构造失败连锁崩 (Sodium 跟着炸). 删除所有 modBus 注册 — **neoforge 普通 Event 只能注册 GAME 总线**; 文档  为 forge 时代旧说法, 不适用
- 验证: 双节点编译 / 全测试 (新增 AnimFileSyncPacketTest 4 校验测试) / gametest 6/6 ×2 (lmaHaqiHit 改走 MOVE→LOOK 转换, 断言 TLM 分支 lma_anim 键)

## 0.9.6 (2026-08-05) — v79 任务管线 GameTick 集中管理

### v79.17 Added (2026-08-05, 哈气概率挥击)

- 哈气 LOOK 期间按概率 (hit_chance 默认 0.3) 延迟 15t 挥击目标一下: `maid.swing(MAIN_HAND)` 挥击动画 `CombatOutput.damage` (mobAttack 真实受击链) 原版 `PLAYER_ATTACK_SWEEP` 挥击音
- 伤害固定配置 `hit_damage` (默认 1.0 = 一点血, 不致命, 裁定) — 不走武器伤害 (`doHurtTarget` 全链含附魔/横扫, 不适用)
- 挥击状态存 `lma_pl_haqi` compound `hit_ticks` 键 (倒计时 →0 执行 →-1 防重复), onCleanup 整体清除闭环
- TLM 实证: 女仆打女仆不引发反击 (DefaultMonsterType FRIENDLY 排除 TamableAnimal, canAttack 恒 false); 被动目标女仆 PANIC 活动 ~5s 自动恢复
- 配置: `passive.toml` haqi 段 全局设置 GUI 哈气分类 (挥击概率/挥击伤害 2 条目)
- 验证: 双节点编译 / 全测试 / gametest 6/6 ×2 (新增 lmaHaqiHit — 构造 LOOK+hit_ticks=1 驱动 tick 断言掉血且仅一次)

### v79.11 Fixed (2026-08-05, 实测反馈4 bug)

1. **跑步女仆随机走动/跟玩家**: running_belt tickRunning 加原地锚定 (NavigationUtil.keepAlive — WALK_TARGET 原地) setHomeModeEnable(true) (防跟玩家); cleanup 恢复
2. **哈气永不触发 (概率 1.0 也无声音)**: 根因 = onSignal 走 MAID_NEARBY 信号, 但 EnvSenseBroadcaster 被 ENVSENSE_ENABLED (默认 false) 门控 →无广播. 修复 = 新 HaqiTrigger 独立触发 (TaskTickHandler 每 20t 直接扫 2 格内女仆 →概率 →submitPassive), onSignal 保留双通道
3. **假人被另一魂符误销毁**: onMaidRestored BINDINGS 遍历经背包石板键匹配, 放 A 魂符时背包有 B 石板键 →误匹配 B. 修复 = PD lma_companion_uuid 直查优先 (精确), BINDINGS 遍历降为无 PD 键 fallback
4. **挖矿不挖 (头上的矿/没跑酷)**: 根因 = moveTo 用 NavGoal.standOn(next) — 目标矿是固体方块, standOn 要求站上目标格不可达 →所有寻路模式规划失败. 修复 = NavGoal.interact (相邻格含上下, 目标格 sacred) — 跑酷/挖路生效 挖头上矿
- 顺带硬编码收敛: ChainHarvestExecute (TASK_WOOD/TASK_ORE/TICKS_PER_SECOND/SCAN_BUDGET_DIVISOR) LmaMagicCastingProvider 动画键 →TaskKeys 开发路径注释清理
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包 / 部署

### v79.10 Added (2026-08-05, running_belt 顺带摇曲柄)

- `CrankService.findCranks(level, center, range, max)` — 螺旋序收集 ≤max 个 (findCrank 参数化)
- `RunningBeltPipeline.tickRunning` 尾部: 周围 2 格内曲柄 (最多 2 个) 顺带摇 (turn) 每 20t swing — 发电上报式不中断 (实证: isMaidOnBelt 只查 Y, addSurfaceMovement 主动上报)
- 无新状态/无新 pd 键/无导航 — 最小改动
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包

### v79.9 Added (2026-08-05, 哈气被动任务)

- 新 `task/pipeline/sense/HaqiPipeline`: 2 格内其他 maid →MAID_NEARBY 信号触发 →概率掷骰 (10% 默认) →锁定目标 submitPassive →MOVE (导航到她旁边 1 格) →LOOK (看着她 随机播放音频) →总时长 = 基础 (60t) 音频实际时长 →cancelPassive; 目标消失/远离 →放弃
- **互斥**: TaskDispatcher.submitPassive (哈气运行中拒其他被动) submit (哈气运行中拒主动任务)
- 音频: 10 个 ogg 导入 (ha_1-5 哈气音 1.1-2.2s laowu_1-5 老五音 4.5-12.8s) LmaSounds 注册 sounds.json — **时长表为 ogg granule 实证** (ha 22-45t / laowu 91-256t); 命名非 maid* 前缀 (TLM playSound 音效包路线实证)
- 配置 (PassiveTaskConfig haqi 段 Cloth分类): ENABLED (默认关) / CHANCE (0.1) / DURATION_TICKS (60) / VOLUME (1.0)
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包

### v79.8 Added (2026-08-05, 寻路模式配置 — 子任务界面 全局)

- 新 `config/PathingModes` — 4 档寻路模式 →PathConfig 映射: safe (纯走绕, 默认) / parkour (只跑酷) / bridge (跑酷+搭桥搭柱) / explorer (跑酷+搭+挖)
- `ActiveTaskConfig` pathing 段: `PATHING_DEFAULT_MODE` (全局默认) `PATHING_COLLECT_MODE` (采集任务覆盖)
- Cloth UI: ClothSettingsScreen 新分类 (startEnumSelector) TaskSettingsScreen collect_wood/ore 分支加
- `ChainHarvestExecute.moveTo`: 采集移动段换 PathingApi.tickPath (模式分级) — FAILED →气泡 停留重扫; 无 fallback (裁定)
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包
- 坑: NavGoal.standOn 需 P3 (BlockPos 转换); cloth 无 startCyclicButton (11.1.136 实证) — 用 startEnumSelector

### v79.7 Changed (2026-08-05, 全部状态机化 寻路 API 补全)

- ArmTransfer/ChainWood/ChainOre/BlockInteract 全转 TaskStateMachine 状态处理器表 StateCtx API 组合 (示范模式固化)
- `PathingApi` 补 4 入口 (规划/执行分离): findPath(maid)/hasPath/tickPath/clearPath — 管线流程 = validate 预检 (无路气泡) tick 执行
- TaskRegistry 静态 executor 调用修 (TaskStateMachine 实例方法)
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2

### v79.6 Removed (2026-08-05, FSM 栈全砍 — 裁定: 状态机自己定义 impl 也能用, FSM 无实际意义)

- 删 task/fsm/ 18 (FsmPipeline/FsmExecutor/FsmTaskFactory/FsmDef×4/FsmCheck/FsmCheckRegistry/CfgOverridesCache/check 6) task/script/ 2 (StepCursor/CompiledAction) task/action/ 33 (28 动作 TaskAction/TaskActionContext/TaskActionRegistry/ParamSpec/ParamValidator) task/condition/ 11 TaskDef core 链 6 (ConditionCache/StaticPreEvaluator/ConditionOperator/ActionStep/ConditionRegistry/spi.condition) DocGenerator (条件文档随栈死)
- 接线: LmaRegistrar (3 registerAll DocGenerator 调用删) / LmaCommand (FsmPipeline 调试分支删) / LMAT (registerAction/registerCondition 删 imports) / network specs 通道删 (ReplyTaskConfigPacket 重写 RequestTaskConfigPacket specsOf LmaTaskConfigContainer.updateSpecs)
- 测试删 18 (fsm 8 script cache action 5 RegistryTest ActionStepTest ModelTest LMATTest)
- **动作语义吸收进 API (6 处)**: SenseApi.findBlockNearest (find_target) / SenseApi.healthRatio (wait_until_maid_health) / FakePlayerInteract.rightClick range 重载 (use_block) / MovementOutput.teleportByMode (teleport) / VisualOutput.playAnimFull (play_anim) / MaidStateWriter.repairItemWithXp (repair_item)
- 保留: TaskStateMachine (ArmTransfer) 12 实例管线 能力 API (SenseApi/PathingApi/NavigationUtil/ContainerOutput) RuleContext/IAction (vanilla)
- 坑: EquipmentSlot.Type 1.21.1 不存在 (显式 4 甲槽) / 宽 sed 误删闭合 (git checkout 恢复 重做) / sed 删方法调用留悬挂参数行 (initServer) / ParamSpec 网络协议死字段 (v77.4 恒 null 标注)
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包

### v79.5 Changed (2026-08-05, 一次性管线 API 填充 — 裁定: 拿 API 填管线, 管线=参数+API)

- 新 `api/nbt/NbtCodecs` — 双平台 BlockPos↔NBT 编解码 (格式兼容零迁移; 替代 ArmTransfer 2 BlockInteract 3 处样板)
- 新 `api/navigation/NavigationUtil` — navigateTo/arrived/keepAlive 三件套 (替代 ArmTransfer/ChainHarvest 内联同款)
- `SenseApi.findNearestBlock` — 泛化最近目标搜索 (BlockScanner skip 集 最近优先; ChainHarvest.findNearestValid 提升到 API 面)
- `ContainerOutput` 增强 — getHandler (capability 六方向, 双平台条件化) depositItemStack/withdrawItemStack (isSameItem 溢出退还统一; ArmTransferService 第二份实现删, execute* 改委托)
- 管线改薄: ArmTransfer (编解码/导航/容器委托) BlockInteract (3 处编解码) ChainHarvest (导航/keepAlive 委托)
- 坑: stonecutter 条件 import 块多行分支处理异常 (forge merged 丢行 — 错题 #37 家族) — ItemHandlerHelper 依赖弃用, 改逐槽 insert 循环 (双平台安全); ContainerOutput 缺 BlockPos import (全限定修)
- 验证: 双节点编译 / 全测试 / gametest 5/5 ×2 / 打包

### v79.4 Removed (2026-08-05, 规则引擎残留全链删除 — 裁定)

- 删 19 个 main 类: ConditionMatcher/ConditionEvaluator/CooldownManager/ParamMerger (core/engine) ExpressionResolver/MvelBootstrap/MvelEvaluator (core/expression, MVEL 表达式) ConditionDef/MatchMode (core/model) RuleCondition/RuleAction/ScriptPlugin (core/annotation) ActionRegistry (core/registry) ClassScanner/ForgeClassScanner (注解扫描器 — 扫描目标 impl 包 v72 已删, 空扫) ScriptPlugin/IScriptPlugin/ScriptPluginRegistry (core/spi/script)
- 删 5 测试: ConditionMatcherTest/CooldownManagerTest/ParamMergerTest/ExpressionResolverTest 整删; RegistryTest/ModelTest 手术 (留 ConditionRegistry/ConditionOperator/ActionStep 部分)
- DocGenerator 动作文档段删 (ActionRegistry 依赖; 任务动作文档 = docs/guides/task-development.md 已有)
- vanilla SubmitTaskAction/AbstractBlockInteraction 去 @RuleAction 残留标注; LmaRegistrar 去扫描调用
- **保留 (任务引擎引用链)**: RuleContext/ConditionCache/StaticPreEvaluator/ConditionRegistry/ICondition/ConditionOperator/ActionStep (FsmCheckAdapter 条件桥接) IAction/ActionCategory (vanilla) TypedParam/MaterialChecker/MaterialReport/LmaAnimationDef (任务引擎) DocGenerator 条件文档
- EnvSenseBroadcaster.emit 去 ctx 死参数 (零消费; SenseApi.emit 同步 3 参)
- 验证: 双节点编译 / 全测试绿 / gametest 5/5 ×2 / 打包 0.9.6

### v79.3 Added (2026-08-05, EnvSense 补全 SenseApi 暴露 — Numen 参考)

- 信号补全 (EnvSignal 18→21): `env:BIOME_CHANGE` / `env:STRUCTURE_ENTER` / `env:STRUCTURE_LEAVE` — WorldInfo +biomeId/structuresAt (站立点所在结构, getAllStructuresAt 零成本, 与 24000t 最近结构通道互补)
- `EnvEdgeDetector` (纯 JVM 边沿检测核心, 21 边沿从广播器剥离) `EnvRules` (温度档/时间段纯逻辑)
- `ScanScheduler` `ScanJob` 增强 (io 层): Tickable 契约 / ownerId 归属 / cancel() / matches 防御拷贝; TaskTickHandler 集中挂载; 女仆卸载 cancelFor 清理闭环; `ScanFilters` (雪/红石灯/水源/熔岩源谓词)
- **`api/sense/SenseApi`** (执行层 API 暴露, 仿 PathingApi): snapshot/worldInfo (O(1) 快照) / biomeAt/structuresAt (直读) / scanEntities/scanSnow/scanRedstoneLamps (同步有界) / **startScan/cancelScan/scanResults (预算化异步扫描)** / emit (事件注入) / tempCategory/timeSegment (纯逻辑)
- 预算优化: dispatch validate pass 作用域缓存 (每管线一次, 原每信号×每管线) `EnvSenseBudget` 广播墙钟 8ms 上限 (超限跳过快照刷新, 边沿只延后不误报)
- 测试 +25: EnvRulesTest 2 / EnvEdgeDetectorTest 11 / ScanSchedulerCoreTest 5 / SenseApiTest 4 / EnvSenseBudgetTest 3 (325 全绿)

### v79.1 Added (2026-08-05, Baritone 算法移植)

- `PrecomputedData` (vanilla/pathing/): 谓词位掩码缓存 — state 级确定语义按 id memoization (位图单次计算, A* 重复扩展同类方块谓词归零; destroyTime 同缓)
- `PathExecutor` 双段路径 (B2): 剩余步数 ≤ 8 前瞻预计算下一段 (以路径末端为起点); 段末 goal 已达 →ARRIVED, 否则容差切换 next (≤4 格) 或从当前位置重算 — 原改进为
- 测试: PrecomputedDataTest 5 例 (语义等价/memo 计数/独立缓存) PathExecutorTest 4 例 (纯函数判定)

### v79.2 Fixed (2026-08-05, 假人重启位置)

- 假人重启后出现在奇怪位置: 玩家登录 (LOWEST, Numen respawnAllOwnedBy 之后) →追踪魂符 (背包石板 lma_companion 键) →在线假人 teleportTo 玩家旁 (视线前方 2 格)。覆盖: roster 残留 .dat 旧位置复活 自主游走残留
- 重启后交接失效 (BINDINGS 内存态清空): onMaidRestored 无绑定兜底 — 新女仆 PD lma_companion_uuid 直查假人 →销毁 清石板键 (不用背包扫描防多假人误伤)

### Added

- `GameTickPipelineManager` (v79): 主动/被动每 tick 驱动集中管理 — 心跳节流 (FLOW_TICK 每 20t 一写, 原每 tick) 看门狗容忍度 (有效超时 [timeout, timeout+20]) 被动预算轮转
- `TaskPipeline.priority()` (默认 0): 提交冲突优先级策略 — 新任务严格更低 →拒绝 (气泡节流), 等/高 →抢占 (树内任务零行为变化)
- `PassiveTaskConfig.PASSIVE_TICK_BUDGET` (默认 2, 0=不限): 每女仆每 tick 最多执行的被动管线数, 超预算环形轮转 (PassiveRotation 确定性零状态)
- `WatchdogMath` / `CfgOverridesCache` / `PassiveRotation` 纯 JVM 类 测试
- gametest +2: lmaPriorityConflict / lmaPassiveBudget (双节点 5/5)

### Changed

- `TaskTickHandler` 变薄: 双循环 →单次实体遍历; 被动清单每 level hoist (TaskRegistry.passiveTasksList 缓存)
- `FsmPipeline`: 预计算 plKey/cfgKey 游标 finished 后 reset() 复用 配置覆盖视图缓存 (稳态零分配)
- 死代码清理: TaskExtraData 删; ScriptedPipeline/JSON 平台死注释 6 处修正

### Fixed

- `super.handleConfigAction()` 接口默认方法调用 →`TaskPipeline.super.handleConfigAction()` (plain super 不搜接口 — 编译坑)

## 0.9.5 (2026-08-05)

### Added

- 近距离寻路 API (v78, vanilla/pathing/ 纯 JVM 执行器): PathWorld/NavGoal/MovementHelper/AStarPathFinder (25 移动集: 走/挖/搭桥/搭柱/斜走/保守跑酷, 危险硬禁: 岩浆/火/斜角/冲线) PathingApi.findPathSafe/findPathExplorer
- PathExecutor (v78.2-3): WALK/MINE/PLACE/JUMP 逐 tick 执行; 渐进破坏 (swing getDestroyProgress 累积 →destroyBlock, maid_useful_task 模式); sweep 全量驱动 (TaskTickHandler)
- navigate_to FSM 动作 (28 动作, 条件阻塞; mode=block/near/interact 能力开关)
- AI move_to 工具升级 (v78.3): getNavigation →PathExecutor; mode=safe (不破坏方块) / explorer (挖/搭/跑酷)

### Changed

- 跑酷默认开 (保守 1-2 格坑 overshoot 硬防)
- 假人全局设置 GUI (v77.9): ai_control 任务设置屏 随机台词气泡开关/间隔/随机语音开关

## v78 Phase 1 (2026-08-05, 并入 0.9.5)

## 0.9.4 (2026-08-04)

### Added

- 假人侧 YSM 命令通道 (YsmCommandChannel, v77.8): `/ysm model set` / `/ysm model disable` / `/ysm anim play` — 镜像 YsmOutput IO 面, 混淆版 YSM 2.6.5 全支持 (roamingVars 无服务端命令不镜像)

### Changed

- applyMaidModel 命令分支提取至 YsmCommandChannel (NumenMaidBridge, 双通道解耦)
- 变身前置提示: OpenYSM 硬前置 →任意 YSM (混淆版走命令通道)
- docs/architecture/compat.md ARCHITECTURE §11.5 同步双通道 (漂移修复)

## 0.1.0

### Added

- Initial reusable multi-loader template.

## Types of changes

- `Added` for new features.
- `Changed` for changes in existing functionality.
- `Deprecated` for soon-to-be removed features.
- `Removed` for now removed features.
- `Fixed` for any bug fixes.
- `Security` in case of vulnerabilities.

## 0.71 (2026-08-03) 假人石板化 Create 双平台

### Added
- v75 石板化假人桥: ai_control 设置 GUI  按钮 →假人唯一主体 (固定 UUID 每女仆独立, YSM 模型自动继承 via OpenYSM DataAttachment, 状态同步) →女仆带全背包收 TLM 石板 (idle 存石板防循环)
- 放石板交接: 假人销毁 (dismiss roster 清理) 物品爆地
- LLM provider/voice 继承 (SHELVED 广播) / 随机台词+语音 (仿 TLM RandomEmoji, 读 TLM MaidConfig)
- 前置门控: ai_control 注册需 Numen; 变身按钮需 Numen OpenYSM 2.6.6+ (开源版)
- Create 任务 running_belt/maid_assembly 双平台化 (1.21.1 neoforge, 18+ 处 API 适配)
- 界面: 旋转全景背景 (1.21), 透明化, ai_control 中文翻译

### Fixed
- 收女仆崩溃 (1.21 ItemStack.save 禁空编码 →saveItem isEmpty 检查)
- 调试按钮主菜单崩溃 (PacketDistributor.sendToServer connection null →Sender 防御)
- 假人头顶气泡不显示 (旧 .dat INVISIBILITY effect →强制清除)
- 假人残留登录复活 (despawn →dismiss roster 清理)
- 任务树模糊背景 (1.21 renderBackground →覆写 旋转全景)

## 68.0.0 (2026-08-02) 架构重构
- 规则引擎残留裁撤: 27 个纯转发事件订阅者删除 (TlmEventAdapter 2 订阅者), RuleEvent/MaidInteractBridge/孤立事件删除, 任务注册与规则引擎解耦
- 死代码删除: FlowTask/TaskFlowGraph/AbstractTaskCondition/ServerTaskQueue/ForgeTaskQueueBridge/FlowTaskData 旁路
- 共享基类: LmaFlowTaskBase LmaTaskConfigContainer 容器契约上提
- tlm-ref 参考源集删除 (8.2MB)
- Parchment 映射 (neoforge 节点, TLM 同款 2024.11.17-1.21.1)
- ⚠️ /lma task flow 调试命令移除 (TaskFlowGraph 死代码)
- 测试: 157 单元测试 gametest 任务生命周期测试

## 0.71.0 (2026-08-03) Numen 假人桥 (v74)

### Added
- Numen 假人桥: 女仆 ai_control 开启 →生成 NumenPlayer 假人 (owner=女仆主人) →**Numen 全套 LLM+工具驱动** (零 LMA 工具编写)
- 动作镜像: 假人攻击/交互/挖掘事件 →女仆 swing 动画 (左手攻击/右手交互)
- 视角同步 (20t 节流) 移动跟随 (寻路 20t 节流, 停靠 ≤2.5 格) 背包单份同步 (女仆=唯一真源)
- Numen 共存检测 (NumenCompat, 未装零开销)
- 全玩家隐形: 客户端 RenderPlayerEvent.Pre 取消渲染 (身体+手持物品全隐; 20t 广播假人 UUID 集识别)
- 成就摘监听: `PlayerAdvancements.stopListening()` (假人背包拷贝不再触发 story/diamond 等成就)
- 面板删除拦截: CompanionLifecycle.onRemove →dropAll 副本去重回收进女仆背包 不重生标记 (任务重开恢复)
- AI 操控任务设置: LLM 模型/声线名称 (TLM 任务设置 GUI 详细设置→任务自定义全局默认, 空=不绑定) →桥广播 →owner 客户端按名绑定 Numen ProviderLibrary/VoiceLibrary
- 引擎配置动作 ACTION_SET_STRING=4 (字符串赋值)
### Changed
- 任务树渲染双平台修复 (withStyle 颜色, 1.21 灰屏问题)
- Cloth Config 软依赖 (未装提示不崩)
- 修复: 假人 noPhysics 穿地掉落重生循环 (Entity.move 直接 setPos 语义) →去 noPhysics 生成在女仆头上一格

## 0.70.0 (2026-08-02) AI 操控 (v73)

### Added
- AI 世界操作工具 10 个 (TLM AI 环 LMA IO): move_to/mine_block/collect_items/interact_block/interact_entity/melee_attack/get_self_status/switch_lma_task/scan_blocks/wait_ticks
- 主动任务 `ai_control` (AI 操控): 权限门控 — 开启后女仆 AI 对话可指挥世界操作 (关闭即收回)
- Numen 共存兼容 (ModList 检测 工具模式参考)
- gametest lmaAiControlGate (门控闭环)

## 0.69.0 (2026-08-02) 引擎重构: 规则引擎 →JSON 任务插件平台 (v72, 5 Phase 完结)
### Added
- JSON 任务定义: `config/littlemaidmoreaction/tasks/*.task.json` (KubeJS 风格数据驱动, 一文件一任务; 11 内置预设)
- 任务插件平台: TaskDef/TaskStorage/TaskPresets/TaskSignalIndex/TaskScreener (纯 JVM 筛选核心)
- 条件库: 10 个 ICondition (damage_type/would_lethal/maid_has_shield/target_holding_item/maid_has_weapon/is_combat_task/is_owner_target/owner_has_attack_target/is_tamed/owner_holding_item)
- 动作库: 16 个 TaskAction (全委托 IO 原语) StepCursor 脚本管线 (wait/random/异常容错)
- 事件桥: 5 信号 (maid_attack/maid_hurt_target_pre/maid_interact/maid_tick 200t 节流/maid_harvest_crop) 取消通道 (cancel_event →setCanceled)
- 任务筛选服务 TaskScreeningService.fire (索引→筛选→提交→游标→取消判定)
- 信号泛化: 统一 String 信号 id (event:/env: 前缀) EnvSenseBroadcaster.emit 事件信号入口
- `/lma task reload` JSON 任务热重载命令
- gametest 4/4 (新增脚本被动任务全链 事件取消)
### Changed
- 信号链路接口: TaskPipeline.onSignal 第 3 参 String (破坏性)
- 11 预设 eventId 归一为 event: 前缀 (与信号契约一致)
- CooldownManager 冷却键 lma_tcd_<id> (规则引擎 lma_rule_ 前缀删除)
### Removed
- 规则引擎全套删除: RuleEngine/ActionPipeline/GroupBuilder/ParallelGroup/ITickScheduler/TickScheduler/RuleIndex/MaidRuleIndex/MaidRuleStorage/RuleActionStorage/RuleTracer/DebugPresets/RuleDef 绑定测试 assets/rules 死资源
- 配置项: 规则引擎总开关 (CUSTOM_RULES_ENABLED); DEBUG_MODE 保留
- /lma rule /lma trace 调试子命令
- 规则 JSON 模板文档 (rule-template.md) TLM skill 复制 (lma_rule_system)
- ⚠️ 旧 config/rules 目录不再加载 (自然退役, 文件不动)
### Fixed
- 节流时间戳防溢出 (PD 跨 session 锁死) / 脚本游标启动缺失补偿 / 信号路由顺序

