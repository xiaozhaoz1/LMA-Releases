# vanilla — 能力层 (io 原语 / 缓存 / 执行器 / 假玩家)

**作用**: **最底层能力**: 读世界 (input) / 写世界 (output) 的**通用原语**, 以及缓存、跨 tick 执行器、假玩家。

> **界面 / 配置键**: **本层没有界面, 也没有配置键** (纯能力层, 不注册 Menu/Screen, 不读 `config/*`)。
> 需要配置的能力, 一律由**调用方把值作为参数传进来** (本层不认识配置系统)。

**依赖方向 (ArchGuard 零容忍)**: 仅 `原版 MC + TLM + api.VanillaConstants`; **不得** import task/compat/storage/event/init/… 
(本会话修掉的 3 处越层: `CropQuery→storage.FarmRegion` · `DebugSelectionCoordinator→event.StickBindUtil` · `MaidAttrRegistry→init.TlmVersion`)。

## 一、结构 (逐子包: 读/写什么 + 被谁调用)
| 子包 | 类数 | 读/写什么 | 被谁调用 |
|---|---|---|---|
`input/world/` | 5 | 世界/结构读取: `WorldStateReader` `StructBox` **`RegionBox`** (纯数据盒, 项目内 import = 0) `HeatSourceQuery` `WaterQuery` | task/service · pipeline |
`input/item/` | 4 | 物品/背包读取 (`ItemStackHelper` 等) | 各层 |
`input/container/` | 3 | 容器/箱子内容读取 | task/service |
`input/maid/` | 4 | 女仆状态读取 (属性/背包/任务) | 各层 |
`input/search/` | 8 | **搜索原语**: `CropQuery` `ScanFilters` `ScanScheduler` 等 (带缓存/节流) | pipeline · service |
`input/recipe/` | 3 | 配方查询 | CraftChain / Furnace 相关 |
`output/item/` | 2 | 给女仆/世界物品 (掉落捕获/存入) | pipeline |
`output/container/` | 2 | 容器写入 (取出/存入) | ArmTransfer 等 |
`output/maid/` | 3 | 女仆写操作 (`ThrottleUtil` 节流判定 · 状态写入) | 各层 |
`output/combat/` | 1 | 战斗原语 (伤害/效果) | 防御塔 · 哈气 |
`output/movement/` | 1 | 移动原语 | 移动类任务 |
`cache/` | 3 | **区块缓存族** (带清理契约) | 搜索/执行器 |
`execute/` | 3 | **跨 tick 协调器** | pipeline |
`execute/scan/` | 5 | 扫描调度族 | pipeline |
`fakeplayer/` | 4 | 假玩家 (模拟玩家操作) | 任务/测试 |
| 顶层 | 2 | `MaidAttrRegistry` (属性注册) · `VanillaCompat` (平台差异封装) | 各层 |

**io 判据 (与 `task/service` 的分界)**: **跨 tick?** → `execute`(协调器); **有业务语义?** → `task/service`; 否则是 io 原语 (读=`input`, 写=`output`)。
⚠ **"碰不碰世界"不是判据** —— 判据是**通用性**: 能无业务语义地被多处复用的读写 = 原语 (粒度不重要)。

## 二、连接链
```
task/pipeline (相位机 / 节拍)
   ├─→ input/*   读: 找目标 · 读容器 · 读世界状态
   ├─→ output/*  写: 存物 · 开火 · 移动 · 节流判定
   ├─→ cache/*   区块缓存 (带清理)
   └─→ execute/* 跨 tick 协调 (如农业/动画执行器)
调用方负责: 门控 / 节拍 / 业务判定; 本层只负责"一次读写 / 一次协调", 不自创业务语义。
```

