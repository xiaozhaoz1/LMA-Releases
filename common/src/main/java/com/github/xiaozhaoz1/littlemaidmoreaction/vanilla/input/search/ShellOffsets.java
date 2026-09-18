package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * **球形距离序偏移表** (v79.64 采矿最近优先搜索的底座) — 纯逻辑类, 不碰 MC 世界 (错题 #174: 纯逻辑抽纯类 ⇒ 可纯 JVM 单测 ✓)。
 *
 * <h2>为什么需要它</h2>
 * 旧采矿扫描 {@link BlockScanner} 是"区块环序 → 环内周长序 → 区块内 x/z 序, 收满名额立即 return, 之后才按距离排序"
 * ⇒ **结果不是最近的方块** ✗ (2026-09-16 用户实测: 邻居区块 / 新放置的矿永远排不进候选)。
 * 本类把"由近到远的遍历顺序"变成**可复用的纯数据**: 按**精确距离平方 d²** 分桶, 桶内是全部该距离的偏移量
 * ⇒ 逐桶迭代 + 命中即停 = **严格最近的合格方块** ✓✓
 *
 * <h2>语义</h2>
 * <ul>
 *   <li>偏移集合: {@code dx,dz ∈ [-maxRadius, maxRadius]}, {@code dy ∈ [-halfHeight, halfHeight]}</li>
 *   <li>桶 k = 所有满足 {@code dx²+dy²+dz² == k} 的 (dx,dy,dz); 桶 0 = 中心格自身 ✓ (调用方自行跳过即可)</li>
 *   <li>迭代顺序 = {@code k = 0,1,2,…} ⇒ **由近到远** ✓ (同桶内顺序无关, 距离相同 ✓)</li>
 * </ul>
 *
 * <h2>典型用量 (用户 2026-09-16 裁定)</h2>
 * <pre>
 *   近扫: maxRadius=4,  halfHeight=8, 每 5t      (4 格球 ≈ 257 格 ✓ 便宜)
 *   中扫: maxRadius=16, halfHeight=8, 每 100t
 *   远扫: maxRadius=32, halfHeight=8, 每 250t    (32×32×16 ≈ 1.85 万格 ⇒ 由节流兜 ✓)
 * </pre>
 */
public final class ShellOffsets {

    /** 水平半径 (格) */
    public final int maxRadius;
    /** 垂直半高 (格) — "16 格高" ⇒ halfHeight = 8 ✓ */
    public final int halfHeight;
    /** 全部已用偏移总量 (含中心) */
    public final int totalCells;

    /** buckets[k] = 距离平方 == k 的全部偏移, 扁平三元组 (dx,dy,dz) */
    private final int[][] buckets;
    /** 最大桶号 = maxRadius²*2 + halfHeight² */
    public final int maxD2;

    private ShellOffsets(int maxRadius, int halfHeight) {
        this.maxRadius = maxRadius;
        this.halfHeight = halfHeight;
        int maxD2 = 2 * maxRadius * maxRadius + halfHeight * halfHeight;
        this.maxD2 = maxD2;
        int[] counts = new int[maxD2 + 1];
        int total = 0;
        for (int dy = -halfHeight; dy <= halfHeight; dy++) {
            for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                for (int dx = -maxRadius; dx <= maxRadius; dx++) {
                    counts[dx * dx + dy * dy + dz * dz]++;
                    total++;
                }
            }
        }
        this.totalCells = total;
        int[][] buckets = new int[maxD2 + 1][];
        for (int k = 0; k <= maxD2; k++) {
            if (counts[k] > 0) buckets[k] = new int[counts[k] * 3];
        }
        int[] cursor = new int[maxD2 + 1];
        for (int dy = -halfHeight; dy <= halfHeight; dy++) {
            for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                for (int dx = -maxRadius; dx <= maxRadius; dx++) {
                    int k = dx * dx + dy * dy + dz * dz;
                    int[] arr = buckets[k];
                    int c = cursor[k];
                    arr[c] = dx;
                    arr[c + 1] = dy;
                    arr[c + 2] = dz;
                    cursor[k] = c + 3;
                }
            }
        }
        this.buckets = buckets;
    }

    private static final Map<Long, ShellOffsets> CACHE = new ConcurrentHashMap<>();

    /** 取 (半径, 半高) 对应的偏移表 — 表**不可变** ⇒ 缓存后可跨线程安全复用 ✓ */
    public static ShellOffsets of(int maxRadius, int halfHeight) {
        if (maxRadius < 0 || halfHeight < 0) throw new IllegalArgumentException("负参数");
        long key = ((long) maxRadius << 32) | (halfHeight & 0xFFFFFFFFL);
        return CACHE.computeIfAbsent(key, k -> new ShellOffsets(maxRadius, halfHeight));
    }

    /** 桶总数 (= maxD2 + 1) — 逐桶迭代用 ✓ */
    public int bucketCount() {
        return maxD2 + 1;
    }

    /**
     * 桶 k 的偏移数据 (扁平三元组 dx,dy,dz) — 无该距离时返回 {@code null} ✓
     * 调用方: 从 k=0 递增, 命中即停 ⇒ 天然最近 ✓
     */
    public int[] bucket(int d2) {
        if (d2 < 0 || d2 > maxD2) return null;
        return buckets[d2];
    }

    /** 该桶里有多少个偏移 (便于预算统计 ✓) */
    public int cellCount(int d2) {
        int[] arr = bucket(d2);
        return arr == null ? 0 : arr.length / 3;
    }

    /** 是否在半径/半高范围内 (预算裁剪用 ✓) */
    public boolean contains(int dx, int dy, int dz) {
        return Math.abs(dx) <= maxRadius && Math.abs(dz) <= maxRadius && Math.abs(dy) <= halfHeight;
    }
}
