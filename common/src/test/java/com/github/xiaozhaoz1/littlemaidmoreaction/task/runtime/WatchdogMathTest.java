package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WatchdogMath} 纯函数测试 (v79) — 四象限: 未初始化/正常超时/容忍度内/skew。
 */
class WatchdogMathTest {

    @Test
    @DisplayName("未初始化 (lastTick==0) → 永不超时")
    void isTimedOut_uninitialized_neverTimesOut() {
        assertFalse(WatchdogMath.isTimedOut(1000, 0, 1200, 20));
    }

    @Test
    @DisplayName("正常超时: now-lastTick > timeout+tolerance → true")
    void isTimedOut_pastDeadline_timesOut() {
        assertTrue(WatchdogMath.isTimedOut(3000, 1000, 1200, 20), "2000 > 1220 → 超时");
    }

    @Test
    @DisplayName("容忍度内 (心跳节流补偿): deadline 前 → false")
    void isTimedOut_withinTolerance_notTimedOut() {
        // lastTick=0, timeout=1200, tolerance=20: 触发点 = 1221; 1220 不触发
        assertFalse(WatchdogMath.isTimedOut(1000 + 1200 + 20, 1000, 1200, 20));
        assertTrue(WatchdogMath.isTimedOut(1000 + 1200 + 21, 1000, 1200, 20));
    }

    @Test
    @DisplayName("防溢出: lastTick > now (时钟回绕) → 不判超时 (由 isStale 单独判定)")
    void isTimedOut_clockSkew_neverTimesOut() {
        assertFalse(WatchdogMath.isTimedOut(1000, 2000, 1200, 20));
        assertFalse(WatchdogMath.isTimedOut(1000, Long.MAX_VALUE, 1200, 20));
    }

    @Test
    @DisplayName("isStale: 小偏差 (正常回绕) → false; 超 MAX_SKEW → true")
    void isStale_skewThreshold() {
        assertFalse(WatchdogMath.isStale(1000, 1001), "1 tick 偏差 = 正常回绕");
        assertFalse(WatchdogMath.isStale(1000, 2000));
        assertTrue(WatchdogMath.isStale(1000, 1000 + WatchdogMath.MAX_SKEW + 1),
                "超 24 小时偏差 = 过期残留");
        assertFalse(WatchdogMath.isStale(1000, 1000 + WatchdogMath.MAX_SKEW),
                "恰好 = 上限不触发");
    }
}