## 三、已知陷阱 (改前必读)
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **越层红线** | 本层不得 import 上层 (ArchGuard **零容忍**, 新增越层即红)。要共享常量 → 加到 `api.VanillaConstants`。|
**B** | **缓存必须可清** | per-maid 键 = **UUID** (实体 id 会复用!); per-区块 = 维度级 `clearDimension`; 新增 static Map 必须挂清理 (参 `task/data/README.md` 缓存纪律 · `MaidUnloadRegistry`)。|
**C** | **原语不做节流决策** | 节流工具 (`ThrottleUtil`) 只提供**判定**, 何时调用由上层按节拍决定。|
**D** | **搜索原语注意性能** | 大范围立方体逐格扫描会拖垮服务器 (防御塔清塔实测: ±64 ≈ **81 万次** `getBlockEntity`/调用) ⇒ 优先有界/缓存/节流, 未加载区块先跳过。|
**E** | **平台差异封在顶层** | 双平台 API 差异用 Stonecutter 分支或 `VanillaCompat` 封装, 不要散落到各原语。|
**F** | **下游只要"形状"就给形状** | 上层 (task) 需要盒/坐标等**数据**时, 本层给**纯数据 record** (如 `RegionBox`), 不要把上层的语义类型 (如 `FarmRegion`) 拖进来 —— 这正是 A4 越层的修法。|

---

## 四、子包逐类明细 (cache / execute / execute/scan / fakeplayer)

> **维护约定**: 这些子包**无独立 README**, 明细集中在本节 (守护以 `"R"` 递归模式校验本 README 覆盖其全部类)。

### `cache/` (3) — 区块缓存族 (带清理契约)
| 类 | 行数 | 缓存什么 | 清理方式 |
|---|---|---|---|
`EntityScanCache` | 255 | 实体扫描结果 (per-maid `queryCache`) | UUID 键 + `MaidUnloadRegistry` (⚠ 历史三处实体 id 例外之一) |
`BlockPatternCache` | 182 | 方块模式 (多方块结构匹配) | 区块卸载/维度清理 |
`StructureScanCache` | 145 | 结构扫描结果 | 同上 |

### `execute/` (3) — 跨 tick 协调器
| 类 | 行数 | 干什么 | 调用方 |
|---|---|---|---|
`CropRegistry` | 365 | **作物注册表** (BlockState → 作物 meta: 成熟/可种/产物) | 农田链 |
`DebugSelectionCoordinator` | 312 | 选区调试工具 (客户端): 木棍选两点 → 渲染盒 + HUD | 平台客户端入口 (**木棒判定走注入**, 见下陷阱 F) |
`BlockUpCoordinator` | 71 | 上行方块协调 (逐层向上处理) | 挖掘类 |

### `execute/scan/` (5) — 扫描调度族
| 类 | 行数 | 干什么 |
|---|---|---|
`ScanJob` | 193 | 一个扫描任务 (分帧执行, 防卡顿) |
`ScanBudget` | 70 | **每 tick 扫描预算** (纯函数, JVM 可测) |
`ScanScheduler` / `ScanSchedulerCore` | 51 / 51 | 调度器 (分帧/优先级) |
`Tickable` | 13 | 可 tick 项接口 |

### `fakeplayer/` (4) — 假玩家 (模拟玩家操作)
| 类 | 行数 | 干什么 |
|---|---|---|
`LmaPlayerSimulator` | 428 | **模拟器核心**: 以假玩家身份执行放置/使用/攻击 (最大) |
| `LmaFakePlayer` | 130 | 假玩家实体 (Factory 创建的 player 替身) |
| `FakePlayerManager` | 106 | 假玩家池管理 (⚠ 历史三处实体 id 例外之一: `TASKS` 键) |
| `FakePlayerInteract` | 65 | 交互原语 (右键/左键封装) |

**⚠ 与本层其它部分的关系**: `execute*` 是**跨 tick 协调** (判据见 §一), `cache/*` 带清理契约, `fakeplayer/*` 是唯一"造实体"的子包 —— 三者都**不是**纯 io 原语, 改动前先确认清理与幂等。
