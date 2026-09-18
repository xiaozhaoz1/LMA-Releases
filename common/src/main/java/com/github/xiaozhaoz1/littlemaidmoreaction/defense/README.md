# defense — 防御塔域 (独立域, 不走 TaskRegistry)

**作用**: 便携防御塔 (garage_kit_defense): 放置 → 开 GUI 装武器/弹药/设半径 → 自动索敌射击。
**依赖方向**: TLM + 原版 + config + `task/service`(FurnaceService 无) + `api`; **不被** task/* 依赖 (独立域)。

## 一、组成 (11 类)
| 类 | 行数 | 职责 |
|---|---|---|
`DefenseGarageKitItem` | 78 | 物品: 右键放置成塔 / 打开 GUI |
`DefenseGarageKitBlock` | 211 | 方块: 放置/破坏 (掉落物保留 = 容器内容) |
`DefenseGarageKitBlockEntity` | 467 | **核心**: 每 tick 索敌/开火/半径/耐久/掉落保留; 火控与弹道已抽出 (v79.63) |
`DefenseTowerFire` | 209 | **火控子系统** (v79.63 抽出, 无状态): 视线检查 / 武器分流 (弓/弩/御币弹幕) / 弹药查找消耗 / 投射物生成 / 耐久 |
`DefenseTowerLogic` | 155 | **纯函数**: 相位抖动 (`phaseOf`/`isPhaseTick`, 多塔错开避免同 tick 齐射被受伤吸收窗口吞) + 弹道 (`aimAt`/`ballisticDrop`) |
`DefenseTowerMenu` | 147 | 容器菜单: 槽0=武器, 槽1-9=弹药, 后接玩家背包; DataSlot 同步武器模式/锁定态 |
`DefenseTowerScreen` | 128 | 客户端屏 (原版 `AbstractContainerScreen`, 用 `imageWidth/imageHeight`) |
`DefenseTowerTickHandler` | 63 | 每 tick 驱动所有塔的 `towerTick()` |
`DefenseTowerRenderer` | 131 | 方块实体渲染 (仅平台源引用) |
`DefenseGarageKitRecipe` | 150 | 合成配方定义 |
`DefenseGarageKitRecipeSerializer` | 35 | 配方序列化 (双平台分支) |
`DefenseTowerConfig` | (config/) | 塔全局配置 (伤害/半径/间隔) |

**伤害口径 (v79.64.4 双平台已对齐)**: 单塔 GUI 覆盖值 > 全局 `defense_tower.damage` (默认 2.0 = 1 心) ⇒
箭塔走 `AbstractArrow.setBaseDamage` (1.21.1 亦同, 此前该分支**缺失** ⇒ neo 忽略配置 ✗), 弹幕塔走 `DanmakuShoot.setDamage` ✓。
差异备案: 1.20.1 把力量附魔写进公式 (有覆盖时力量不再叠加); 1.21.1 由原版在命中时 `modifyDamage` 叠加 (力量仍追加) ✓

## 二、连接链
```
玩家右键放置 → DefenseGarageKitBlock/BE 建档 (owner UUID)
  → 右键塔 → DefenseTowerMenu (槽位 = BE 的 SimpleContainer `getAmmo()`)
  → DefenseTowerTickHandler 每 tick → BE.towerTick()
        ├ 相位闸门 (DefenseTowerLogic.phaseOf, 仅相位 tick 活动)
        ├ 扫描/射击闸门 (间隔节流) → 候选 (AABB 水平半径 + 全高) → LOS 筛选 (Fire.hasLineOfSight)
        └ DefenseTowerFire.fire(...) → 弓/弩消耗箭 + hurtAndBreak / 御币弹幕
```
配置 GUI 与容器屏均在 **defense 自管** (不进 `task/gui`) ⇒ 改这里别去动 `TaskConfigGuiActionPacket`。

## 三、已知陷阱 (每条附事故)
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **残留塔会射击后续用例的目标** | gametest 世界共享 + 塔不自灭 ⇒ 后续用例目标被"别人的塔"打死。塔测试必须 **① 各自独立 `batch` ② 开始时按固定清单清塔** (5 个常用塔位, 见 `LmaGameTests.clearTowerTestBlocks`)。|
**B** | **多个被测对象不能共用靶子** | `lmaDefenseTowerDurability` 原设计三塔共用 3 只僵尸 ⇒ **弹幕塔(不耗箭)先把靶子全打死** ⇒ 弓/弩`damage=0` 看似"塔坏了"。⇒ **每个塔配对齐同 z 的专用靶** + 拉开塔位 (z=3/8/13)。|
**C** | **半径 × AABB 全高 = 会打到隔壁测试的怪** | 半径 64 + 全高 AABB 曾把邻近用例的僵尸当成"最近目标" (判决实验: FIRE 时箭全部脱靶)。⇒ 塔测试半径别开满, 目标放**模板内** (16³; x/z >15 无地面会掉落)。|
**D** | **不要用扫描清塔** | `±64` 立方体逐格 `getBlockEntity` ≈ 81 万次/调用 ⇒ 拖垮测试服务器 (比不清还糟)。用**已知坐标清单**。|
**E** | **排查开关** | `[TOWER-DIAG]` 门控诊断: 启动加 `-Dlma.towerDiag=true` **或环境变量 `LMA_TOWER_DIAG=1`** (⚠ `-D` 只作用于 Gradle JVM, 传不进游戏进程 ⇒ 实际用环境变量)。决策点日志会打 扫描/闸门/候选/LOS/开火。|
