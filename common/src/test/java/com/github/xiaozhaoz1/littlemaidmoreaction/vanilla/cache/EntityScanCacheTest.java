package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache;

import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.RingSpiral;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EntityScanCache.isFresh 纯函数用例 (2026-08-16 环境感知扫描缓存) —
 * TTL 窗口 / 恰在边界 / 时钟回退守卫 (对齐 StructureScanCache 同款语义)。
 */
class EntityScanCacheTest {

    @Test
    void sameTickIsFresh() {
        assertTrue(EntityScanCache.isFresh(100, 100, 200));
    }

    @Test
    void withinTtlIsFresh() {
        assertTrue(EntityScanCache.isFresh(100, 299, 200));
    }

    @Test
    void exactlyTtlIsStale() {
        assertFalse(EntityScanCache.isFresh(100, 300, 200));
    }

    @Test
    void beyondTtlIsStale() {
        assertFalse(EntityScanCache.isFresh(100, 301, 200));
    }

    @Test
    void clockRewindIsStale() {
        assertFalse(EntityScanCache.isFresh(500, 100, 200));
    }

    // ── grace 宽限 (Retold 模式 ②: 过期条目宽限内仍可读) ──

    @Test
    void withinGraceWindowStillUsable() {
        // TTL 200 + grace 40: 239 tick 内仍可用
        assertTrue(EntityScanCache.isFresh(100, 339, EntityScanCache.CACHE_TTL_TICKS + EntityScanCache.GRACE_TICKS));
    }

    @Test
    void beyondGraceWindowIsStale() {
        assertFalse(EntityScanCache.isFresh(100, 341, EntityScanCache.CACHE_TTL_TICKS + EntityScanCache.GRACE_TICKS));
    }

    // ── 漂移门限常量 (Retold 模式 ①) ──

    @Test
    void driftThresholdMatchesRetold() {
        // 3.5 格平方门限 (中心漂移超过即失效重扫)
        org.junit.jupiter.api.Assertions.assertEquals(12.25, EntityScanCache.MAX_CENTER_DRIFT_SQ, 1e-6);
    }

    // ── driftSq 纯函数: 中心漂移平方 ──

    @Test
    void driftSq_同点为零() {
        var c = new net.minecraft.core.BlockPos(10, 64, 10);
        org.junit.jupiter.api.Assertions.assertEquals(0.0, EntityScanCache.driftSq(c, 10, 64, 10), 1e-9);
    }

    @Test
    void driftSq_边界内() {
        // (3, 0, 1) → 3²+0²+1² = 10 ≤ 12.25
        var c = new net.minecraft.core.BlockPos(0, 0, 0);
        org.junit.jupiter.api.Assertions.assertEquals(10.0, EntityScanCache.driftSq(c, 3, 0, 1), 1e-9);
    }

    @Test
    void driftSq_边界外() {
        // (4, 0, 0) → 16 > 12.25
        var c = new net.minecraft.core.BlockPos(0, 0, 0);
        org.junit.jupiter.api.Assertions.assertEquals(16.0, EntityScanCache.driftSq(c, 4, 0, 0), 1e-9);
    }

    @Test
    void driftSq_三维对角() {
        // (3, 3, 3) → 27
        var c = new net.minecraft.core.BlockPos(0, 0, 0);
        org.junit.jupiter.api.Assertions.assertEquals(27.0, EntityScanCache.driftSq(c, 3, 3, 3), 1e-9);
    }

    // ── shouldReuseQuery 纯函数: L1 命中判定 (新鲜 + 漂移) ──

    @Test
    void reuseQuery_新鲜且漂移内_命中() {
        assertTrue(EntityScanCache.shouldReuseQuery(100, 200, 10.0, 12.25));
    }

    @Test
    void reuseQuery_结果过期_不命中() {
        assertFalse(EntityScanCache.shouldReuseQuery(100, 300, 0.0, 12.25));
    }

    @Test
    void reuseQuery_漂移超门限_不命中() {
        assertFalse(EntityScanCache.shouldReuseQuery(100, 200, 16.0, 12.25));
    }

    @Test
    void reuseQuery_时钟回退_不命中() {
        assertFalse(EntityScanCache.shouldReuseQuery(500, 100, 0.0, 12.25));
    }

    @Test
    void reuseQuery_漂移恰在门限_命中() {
        // 3.5² = 12.25 边界含等号 (Retold 语义)
        assertTrue(EntityScanCache.shouldReuseQuery(100, 200, 12.25, 12.25));
    }

    // ── chunkRadiusOf 纯函数: 半径 → 区块半径 ──

    @Test
    void chunkRadiusOf_零或负_下限一() {
        org.junit.jupiter.api.Assertions.assertEquals(1, EntityScanCache.chunkRadiusOf(0));
        org.junit.jupiter.api.Assertions.assertEquals(1, EntityScanCache.chunkRadiusOf(-5));
    }

    @Test
    void chunkRadiusOf_16格_一区块() {
        org.junit.jupiter.api.Assertions.assertEquals(1, EntityScanCache.chunkRadiusOf(16));
    }

    @Test
    void chunkRadiusOf_17格_两区块() {
        org.junit.jupiter.api.Assertions.assertEquals(2, EntityScanCache.chunkRadiusOf(17));
    }

    @Test
    void chunkRadiusOf_64格_四区块() {
        org.junit.jupiter.api.Assertions.assertEquals(4, EntityScanCache.chunkRadiusOf(64));
    }

    @Test
    void chunkRadiusOf_65格_五区块() {
        org.junit.jupiter.api.Assertions.assertEquals(5, EntityScanCache.chunkRadiusOf(65));
    }

    // ── 螺旋覆盖: RingSpiral 枚举 = 期望区块集 (无重复, 中心优先) ──

    @Test
    void 螺旋覆盖_默认半径16_恰9区块() {
        int r = EntityScanCache.chunkRadiusOf(16);
        java.util.Set<Long> covered = new java.util.HashSet<>();
        int first = 0;
        boolean centerFirst = false;
        for (int ring = 0; ring <= r; ring++) {
            for (int i = 0; i < RingSpiral.perimeter(ring); i++) {
                int[] off = RingSpiral.offset(ring, i);
                if (Math.max(Math.abs(off[0]), Math.abs(off[1])) > r) continue;
                long key = (long) off[0] * 1000 + off[1];
                if (covered.isEmpty() && off[0] == 0 && off[1] == 0) centerFirst = true;
                covered.add(key);
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(9, covered.size());
        org.junit.jupiter.api.Assertions.assertTrue(centerFirst, "螺旋首位应为中心区块");
    }

    @Test
    void 螺旋覆盖_大半径_无重复全覆盖() {
        int r = EntityScanCache.chunkRadiusOf(48);   // (48+15)>>4 = 3 → 7×7 = 49
        java.util.Set<Long> covered = new java.util.HashSet<>();
        for (int ring = 0; ring <= r; ring++) {
            for (int i = 0; i < RingSpiral.perimeter(ring); i++) {
                int[] off = RingSpiral.offset(ring, i);
                if (Math.max(Math.abs(off[0]), Math.abs(off[1])) > r) continue;
                covered.add((long) off[0] * 1000 + off[1]);
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(49, covered.size());
    }
}
