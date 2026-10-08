package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StructureScanCache.isFresh 纯函数用例 (2026-08-16 预算方案 P) —
 * 新鲜判定边界: TTL 窗口 / 恰在边界 / 时钟回退守卫 (对齐 ThrottleMath 语义)。
 */
class StructureScanCacheTest {

    @Test
    void sameTickIsFresh() {
        assertTrue(StructureScanCache.isFresh(100, 100, 400));
    }

    @Test
    void withinTtlIsFresh() {
        assertTrue(StructureScanCache.isFresh(100, 499, 400));
    }

    @Test
    void exactlyTtlIsStale() {
        assertFalse(StructureScanCache.isFresh(100, 500, 400));
    }

    @Test
    void beyondTtlIsStale() {
        assertFalse(StructureScanCache.isFresh(100, 501, 400));
    }

    @Test
    void clockRewindIsStale() {
        assertFalse(StructureScanCache.isFresh(500, 100, 400));
    }
}
