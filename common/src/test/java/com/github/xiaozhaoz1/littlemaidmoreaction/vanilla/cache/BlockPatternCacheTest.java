package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BlockPatternCache#isFresh} 纯 JVM 测试 (v79.61x 缓存体系 L3) —
 * TTL 新鲜度语义: 含 ttl 左闭右开 / 时钟回绕过期 (逐字镜像 EntityScanCache.isFresh 契约)。
 */
class BlockPatternCacheTest {

    @Test
    @DisplayName("未过期 (now - scan < ttl) → 新鲜")
    void fresh_ok() {
        assertTrue(BlockPatternCache.isFresh(100, 100, 40));
        assertTrue(BlockPatternCache.isFresh(100, 139, 40));
    }

    @Test
    @DisplayName("ttl 边界: now - scan == ttl → 过期 (左闭右开)")
    void ttl_boundary() {
        assertFalse(BlockPatternCache.isFresh(100, 140, 40));
        assertFalse(BlockPatternCache.isFresh(100, 141, 40));
    }

    @Test
    @DisplayName("时钟回绕 (scan > now) → 过期")
    void clock_rollback() {
        assertFalse(BlockPatternCache.isFresh(500, 100, 40));
    }

    @Test
    @DisplayName("per-type TTL 值契约 (v79.62: SNOW 已删 — 清雪移除; Heat 100 / Water 100 / Container 200)")
    void pattern_type_ttl() {
        assertTrue(BlockPatternCache.PatternType.HEAT.ttl == 100);
        assertTrue(BlockPatternCache.PatternType.WATER.ttl == 100);
        assertTrue(BlockPatternCache.PatternType.CONTAINER.ttl == 200);
    }
}
