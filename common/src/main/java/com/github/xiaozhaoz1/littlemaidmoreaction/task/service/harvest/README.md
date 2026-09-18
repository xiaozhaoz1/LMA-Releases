# task/service/harvest — 采集/导航/自救/空置域/农场域服务 (V5 迁入 + 农场域)

**作用**: 五个**域协调器族**——跨 tick 状态机 + 域算法 (+ 农场域的区域数据), 由对应管线驱动。**本包 = task/service 子包** (V5, 2026-09-11 自 `vanilla/execute` 整簇迁入)。

**为什么在这层**: 这些类**带任务语义** (读 `TaskRegistry` / `FlowTaskData` / `TaskKeys`, 演化自任务执行链) — 按两轴表「跨 tick → 协调器」+「有业务语义 → service」, 正确归属是 `task/service` (而非 `vanilla/execute` 的"零任务语义"面)。详见 `docs/plans/vanilla-layering-worklist-2026-09-11.md` V5。

**依赖方向**: task/* + api/* (PathingApi/TaskResult/NavigationUtil/SenseApi) + vanilla/* (io 原语 + BlockUpCoordinator) — **全部为允许方向** (task → vanilla/api 正向)。
**被**: `task/pipeline/*` (ChainHarvestPipeline/VoidExcavationPipeline/DamFillPipeline/SelfRescuePipeline/SelfRescueTrigger) · `task/runtime/*` (MaidUnloadRegistry/GameTickPipelineManager/EntityCleanupListener) · `adapter/TlmEventAdapter` · `api/pathing/PathingApi` · `event/MaidDamageListener` · gametest 调用。

## 四域分组 (13 类 — v79.63 增 VoidExcavationPool)

> ⚠ 本包现含 **5 族** (13 + 农场域 3 = 16 类, v79.64 起): 下表 4 族为 V5 迁入的"协调器族";
> **第 5 族 = 农场域** ({@code FarmExecute} / {@code FarmItems} / {@code FarmRegionContainers} + v79.64 新增
> {@code FarmRegionStorage} / {@code FarmRegionLegacyImport}) 为 V6 迁入与后续新增, 详见下方「农场域」小节。

| 域 | 类 | 职责 |
|---|---|---|
| **采集** | `ChainHarvestExecute` (520) · `ChainScan` (225) · `ChainHarvestMath` (48) · `MaidChainState` (89) | 连块脉 BFS + 蓄力整脉破坏 (SCAN/CHARGE 相位) + 纯函数 (Math) + per-maid 状态 |

> **v79.64 采集扫描重写**（用户 2026-09-16 裁定 "挖矿不该用区块扫描" ✓）：
> `ChainScan` 三层球扫描 ⇒ 走 **`vanilla/input/search/NearestBlockSearch`**（球壳**精确 d² 由近到远** + **命中即停** ✓）
> · 近扫 **4 格球 / 5t**（= 她站着就能直接挖的 ✓ 无裸露过滤 ✓ v79.63 裁定 ✓）
> · 中扫 **10 格球 / 100t** · 远扫 **16 格球 / 250t**（`MaidChainState.lastMidScan/lastFarScan` ✓ per-maid ✓）
> · `searchRadius` 32 → **16** ✓；旧的 `nearPass`(±4 盒子 ✗) 与 `findNearestValid`/`BlockScanner`(区块环+名额截断 ✗) **已不被采矿调用**（`BlockScanner` 保留给挖空/农场 ✓）
> · **陷阱 #1（实测踩过 ✗）**：三层节流后 `navigate` 若不再被周期调用 ⇒ **`WALK_TARGET` 无人续期 ⇒ 女仆一步不动** ✗（日志 `navigate=WALKING` + 零位移 ✓）
>   ⇒ 修法 = `MaidChainState.currentTarget` + 节流期间**每 tick 对旧目标重发 `navigate`**（幂等 ✓）—— **任何"降频"改动都要问"谁的续期动作被我省掉了"** ✗
> · **陷阱 #2（旧实现 ✗）**：`BlockScanner` 的"**收满 maxHits 立即 return → 之后才排序/过滤**"⇒ 结果**不是最近的矿** ✗（本区块埋矿吃光名额 ⇒ 邻居区块/新放的近矿排不进候选 ✗）= 用户最初"区块不同不挖"的真机制 ✓
> · 诊断（排查期保留 ✓ `[ORE-EXPOSE]`/`[ORE-FILTER]` 已降 debug ✓）：`[ORE-TIER]` `[ORE-SCAN]` `[ORE-FILTER]` `[ORE-GATE]`(10 出口) `[ORE-TOOL]`
> · 验证：gametest **110/111** ✓（1 条为既有 farm flake ✓）· 单测 **539/0** ✓ · `ShellOffsetsTest` 4/4 ✓
> · 详见 `docs/MINING-NEAREST-SEARCH-PLAN.md` §8 与 `docs/lessons-learned.md` #334 ✓
| **导航安全** | `DangerGuardCoordinator` (111) · `DigThroughCoordinator` (85) | 危险堵护状态机 · 头顶挖穿 (digUp 深度 6) |
| **自救** | `SelfRescueCoordinator` (70) · `SelfRescueState` · `MlgRescueCoordinator` | 卡方块自救 · 自救上下文 · MLG 摔落自救 (五射线 + 水桶/软方块双通道) |
| **空置域** | `VoidExcavationService` (227) · `VoidExcavationChunkManager` · `VoidExcavationContainerService` · **`VoidExcavationPool`** (225, v79.63 自 `VoidExcavationPipeline` 抽出) | 区块工作制推进 · setChunkForced 管理 · 输入/输出箱与扩展搜索 · **认领池/扫描共享缓存/超时自愈** (多女仆共享运行时状态; 锁封闭池内, 启动 `resetPool`, 卸载 `onMaidUnload`) |

### ⚠ 空置域「传送前第一层检查」(v79.65.1, 用户实测)

`VoidExcavationService.teleportToVoid` **允许站空气** (挖空后落下层), 但**没有"落点净空"保证** ⇒ 调用方
(`VoidExcavationPipeline`) 必须**传送前**用 `ensureLandingClear` 把落点挖成 **2 格净空**
(**头位 `y+1` → 本体 `y`**, 用户裁定顺序) ✓。理由:
女仆站姿 = 脚在落点格 + 头在其上 1 格; 逐步下挖时**后续层头位**已在上一层挖空 ⇒ 安全,
但**第一层 (标记层) 的头位是未开挖地表** ⇒ 直接传送 = **头埋实心方块 ⇒ 窒息持续掉血** ✗ ⇒ 两条传送路径
(关导航的区块中间 / 导航超时的 `target.above()`) 都先清落点再传 ✓。回归: gametest `lmaVoidFirstLayerLandingClear`。

### ⚠ 空置域 `min_y` 必须 **低于** 标记层 (v79.66.1, 用户实测「一直提示未设起始点」根因)

`while (y > minY)` 决定挖掘循环是否进入 ⇒ 若 `min_y >= start.y` (无可挖层), 循环**一次不进**, 而循环后的
`if (y <= minY)` **恒真** ⇒ 首 tick 就把区块标 `poolMark(…, 2)`(已挖完) ⇒ 下 tick 认领池判"区域完成"
⇒ **删 `start`** + cancel ⇒ 之后每次启动 `validate` 都失败 (表现为"未设起始点"反复出现) ✗✗
**两道防线** (v79.66.1): ① **管线自愈** — 检测到无效 `min_y` 即移除该键 + WARN, 回落"自动检测基岩层" (保留 `start` ✓);
② **屏端拒存** — `VoidExcavationConfigScreen` 保存时若 `min_y >= start.y` 直接拒绝 + 提示
(`screen.littlemaidmoreaction.void.min_y.invalid`) ✓。回归: gametest `lmaVoidMinYInvalidSelfHeal`。

## 农场域 (v79.64 起, 作物区域存储归位)

| 类 | 职责 |
|---|---|
`FarmRegionStorage` | **作物区域存储 (v79.64 改存女仆 NBT)** — 落在该女仆 `lma_cfg_farm.regions` (ListTag, 走 `MaidData.cfgOrCreate`); `FarmRegion` record 带 **dimension** ⇒ 女仆换维度后该区域**暂停** (`isActiveIn`); 含 `findMaid(level, uuid)` (网络包按 uuid 解析女仆; `level.getEntity(UUID)` 在 1.20.1 不存在 ⇒ 遍历 `getAllEntities`) |
`FarmRegionLegacyImport` | **老 `config/farm_regions.json` 一次性导入** (惰性 + 幂等 + 全 uuid 命中才改名 `.imported`); 临时代码 — 导完可删; `parseLegacy` 纯函数可单测 |

**为什么从这里而不是 `storage`**: `ArchGuard` 硬红线 = **storage 不得 import `task.*`**; 而区域数据要经 `MaidData` 写女仆 NBT ⇒ 只能住 task 侧。
对齐 TLM 的做法 (任务信息挂实体: `EntityMaid.getData(TaskDataKey)` → `MaidTaskDataMaps` 写女仆自己的 NBT) ⇒ 数据随实体消失、无孤儿条目、无"每次编辑全量重写整份文件"。

## 修改注意

1. **包内互引为裸名** (同包) — 搬迁时新增的跨包引用: `BlockUpCoordinator` (vanilla/execute, 其 `placeMaterial` 已由 package-private 改 **public** 以支持跨包调用)
2. **状态管理**: per-maid 状态走 `MaidChainState` / PD, 静态表**必须登记 `MaidUnloadRegistry`** (错题 #272: 键用 UUID)
3. **纯逻辑抽纯类**: `ChainHarvestMath` / `MlgJudge` 类判定 — 纯 JVM 可测 (错题 #174)
4. **失败出口一律 CONTINUE** (禁递归 idleScan — 错题 #264): 卡点万能根因
5. **不要在此层加新 IO 原语** — 读/写世界用 `vanilla/{input,output}`, 协调器只编排
6. 搬迁分两批: V5 本簇 12 类 · **V6 再迁 `AnimExecute`/`FarmExecute`** (→ `task/service/`, 反向依赖归零) · `CropRegistry`/`CropQuery` **留 vanilla** (被 `vanilla/cache`+`input` 同层引用, 迁走会新增越层) · `BlockUpCoordinator` 留 `vanilla/execute` (纯放置链; 其 `placeMaterial` 已改 public 供本簇跨包调用) · `AutoCropHandler` 已删 (死代码)

## 测试

`common/src/test/.../task/service/harvest/`: `ChainHarvestMathTest` · `MaidChainStateTest` · `MlgJudgeTest` · `VoidExcavationChunkManagerTest` (V5 随迁, 同包测 package-private 方法) · **`FarmRegionStoragePureTest`** (v79.64: 几何/归一化/维度判定/NBT 编解码/老文件解析; 实体读写路径走 gametest)。
域行为链路 (挖掘/堵护/自救/空置域) 走 **gametest** (`lmaChainOre*` / `lmaWoodCollect*` / `lmaVoid*` / `lmaMlgRescue` / `lmaSelfRescueBuried`)。
