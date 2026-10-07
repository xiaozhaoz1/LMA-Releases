# defense — 防御塔域 (独立域, 不走 TaskRegistry)

**作用**: 便携防御塔 (garage_kit_defense): 放置 → 开 GUI 装武器/弹药/设半径 → 自动索敌射击。
**依赖方向**: TLM + 原版 + config + `task/service`(FurnaceService 无) + `api`; **不被** task/* 依赖 (独立域)。

## 一、组成 (14 类 in-tree = tower 7 + garage 5 + client 2; 2026-09-21 分子包)

**结构** (2026-09-21 拆分 — 原 12 类同包平铺 ⇒ 按功能分三子包):
`tower/` 塔本体与火控 · `garage/` 车库套件 (方块/物品/BE/配方) · `client/` 客户端专用 (手办渲染驱动 / YSM 可用性)。
> 拆分代价备案: `DefenseTowerFire.hasLineOfSight` 由**包私有升 public** (跨包被 `garage.DefenseGarageKitBlockEntity` 选目标时调用)。

| 子包 | 类 | 行数 | 职责 |
|---|---|---|---|
| `tower/` | `DefenseTowerFire` | 235 | **火控子系统** (无状态): 视线检查 (`hasLineOfSight`, public) / 武器分流 (**仅弓/弩**; v79.66n 御币弹幕已删) / 弹药查找消耗 / 投射物生成 / 耐久 |
| `tower/` | `DefenseTowerLogic` | 173 | **纯函数**: 相位抖动 (`phaseOf`/`isPhaseTick`, 多塔错开避免同 tick 齐射被受伤吸收窗口吞) + 弹道 (`aimAt`/`ballisticDrop`) |
| `tower/` | `DefenseTowerMenu` | 148 | 容器菜单: 槽0=武器, 槽1-9=弹药, 后接玩家背包; DataSlot 同步武器模式/锁定态 |
| `tower/` | `DefenseTowerRenderer` | 177 | 方块实体渲染 (仅平台源引用) |
| `tower/` | `DefenseTowerScreen` | 128 | 客户端屏 (原版 `AbstractContainerScreen`, 用 `imageWidth/imageHeight`) |
| `tower/` | `DefenseTowerTickHandler` | 64 | 每 tick 驱动所有塔的 `towerTick()` |
| `tower/` | `DefenseTowerAnimNames` | 48 | **手办施法动画名护栏** (拒绝 `iss:` 前缀, 回退安全默认) |
| `garage/` | `DefenseGarageKitBlockEntity` | 717 | **核心**: 每 tick 索敌/开火/半径/耐久/掉落保留; 火控与弹道已抽出 (v79.63) |
| `garage/` | `DefenseGarageKitBlock` | 211 | 方块: 放置/破坏 (掉落物保留 = 容器内容) |
| `garage/` | `DefenseGarageKitRecipe` | 150 | 合成配方定义 |
| `garage/` | `DefenseGarageKitItem` | 78 | 物品: 右键放置成塔 / 打开 GUI |
| `garage/` | `DefenseGarageKitRecipeSerializer` | 35 | 配方序列化 (双平台分支) |
| `client/` | `YsmAnimAvailability` | 225 | **YSM 动画可用性解析**: 按模型 id 直查模型包 `animations/iss.animation.json` (custom 加密包 ⇒ UNKNOWN) |
| `client/` | `TowerStatueAnimationDriver` | 149 | **手办动作显示 — YSM 通道客户端驱动** (瞄准持续 + 开火触发; 原生 gecko 通道不经本类) |
| (config/) | `DefenseTowerConfig` | — | 塔全局配置 (伤害/半径/间隔) — 住 `config/`, 表内列出仅为交叉索引 |

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
        └ DefenseTowerFire.fire(...) → 弓/弩消耗箭 + hurtAndBreak (v79.66n: 御币弹幕攻击已删)
           ⚠ **武器耐久口径 = 原版 (用户裁定 2026-09-20 ✓)**: 每发 `hurtAndBreak(1, owner, …)` ⇒
             **耐久附魔照常生效** (1.20.1 `ItemStack.hurt` 逐点掷骰 / 1.21.1 `EnchantmentHelper.processDurabilityChange`)
             ⇒ 耐久 III 时约 1 点/4 发 (用户实测"射几十发条几乎不动" = 正常 ✓, 不是只扣一次 ✗);
             不加"无视附魔"开关 (裁定保持原版 ✓)                            |
```
配置 GUI 与容器屏均在 **defense 自管** (不进 `task/gui`) ⇒ 改这里别去动 `TaskConfigGuiActionPacket`。

## 二·五、手办动作显示 (v79.66, 用户需求: "像正常女仆一样射箭/用弹幕")

**数据流**: 塔 BE 决策到"有目标" ⇒ 往手办 NBT 的**实体持久数据子标签** (`ForgeData`/`NeoForgeData`) 写 `lma_anim*`
(**FULL/CASTING** = 瞄准循环) → 开火 ⇒ 默认**只"松手"**(不写, 让拉弓保持) → `sendBlockUpdated` 同步给客户端。
(⚠ 写顶层键客户端 `getPersistentData()` 读不到 ✗ — 第一版就栽这)

| 通道 | 适用模型 | 驱动 |
|---|---|---|
| **ISS (原生 gecko)** | TLM 原版模型 | 客户端 `LmaMagicCastingProvider` 读假女仆 PD ⇒ 播 `use_mainhand:bow` 等 |
| **轮盘 (YSM)** | `isYsmModel()` | 新增 `TowerStatueAnimationDriver`: 遍历 `STATUE_CACHE`, 只认防御塔, `playRouletteAnim`(seq 感知) |

**实测动画语义** (2026-09-20 直接读模型动画文件):
| 动画名 | 内容 | 用途 |
|---|---|---|
`use_mainhand:bow` | 60s, 关键帧到 t≈0.75~2.0s, `loop=hold_on_last_frame` | **拉弓**(瞄准循环) ✓ |
> ⚠ v79.66n (用户裁定): **御币(弹幕)攻击已删除** — 实测弹幕从**塔主人(玩家)位置**生成且 TLM 把施法动画/渲染挂到 thrower 上 ("攻击从我身上发出 + 我的皮肤被当渲染体") ⇒ 御币显式判 NONE ⇒ 塔不开火 ✓ (弹幕专用配置键一并移除) |
`swing:bow` | 3s, 姿势由 **Molang `v.qh`** 条件决定 | ⚠ 手办无该变量 ⇒ **退化成空挥手** ⇒ 默认不用 |

**弓的来源按模型分两类** (用户实测裁定 + 骨骼实证) ⇒ `show_weapon=auto` 自动判别:
| 模型 | `use_mainhand:bow` 骨骼含弓? | 处理 |
|---|---|---|
`wine_fox/05_magical` | ❌ 无(只动手臂) | **画**塔的原版弓 ✓ |
`wine_fox/21_saint`(圣女酒狐) / `22_elf` | ✅ `ysmGlow_MagicBow` | **不画** (模型自带弓, 再画会多一把 ✗) |

**配置** (`defense_tower.toml`): `anim_enabled` · `aim_anim` · `fire_anim` · `aim_anim_danmaku` ·
`fire_anim_danmaku` · `anim_ticks` · `show_weapon`(auto/always/never)。

**与参考模组 (TouhouLittleMaidStatueAnimation) 避让** (用户裁定): 只用自有 `lma_anim*` 键 · **绝不写**它的
`YsmRouletteAnim`/`StatueRoulettePlaying` · 检测到它在播 ⇒ **让位** · 只处理防御塔 BE (TLM 雕像/普通手办不碰) ✓

**更新包必须剥掉弹药**: `getUpdateTag()` 若含 `KEY_AMMO` ⇒ 客户端 BE 整体重载 ⇒ 打开的 GUI 数字**乱跳**
(用户实测 30↔14↔13) ✗ ⇒ 本类已覆写剥掉; 弹药只走菜单槽位同步 (原版语义: 从第一个非空槽一个个消耗, 不搬运) ✓

## 三、已知陷阱 (每条附事故)
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **残留塔会射击后续用例的目标** | gametest 世界共享 + 塔不自灭 ⇒ 后续用例目标被"别人的塔"打死。塔测试必须 **① 各自独立 `batch` ② 开始时按固定清单清塔** (5 个常用塔位, 见 `LmaGameTests.clearTowerTestBlocks`)。|
**B** | **多个被测对象不能共用靶子** | `lmaDefenseTowerDurability` 原设计三塔共用 3 只僵尸 ⇒ **弹幕塔(不耗箭)先把靶子全打死** ⇒ 弓/弩`damage=0` 看似"塔坏了"。⇒ **每个塔配对齐同 z 的专用靶** + 拉开塔位 (z=3/8/13)。|
**C** | **半径 × AABB 全高 = 会打到隔壁测试的怪** | 半径 64 + 全高 AABB 曾把邻近用例的僵尸当成"最近目标" (判决实验: FIRE 时箭全部脱靶)。⇒ 塔测试半径别开满, 目标放**模板内** (16³; x/z >15 无地面会掉落)。|
**D** | **不要用扫描清塔** | `±64` 立方体逐格 `getBlockEntity` ≈ 81 万次/调用 ⇒ 拖垮测试服务器 (比不清还糟)。用**已知坐标清单**。|
**E** | **排查开关** | `[TOWER-DIAG]` 门控诊断: 启动加 `-Dlma.towerDiag=true` **或环境变量 `LMA_TOWER_DIAG=1`** (⚠ `-D` 只作用于 Gradle JVM, 传不进游戏进程 ⇒ 实际用环境变量)。决策点日志会打 扫描/闸门/候选/LOS/开火。|

## 四、v79.66q 动画名护栏与两条实测陷阱 (2026-09-21)

### 动画名护栏 (`DefenseTowerAnimNames`)
* 塔(手办)的**瞄准动画由「有目标」直接触发** —— `DefenseGarageKitBlockEntity`:
  `if (hasTarget) beginAimAnim();` ⇒ **与武器/弹药无关** ✗ (不是"拿弓才瞄准")
* ⇒ 配置里的动画名**不能是施法(ISS)动画**: 历史默认值 `iss:charge_arrow` / `iss:instant_projectile`
  会让手办持续播"施法动作" ✗ (实机实测)
* ⇒ 现统一过 `DefenseTowerAnimNames`: **`iss:` 前缀一律拒绝**, 回退安全默认
  (瞄准 `use_mainhand:bow` · 开火 空=只松手 ✓), 且**每个配置键只警告一次** ✓; 配置写空 = 尊重"显式关闭" ✓

| # | 陷阱 | 规则 |
|---|---|---|
**h** | **`git checkout <file>` 会静默回退该文件的"其他未提交修复"** ✗✗ | 本次实录: 文件被脚本切坏后用 `git checkout` 复原 ⇒ 同文件里**已完成的御币删除改动被一起回退** ✗, 而后续测试只覆盖了另一条断言 ⇒ **bug 悄悄进包** ✓ ⇒ 复原后必须**重跑该文件的全部相关断言**(或 `git diff` 逐条核对) ✓ |
**i** | **"改配置默认值"救不了旧配置文件** ✗ | NightConfig 只在**缺键**时写新默认 ⇒ 用户磁盘上的旧值(`iss:*`)会一直生效 ✗ ⇒ 读值处要**护栏/迁移**(本包 `DefenseTowerAnimNames` ✓), 不能只改默认 |
**j** | **新建 gametest 要先想"它跟谁并行"** ✗ | 同一 `batch` = **并行** ⇒ 新用例若造 mock 玩家/改全局状态, 会干扰同批其它用例(实录: 挤进 `defaultBatch` 时 `lmaBrushNothing` 变红 ✗) ⇒ 给**独立批**(如 `z_gohei`) ✓ |

## 五、⚠ 手办模型被"御币右键"复制 = **TLM 官方联动功能**（不是 bug ✓）

**发现经过** (2026-09-21, 联网查证 + 探针实证): 现象 = "创造模式拿**御币**右键手办 ⇒ 手办变成**玩家**的模型"
(空手/持弓右键**不会** ✗) ⇒ 曾误判为渲染 bug ✗, 实为 **TLM 2.4.0 的官方功能**:

> 更新日志: 「现在**创造模式**拿着**御币**右击**雕像或者手办**，可以把自己的模型**复制**到雕像手办上」

**本仓库侧的实测证据**:
* 探针 `setData` 栈探针**零命中** ✗ ⇒ 不走 setter ✓
* 根因 = TLM `TileEntityGarageKit.getExtraData()` **直接返回可变 CompoundTag** (`:57-59`) ⇒
  TLM 在该功能里**原地改写** `YsmModelId`/`id` ✗ ⇒ 每 tick 影子对比探针成功抓到
  `[TowerProbe2] … 旧: DS鲸鱼娘flash.ysm → 新: …` ✓
* 只读解析存档同样证实: 塔 BE 的 `NeoForgeData.ExtraData.YsmModelId` 被改写为**玩家的模型 id** ✓

**结论 / 纪律**:
1. **LMA 不拦该功能** ✓ (属 TLM 有意行为; 要还原手办模型 = 合成时用的那只女仆的手办 / 用刷怪蛋改样式 ✓)
2. ⚠ **诊断"手办/女仆模型变化"类问题, 先查 TLM 更新日志/wiki**(联网) ✓ ——
   本仓库有 `web_search` 通道, 别只在本地挖 ✗ (本次教训: 本地挖数小时, 联网 5 分钟出答案 ✗✗)
3. 复现/取证手法留档: ① `setData` 栈探针 (抓 setter 路径 ✓) ② **每 tick 影子对比** (抓原地改写 ✓,
   并记录变更时刻 + 附近玩家/手持物 ✓) —— 两者互补, 后者对"可变引用被改"这类问题必需 ✓
