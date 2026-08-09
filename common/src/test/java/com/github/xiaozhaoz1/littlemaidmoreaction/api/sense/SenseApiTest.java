package com.github.xiaozhaoz1.littlemaidmoreaction.api.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SenseApi} 纯 JVM 可测面 (v79.3) — 纯逻辑委托 + null 安全。
 * MC 方法 (snapshot/scan 等) 走 gametest/游戏内验证。
 */
class SenseApiTest {

    @Test
    @DisplayName("tempCategory/timeSegment 委托 EnvRules (边界)")
    void pureLogic_delegatesToEnvRules() {
        assertEquals("COLD", SenseApi.tempCategory(0.0f));
        assertEquals("WARM", SenseApi.tempCategory(1.0f));
        assertEquals("DAY", SenseApi.timeSegment(0));
        assertEquals("NIGHT", SenseApi.timeSegment(15000));
        assertEquals("DAWN", SenseApi.timeSegment(23000));
    }

    @Test
    @DisplayName("null 安全: snapshot/worldInfo(null) → null (不 NPE)")
    void nullMaid_safe() {
        assertNull(SenseApi.snapshot(null));
        assertNull(SenseApi.worldInfo(null));
    }

    @Test
    @DisplayName("scanResults/cancelScan/isScanDone(null job) 安全")
    void nullJob_safe() {
        assertTrue(SenseApi.scanResults(null).isEmpty());
        assertFalse(SenseApi.isScanDone(null));
        SenseApi.cancelScan(null);   // 不炸
    }

    @Test
    @DisplayName("envConfig 构造透传")
    void envConfig_passthrough() {
        var cfg = SenseApi.envConfig(0.15f, 1.0f, 7);
        assertEquals(0.15f, cfg.coldThreshold());
        assertEquals(1.0f, cfg.hotThreshold());
        assertEquals(7, cfg.darknessThreshold());
    }
}
