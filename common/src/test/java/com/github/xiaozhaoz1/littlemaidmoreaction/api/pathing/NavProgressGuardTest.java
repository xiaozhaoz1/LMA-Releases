package com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NavProgressGuard 纯函数测试 (v79.61x) — 卡死判定/位移判定/CSV 解析。
 * BlockPos 纯数据类无注册表 clinit 链, JVM 直测安全 (错题 #174 边界内)。
 */
class NavProgressGuardTest {

    // ── shouldFlagStuck ──

    @Test
    void stuckRequiresRecordedProgress() {
        // 无记录 (0) → 永不判卡死
        assertFalse(NavProgressGuard.shouldFlagStuck(0, 500, 100));
    }

    @Test
    void stuckIgnoresClockRewind() {
        // 时钟回绕 (记录 > 当前) → 不判卡死
        assertFalse(NavProgressGuard.shouldFlagStuck(600, 500, 100));
    }

    @Test
    void stuckWithinWindowIsFalse() {
        assertFalse(NavProgressGuard.shouldFlagStuck(400, 500, 100));
    }

    @Test
    void stuckAtWindowBoundaryIsFalse() {
        // 严格大于窗口才卡死 (== 边界不触发)
        assertFalse(NavProgressGuard.shouldFlagStuck(400, 500, 100));
        assertTrue(NavProgressGuard.shouldFlagStuck(399, 500, 100));
    }

    // ── hasProgress ──

    @Test
    void progressAboveThresholdIsTrue() {
        // 位移 1 格 > 0.5 阈值
        assertTrue(NavProgressGuard.hasProgress(
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), 0.25));
    }

    @Test
    void progressBelowThresholdIsFalse() {
        // 位移 0.3 格 < 0.5 阈值
        assertFalse(NavProgressGuard.hasProgress(
                new BlockPos(0, 0, 0), new BlockPos(0, 0, 0), 0.25));
    }

    // ── parsePos ──

    @Test
    void parseValidCsv() {
        assertEquals(new BlockPos(1, 2, 3), NavProgressGuard.parsePos("1,2,3"));
        assertEquals(new BlockPos(-4, 0, 7), NavProgressGuard.parsePos("-4, 0, 7"));
    }

    @Test
    void parseEmptyReturnsNull() {
        assertNull(NavProgressGuard.parsePos(""));
        assertNull(NavProgressGuard.parsePos(null));
    }

    @Test
    void parseBadReturnsNull() {
        assertNull(NavProgressGuard.parsePos("abc"));
        assertNull(NavProgressGuard.parsePos("1,2"));
        assertNull(NavProgressGuard.parsePos("1,x,3"));
    }
}
