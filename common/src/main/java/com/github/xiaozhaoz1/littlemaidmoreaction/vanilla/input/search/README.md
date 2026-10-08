# vanilla/input/search — 方块扫描器（BlockScanner / 环形螺旋 / 位置过滤）

> 铁律 #0 补齐（2026-09-16）：本包原无 README。**改这里之前必须先读下面的"已知陷阱"**。

## 职责

| 文件 | 职责 |
|---|---|
`BlockScanner` | **同步**方块扫描：环形 chunk 螺旋（近优先）+ section 调色板短路 + 距离过滤；返回 `Match` 列表（按精确距离排序后截断 `maxHits`）|
`RingSpiral` | 环 r 的周长与第 idx 格偏移（正方形螺旋，`perimeter(r)=1/8r`）|
`ConnectedBlockSearch` | 开脉用连通扩张（与扫描无关）|

## 调用链（谁用它）

```
ChainScan.findNearestValid  → SenseApi.findNearestBlock → BlockScanner.scan → scanChunk（逐 section/逐方块）
ChainScan.nearPass          → 自己手写 ±4 三层循环（不走本包）
挖空/农场/其它              → 各自走 SenseApi 或自己的扫描
```

## 关键语义

- `scan(level, center, maxRing, vRange, filter, maxHits, [posFilter])`
  - `maxRing` **是 chunk 环半径**（不是格数）：`maxRing=2` ⇒ 5×5 区块；`maxDistSqr=(maxRing*16)²`
  - **`level.getChunkSource().getChunkNow(cx,cz)` 只读** — 未加载区块**静默跳过**，**绝不强制加载**
  - section `maybeHas(palette)` 短路：该 16³ 小节调色板里没有目标方块 ⇒ 整节跳过（稀疏目标 50~200× 提速）
  - 收集顺序：**ring 由近到远**；**同 ring 内按 chunk/section 顺序** ⇒ **不是精确距离序**
  - 收满 `maxHits` **立即 return**（`results.size() >= maxHits` + `MAX_COLLECT` 双重判断）
  - 收完之后 `results.sort(by distSqr)` ⇒ 返回**精确距离最近**的 `maxHits` 个

## ⚠ 已知陷阱（踩过，改前必看）

| # | 陷阱 | 后果 | 对策 |
|---|---|---|---|
**1** | **`filter`（`Predicate<BlockState>`）只拿到方块状态，拿不到位置** ⇒ 位置类判定（裸露 `hasOpenFace`、可达、跳过点）只能由调用方在 `posFilter` 里做 | 见 #2 | 需要位置判定的过滤**必须**走 `posFilter` |
**2** | **截断发生在过滤之前**（原实现）：先按距离收满 `maxHits`，**之后**才在 `SenseApi` 里跑 `skip`/`posFilter` | **名额被"埋死的矿"吃光** ⇒ 本区块埋矿多的场景里，**邻居区块**或**新放的矿**永远排不进候选 ⇒ 表现为"**不扫它/不挖它**"（2026-09-16 用户实机：4~6 格外新放的铜矿不挖 ✓ 14804 条 `ORE-EXPOSE` 全是被拒的埋矿）| **位置过滤必须下推到收集阶段**（`scanChunk` 里 `results.add` 之前）⇒ 名额只被"**可挖的**"占用；否则只能靠调大 `maxHits` 缓解（治标 ✗）|
**3** | 调用方若把 `maxHits` 传得很小 | 见 #2，问题被放大 | 挖矿侧 `ChainScan` 用 `Math.max(256, scanBudget(radius))` 兜（下推落地后回落 16）|
**4** | 环序 ≠ 距离序 | 单看"先扫到的"会误判优先级 | 最终排序按 `distSqr` ✓（但**截断**仍受环序影响，见 #2）|
**5** | `getBlockState` 对未加载区块返回空 ⇒ 与"真空气"无法区分 | 裸露判定可能把未加载邻居当空气 ✓（v79.63.31 起有意为之：**放行**区块边界的矿）| 需要"精确"时先查 `level.hasChunk` |
**6** | 扫描是**无状态**的（每次 `new ArrayList<>()`，无缓存） | "刷新频率"不是病根 ✗（2026-09-16 结论）；改刷新间隔**救不了**丢失的目标 | 病根看 #2 |

## v79.64 新增：最近优先搜索（采矿专用 ✓）

| 类 | 职责 |
|---|---|
`NearestBlockSearch` | **球内由近到远 + 命中即停** ✓ ⇒ 返回**最近的合格方块** ✓（三层半径常量 `RADIUS_NEAR=4` / `RADIUS_MID=10` / `RADIUS_FAR=16` ✓ 单轮预算 `DEFAULT_BUDGET_CELLS=16384` ✓）|
`ShellOffsets` | **精确距离平方 d² 分桶**的偏移表（纯逻辑 ✓ 可纯 JVM 测 ✓）—— "球" = **只迭代桶号 `k ≤ r²`** ✓（不是盒子 ✗）；按 `(半径,半高)` 缓存 ✓ 表不可变 ⇒ 线程安全 ✓ |

**背景（用户 2026-09-16 实机复现并裁定 ✓）**：旧 `BlockScanner` 的"区块环序 + 收满 maxHits 立即 return → 之后才排序"⇒
**结果不是最近的方块** ✗ ⇒ ① 邻居区块永远轮不到 ② 新放的近矿排不进候选 ③ 埋死矿吃光名额（实测一轮 14804 次 `ORE-EXPOSE` 全是被拒埋矿）✗
⇒ 采矿业改用 `NearestBlockSearch` ✓；`BlockScanner` / `SenseApi.findNearestBlock` **保留**给挖空/农场等调用方 ✓。

**分工**：近扫 4 格球每 5t（"站着就能直接挖的" ✓）· 中扫 10 格球每 100t · 远扫 16 格球每 250t（节流在 `ChainScan` 侧 ✓）。

## 修改自检

1. 你改的是 `scan` 的**收集**还是**返回**？（收集阶段改动会影响 `maxHits` 语义 ⇒ 回顾陷阱 #2）
2. 新加的位置类判定，是走 `posFilter` 还是被塞进 `filter`？（后者做不到 ⇒ 见 #1）
3. 跑 `:forge:1.20.1:runGameTestServer`（挖矿家族 `z_ore` ✓）+ 单测（`ChainHarvestMathTest` 管 `scanBudget` 语义 ✓）
