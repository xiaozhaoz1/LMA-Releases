package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorldInfoCache#isFresh} 纯 JVM 测试 (v79.61x 广播粒度 per-chunk 缓存) —
 * TTL 新鲜度语义 (含 ttl 左闭右开 / 时钟回绕过期), 逐字镜像 StructureScanCache.isFresh 契约。
 */
class WorldInfoCacheTest {

    @Test
    @DisplayName("未过期 (now - scan < ttl) → 新鲜")
    void fresh_ok() {
        assertTrue(WorldInfoCache.isFresh(100, 100, 200));
        assertTrue(WorldInfoCache.isFresh(100, 299, 200));
    }

    @Test
    @DisplayName("ttl 边界: now - scan == ttl → 过期")
    void ttl_boundary() {
        assertFalse(WorldInfoCache.isFresh(100, 300, 200));
        assertFalse(WorldInfoCache.isFresh(100, 301, 200));
    }

    @Test
    @DisplayName("时钟回绕 (scan > now) → 过期")
    void clock_rollback() {
        assertFalse(WorldInfoCache.isFresh(500, 100, 200));
    }

    @Test
    @DisplayName("TTL 契约 = 200t (对齐广播间隔)")
    void ttl_value() {
        assertTrue(WorldInfoCache.isFresh(100, 299, 200));
        assertFalse(WorldInfoCache.isFresh(100, 300, 200));
    }
}
