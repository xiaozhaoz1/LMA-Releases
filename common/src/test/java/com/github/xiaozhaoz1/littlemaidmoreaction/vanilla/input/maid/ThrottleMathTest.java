package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ThrottleMath} 纯 JVM 测试 (v79.49) — 节流判定纯函数 (ThrottleUtil 语义逐字等价)。
 */
class ThrottleMathTest {

    @Test
    @DisplayName("首次放行: last=0 (未标记) → 不冷却")
    void first_fire() {
        assertFalse(ThrottleMath.isCoolingDown(100L, 0L, 600L), "last=0 放行");
    }

    @Test
    @DisplayName("窗口内节流: now-last < interval → 冷却中")
    void within_window() {
        assertTrue(ThrottleMath.isCoolingDown(650L, 100L, 600L), "550 < 600 冷却中");
    }

    @Test
    @DisplayName("过期重置: now-last >= interval → 放行")
    void expired() {
        assertFalse(ThrottleMath.isCoolingDown(700L, 100L, 600L), "600 >= 600 放行");
        assertFalse(ThrottleMath.isCoolingDown(1000L, 100L, 600L), "900 远超放行");
    }

    @Test
    @DisplayName("时钟回退 (跨 session): last > now → 放行")
    void clock_rollback() {
        assertFalse(ThrottleMath.isCoolingDown(100L, 9999L, 600L), "last>now 视为过期");
    }

    @Test
    @DisplayName("cooldownRemaining: 窗口内剩余 / 未标记 0 / 回退 0")
    void remaining() {
        assertEquals(50L, ThrottleMath.cooldownRemaining(650L, 100L, 600L), "600-(650-100)=50");
        assertEquals(0L, ThrottleMath.cooldownRemaining(800L, 100L, 600L), "过期 0");
        assertEquals(0L, ThrottleMath.cooldownRemaining(100L, 0L, 600L), "未标记 0");
        assertEquals(0L, ThrottleMath.cooldownRemaining(100L, 9999L, 600L), "回退 0");
    }

    @Test
    @DisplayName("interval=0: 永不冷却 (now-last < 0 恒假)")
    void zero_interval() {
        assertFalse(ThrottleMath.isCoolingDown(100L, 100L, 0L));
    }
}
