package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * **最近优先方块搜索（球形距离序 + 命中即停）** — v79.64 采矿新扫描的核心。
 *
 * <h2>为什么替换 BlockScanner</h2>
 * 旧扫描（{@link BlockScanner}）顺序是"区块环 → 环内周长 → 区块内 x/z ⇒ **收满名额立即 return** ⇒ 之后才按距离排序"
 * ⇒ **结果不是最近的方块** ✗：① 邻居区块永远轮不到（her 自己区块的矿就填满名额）② 新放置的近处矿排不进候选
 * ③ 埋死的矿吃光名额。2026-09-16 用户实机复现（4~6 格外新放的矿不挖 ✓）并裁定改为本实现 ✓。
 *
 * <h2>语义（三层，用户四裁定稿）</h2>
 * <ul>
 *   <li>近扫 {@link #RADIUS_NEAR} = <b>4 格球</b>（"站着就能直接挖的" ✓ 与 4 格破块球对应 ✓）· 每 5t</li>
 *   <li>中扫 {@link #RADIUS_MID} = 10 格球 · 每 100t（5s）</li>
 *   <li>远扫 {@link #RADIUS_FAR} = 16 格球 · 每 250t（12.5s）</li>
 * </ul>
 * <b>球</b>（不是盒子 ✗）：以 {@code dx²+dy²+dz² ≤ radius²} 判定 ✓ ⇒ 垂直范围由半径自然确定
 * （4 格高以外归中扫 ✓）。
 *
 * <h2>实现</h2>
 * 用 {@link ShellOffsets} 的**精确 d² 分桶**：桶号 {@code k = 0,1,…,radius²} 递增 = 由近到远 ✓；
 * 每格依次过 {@code hasChunk（只读, 不强制加载 ✓）→ filter → posFilter → skip}，**第一个通过者立即返回** ✓
 * ⇒ 返回值必为**该球内最近的合格方块** ✓✓
 *
 * <p>性能：单轮预算 {@link #DEFAULT_BUDGET_CELLS}（16384 ✓ ≈ 16 格球整球规模 ✓）用尽即返回 null；
 * 典型命中在 ≤8 格 ⇒ 只查约两千格（近扫更少 ✓）。
 */
public final class NearestBlockSearch {

    /** 近扫球半径 — 站着就能直接挖（= MINE_DIG_DIST_SQR 的 4 格 ✓） */
    public static final int RADIUS_NEAR = 4;
    /** 中扫球半径 — 要走一小段 */
    public static final int RADIUS_MID = 10;
    /** 远扫球半径 — 总范围上限（原 32 → 16 ✓ 用户裁定） */
    public static final int RADIUS_FAR = 16;

    /**
     * 单轮访问格数上限（安全阀 ✓）。
     * <p>v79.64 实测校正（用户裁定 ✓）：16 格球实有 **17,077** 格 ⇒ 原 16384 **会掐断** ✗
     * （日志：{@code [ORE-SCAN] 空结果 r=16 访问=16385 … 预算用尽=true}）⇒ 提到 <b>17,500</b>（整球 + 余量 ✓）。
     */
    public static final int DEFAULT_BUDGET_CELLS = 17500;

    private NearestBlockSearch() {}

    /**
     * 找最近合格方块（球内 ✓ 由近到远 ✓ 命中即停 ✓）。
     *
     * @param radius     球半径（用 {@link #RADIUS_NEAR}/{@link #RADIUS_MID}/{@link #RADIUS_FAR}）
     * @param filter     方块态级过滤（matches ∧ allowed ∧ canHarvest — 便宜 ✓ 先跑）
     * @param posFilter  位置级过滤（裸露 hasOpenFace 等 — 在**命中判定前**生效 ✓ 不占预算 ✓）
     * @param skip       跳过集（命中集合 asLong ✓）
     * @param budgetCells 单轮访问上限（≤0 视为 {@link #DEFAULT_BUDGET_CELLS}）
     * @return 最近的合格方块；无则 null
     */
    @Nullable
    public static BlockPos find(ServerLevel level, BlockPos center, int radius,
                                Predicate<BlockState> filter,
                                @Nullable BiPredicate<BlockPos, BlockState> posFilter,
                                @Nullable Set<Long> skip,
                                int budgetCells) {
        if (radius <= 0) return null;
        int budget = budgetCells > 0 ? budgetCells : DEFAULT_BUDGET_CELLS;
        ShellOffsets off = ShellOffsets.of(radius, radius);   // 球 ⇒ 半高 = 半径 ✓（垂直界限由 d² 决定 ✓）
        int limit = radius * radius;                          // 只迭代 k ≤ r² ⇒ 球 ✓ 不是盒子 ✓
        int visited = 0;
        int matched = 0;        // filter 通过数 (诊断 ✓)
        int posRejected = 0;    // posFilter 拒绝数 (诊断 ✓)
        boolean budgetHit = false;
        for (int k = 0; k <= limit; k++) {
            int[] arr = off.bucket(k);
            if (arr == null) continue;                        // 该距离无格 ⇒ 跳过 ✓
            for (int i = 0; i < arr.length; i += 3) {
                if (++visited > budget) { budgetHit = true; k = limit + 1; break; }   // 预算用尽 ✓
                BlockPos p = center.offset(arr[i], arr[i + 1], arr[i + 2]);
                // 未加载区块跳过 — 只读判断, 绝不强制加载 ✓
                if (!level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
                BlockState st = level.getBlockState(p);
                if (!filter.test(st)) continue;
                matched++;
                if (posFilter != null && !posFilter.test(p, st)) {
                    posRejected++;
                    continue;
                }
                if (skip != null && skip.contains(p.asLong())) continue;
                return p;                                     // ★ 命中即停 = 最近的合格矿 ✓✓
            }
        }
        // ★ v79.64 诊断 (用户要求 ✓): 返回 null 时说明"为什么没找到" — 频率受调用方节流限制 ✓
        //   matched=0 ⇒ filter 全拒 (matches/名单/工具) ✗; matched>0 且 posRejected==matched ⇒ 开口判定全拒 ✗;
        //   budgetHit=true ⇒ **预算用尽** (16 球 17077 > 16384) ✗
        if (radius >= 10) {
            com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.warn(
                    "[ORE-SCAN] 空结果 r={} 访问={} filter通过={} 开口拒={} 预算用尽={} 她={} limit(d²)={}",
                    radius, visited, matched, posRejected, budgetHit,
                    center.toShortString(), limit);
        }
        return null;
    }

    /** 便捷重载 — 无 posFilter（近扫等） */
    @Nullable
    public static BlockPos find(ServerLevel level, BlockPos center, int radius,
                                Predicate<BlockState> filter, @Nullable Set<Long> skip) {
        return find(level, center, radius, filter, null, skip, DEFAULT_BUDGET_CELLS);
    }
}
