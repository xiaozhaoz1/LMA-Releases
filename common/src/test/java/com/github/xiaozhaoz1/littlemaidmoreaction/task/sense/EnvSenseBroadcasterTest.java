package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EnvSenseBroadcaster 分流判定纯函数用例 (2026-08-16 混合分流) —
 * shouldUseCache: eligible 女仆 ≥3 走缓存共享, ≤2 直扫原路
 * (成本模型: 预热 9 区块全高扫描 ≈ 5-10 次近身扫描)。
 */
class EnvSenseBroadcasterTest {

    @Test
    void 零女仆_不缓存() {
        assertFalse(EnvSenseBroadcaster.shouldUseCache(0));
    }

    @Test
    void 一个女仆_直扫() {
        assertFalse(EnvSenseBroadcaster.shouldUseCache(1));
    }

    @Test
    void 两个女仆_直扫() {
        assertFalse(EnvSenseBroadcaster.shouldUseCache(2));
    }

    @Test
    void 三个女仆_走缓存() {
        assertTrue(EnvSenseBroadcaster.shouldUseCache(3));
    }

    @Test
    void 十个女仆_走缓存() {
        assertTrue(EnvSenseBroadcaster.shouldUseCache(10));
    }
}
