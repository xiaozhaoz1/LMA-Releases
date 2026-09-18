# vanilla/input — io 原语 · 读侧 (29 类)

> **本子包整体属于 io 原语层** (与 [output](../output/README.md) 同层, 分野 = 读侧/写侧)。
> io 身份判据 (两轴表): **非跨 tick + 无业务语义**; **io 判据是通用性不是粒度** —
> 复合工具 (HandSwap) 仍是 io 原语, 不因"一步做了几件事"或"它写"而否定 io 身份。
> ✅ **V1–V3 归位已完成** (2026-09-11): 跨 tick 5 类 → `execute/scan/` · 写侧 4 类 → `output/` · `NearbyContainerScanner` 读写拆分 (写半边 → `output/container/ContainerExtractor`)。
> ✅ V4: 区块缓存 2 类 → `cache/` (现 `vanilla/cache/` 共 3 类: BlockPatternCache/EntityScanCache/StructureScanCache)。✅ **缓存契约已裁定 (v79.67, 错题 #274): 缓存属 io 读侧的合法形态** — 允许 "TTL / 弱引用自清" 这类**加速状态** (判据仍是两轴: 非跨 tick 业务语义 + 无业务判定); `RecipeIndex` (资源级缓存, WeakHashMap 自清) **留本包合法** ✓。**新代码只放"非跨 tick + 通用 + 读侧"。**
> **计数口径**: 本包 **29 类** (逐类 `find` 实测, 2026-09-17) —— **改本包必须同步此表**。

**作用**: 与世界的单次**读取** — 背包/女仆状态/方块/配方/扫描/节流。通用、无业务语义。
**判据 (两轴表)**: 轴 1「跨 tick 吗」否 + 轴 2「有业务语义吗」否 → io; 然后按读/写归本包或 output。带业务判定 → `task/service`; 跨 tick → `execute`。
**依赖方向**: 只依赖 MC + TLM + `api.VanillaConstants`; 被 service/execute/pipeline 依赖。

## 一、逐类明细 (行数 / 读什么 / 典型调用方)

### `world/` (5)
| 类 | 行数 | 读什么 | 调用方 |
|---|---|---|---|
`WorldStateReader` | 63 | 世界状态聚合 (时间/天气/维度) | service · pipeline |
`StructBox` | 24 | 结构边界盒 (纯数据 record) | 结构类 service |
**`RegionBox`** | 60 | **区域盒纯数据** (`contains`/`containsYExtended`(Y±1)/`centerX|Y|Z`/`maxDimension`/`horizontalArea`/`volume`); **项目内 import = 0** | FarmExecute (A4 修: 断 `CropQuery→storage.FarmRegion`) |
`HeatSourceQuery` | 94 | 热源查找 (熔炉/岩浆/火) | 温度相关任务 |
`WaterQuery` | 37 | 水源判定 | 农业/填坝 |

### `maid/` (4)
| 类 | 行数 | 读什么 | 调用方 |
|---|---|---|---|
`MaidStateReader` | 393 | **女仆状态领域门面** (血/饱食/心情/工作状态/手持) | 各层 (最大读侧类) |
`MaidInventorySpace` | 54 | 背包剩余空间 | service |
`MaidInventoryReader` | 35 | 背包遍历读取 | service |
`ThrottleMath` | 26 | **节流纯函数** (`isCoolingDown`/`cooldownRemaining`) — JVM 可测 | `ThrottleUtil` (output) |

### `item/` (4)
| 类 | 行数 | 读什么 | 调用方 |
|---|---|---|---|
`ToolJudge` | 158 | 工具判定 (斧/镐/铲/刷…) | 采集类服务 |
`ToolStateReader` | 79 | 工具状态 (耐久/附魔) | 采集/刷扫 |
`ItemSelect` | 66 | 从背包挑物品 (策略) | service |
`ItemStackHelper` | 24 | ItemStack 小工具 | 各层 |

### `container/` (3)
| 类 | 行数 | 读什么 | 调用方 |
|---|---|---|---|
`NearbyContainerScanner` | 116 | 附近容器扫描 (纯读; 写半边已拆到 `output/container/ContainerExtractor`) | service |
`WirelessChestSpace` | 86 | 隙间空间 | service |
`WirelessChestReader` | 49 | 隙间内容 | service |

### `search/` (10)
| 类 | 行数 | 读什么 | 调用方 |
|---|---|---|---|
`BlockScanner` | 124 | 方块扫描基础 | pipeline |
`BlockSearch` | 63 | 方块搜索 (谓词/策略) | pipeline |
`ConnectedBlockSearch` | 109 | 连通块 (矿脉/树) | 连锁采集 |
`EntityScanner` | 115 | 实体扫描 (敌对/掉落物) | 塔/哈气 |
`CropQuery` | 120 | 作物查询 (`scanMature`/`scanPlantable`; 用 `RegionBox`) | FarmExecute |
`ScanFilters` | 27 | 扫描过滤谓词常量 | 各搜索类 |
`LightQuery` | 24 | 光照查询 | 火把相关 |
`RingSpiral` | 31 | 环形螺旋遍历序 (由近到远) | 搜索/塔 |
`NearestBlockSearch` | 145 | **球内由近到远 + 命中即停** ⇒ 返回**最近的合格方块** ✓（v79.64 采矿专用；半径常量 4/10/16 ✓ 预算 16384 ✓） | ChainScan（采矿）|
`ShellOffsets` | 112 | **精确距离平方 d² 分桶**偏移表 (纯逻辑 ✓ 可纯 JVM 测 ✓)；"球" = 只迭代桶号 `k ≤ r²` ✓ | NearestBlockSearch |
> **v79.64 背景**（用户 2026-09-16 裁定"挖矿不该用区块扫描" ✓）：旧 `BlockScanner` 顺序是"区块环 → 环内周长 → 区块内 x/z ⇒ **收满名额立即 return** ⇒ 之后才按距离排序" ✗
> ⇒ 结果**不是最近的方块** ✗ ⇒ ① 邻居区块轮不到 ② 新放的近矿排不进候选 ③ 埋死矿吃光名额（实测一轮 14804 次 `ORE_EXPOSE` 全是被拒埋矿）⇒ 采矿业改走 `NearestBlockSearch` ✓
> 陷阱：位置类判定必须走 `posFilter`（在**命中判定前**生效 ✓ 不占预算 ✓）；`filter` 只拿得到 `BlockState` ⇒ 拿不到坐标 ✗（详见 `search/README.md` 陷阱表 ✓）

### `recipe/` (3)
| 类 | 行数 | 读什么 | 调用方 |
|---|---|---|---|
`RecipeTreeResolver` | 316 | 配方树解析 (合成链核心) | CraftChain |
`RecipeIndex` | 146 | 配方索引 (资源级缓存, WeakHashMap 自清 — **已裁定: 缓存属 io 读侧合法形态**, #274) | CraftChain |
`RecipeChain` | 47 | 配方链描述 | CraftChain |

## 二、修改注意
1. **纯逻辑抽纯类** (`ThrottleMath`/`RingSpiral`/`StructBox`/`RegionBox`) — 纯 JVM 可测, 改动必须配单测。
2. **门面变胖是漂移信号** (`MaidStateReader` 393 行 = 领域门面, 类头要标清)。
3. **读侧不应有副作用** — 历史上 4 个写侧类已迁 output, **别照抄** (新代码写操作去 output)。
4. **io 判据是通用性不是粒度** — 复合 io 原语合法, 但注意它属于哪一侧 (读/写)。
5. **下游只要"形状"就给形状** — 上层要盒/坐标等数据时给**纯数据 record** (`RegionBox` 先例), 不要引入上层语义类型 (A4 越层教训)。
6. **改本包必须同步本表** (计数 + 逐类明细), 否则文档立刻漂移 (本会话已见: `RegionBox` 加进来后计数未更新)。
