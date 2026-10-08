# storage — 数据表族 (只读静态数据)

**作用**: **静态数据表** (节日/动画等, 启动时从资源加载, 只读)。⚠️ 本层**不再有运行时区域存储** — 跟女仆实体走的数据不能住这层 (硬红线见下), 最后一位住户 `FarmRegionStorage` 已于 v79.63.23 迁往 `task/service/harvest/`。
**依赖方向 (2026-09-17 实测 import)**: 允许依赖 `config/*` + `resource/*` + 根包 (`LittleMaidMoreAction`) + 平台 API (FMLPaths/gson 等); **不得 import `task.*`** (硬红线) · **不得被 `vanilla` 依赖** (A4 曾修: `CropQuery→storage.FarmRegion` 改为 `RegionBox` 纯数据盒)。改本层字段仍**影响存档兼容** (静态表本身无存档, 但被存档引用)。
**硬红线 (ArchGuard)**: **storage 不得 import `task.*`** — 所以"跟女仆实体走的数据"(需 `MaidData`) 不能住这层 ⇒ 见下方迁移记录。

## 一、类明细 (4 类 + 1 条迁移记录)
| 类 | 行数 | 是什么 | 谁在用 |
|---|---|---|---|
`FarmRegionStorage` | ~~253~~ | **已迁出 (v79.63.23)** → `task/service/harvest/FarmRegionStorage` (作物区域改存女仆 NBT `lma_cfg_farm.regions`; 旧版是全局 `farm_regions.json` 按 uuid 索引 ⇒ 删女仆留孤儿/每次编辑全量重写/无维度)。本层禁止 import `task.*`, 故必须迁走 | — |
`FestivalLoader` | 114 | **节日数据加载器** — **纯读 jar 预设** (`assets/littlemaidmoreaction/festival.json` → 建表); v79.64 起**不再复制/读取 `config/*` 副本** (用户裁定: 节日是预设数据; 见陷阱 B) | `FestivalTable` 消费 |
`FestivalTable` | 74 | **节日表** (静态数据; 原在 `task/sense/`, 架构审计后归位到 storage — 同为"静态数据表"族) | `EnvSenseBroadcaster` · `FestivalPassiveTask` · `LmaCommand` · gametest |
`LmaAnimationStorage` | 64 | 动画资源索引 (供动画注册/查询) | `AnimationResourceRegistrar` 等 |
`StartupLoader` | 174 | **启动加载编排** (按序加载各静态表; 平台入口调用) | 平台入口 |

## 二、连接链
```
启动: 平台入口 → StartupLoader → FestivalLoader (读 jar 资源) → FestivalTable (静态表) / LmaAnimationStorage
(区域数据已不在本层: FarmRegionStorage → task/service/harvest/FarmRegionStorage ←→ FarmExecute/FarmRegionContainers / 区域管理屏 / 命令)
```
**本层只有一类数据**: **静态表** (只读, 启动加载, 无存档; 数据与 jar 版本同源)。

## 三、已知陷阱
| # | 陷阱 | 规则 |
|---|---|---|
**A** | **改字段 = 改兼容** | 静态表字段增删必须考虑**老存档/老数据**的兼容 (缺失字段给默认值, 不要直接 NPE/覆盖)。⚠️ 原例 `FarmRegionStorage` 已迁出本层 (`task/service/harvest/`, 其字段兼容规则见该包 README — 区域现存女仆 NBT `lma_cfg_farm.regions`)。|
**B** | **静态表不得被当配置改** | 节日/动画表是**只读数据**; 用户可调参数应放 `config/*`, 不要往这里塞可写状态。**反面实例 (已修)**: 节日预设曾被复制成 `config/littlemaidmoreaction/festival.json` 供用户改, 且复制语义"缺了才复制, 永不更新" ⇒ 老 config 永久冻结 ⇒ 后续新增节日/字段 (`foods`) 老用户永远拿不到 ✗ (实测事故: 节日不送礼)。⇒ 纯预设数据**直接读 jar**, 只读一份与版本同源; 老 config 残留**静默忽略, 不读不动**。仍可给用户改的参数走 Cloth 全局设置/`config/*.toml` (有 schema 与默认值兜底) — **不要用"整份文件副本"当配置面** ✗。|
**C** | **本层不得被 vanilla 依赖** | 曾出 `CropQuery → storage.FarmRegion` 越层 ⇒ 修法 = vanilla 侧建**纯数据盒** `RegionBox`, 由 task 层转换 (ArchGuard 守)。|
**D** | **静态表加载失败要可见** | 资源缺失/格式错时**留 WARN** 并降级 (空表), 不要让整个启动流程崩。|
**E** | **服务器/客户端侧别** | 静态表按需双端加载即可; 运行时可写数据**不属本层** (区域存储已迁 `task/service/harvest/`, 只在服务端读写 — 客户端要数据走包/DataSlot)。|
