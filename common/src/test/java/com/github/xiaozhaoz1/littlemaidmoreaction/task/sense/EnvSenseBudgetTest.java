package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EnvSenseBudget} 纯 JVM 测试 (v79.3 D3) — 时钟注入边界。
 */
class EnvSenseBudgetTest {

    @Test
    @DisplayName("begin 后 deadline = now + max; 超限翻转")
    void pass_deadlineAndExhaustion() {
        EnvSenseBudget.Pass pass = EnvSenseBudget.begin(1_000_000L, 8_000_000L);
        assertEquals(9_000_000L, pass.deadlineNanos());
        assertFalse(pass.exhausted(8_999_999L), "deadline 前未超限");
        assertTrue(pass.exhausted(9_000_001L), "deadline 后超限");
        assertFalse(pass.exhausted(9_000_000L), "恰 deadline 不超限 (> 语义)");
    }

    @Test
    @DisplayName("零/负预算立即超限 (防御)")
    void pass_zeroBudgetImmediatelyExhausted() {
        EnvSenseBudget.Pass pass = EnvSenseBudget.begin(100L, 0L);
        assertTrue(pass.exhausted(101L));
    }

    @Test
    @DisplayName("大预算不误报")
    void pass_largeBudgetTolerant() {
        EnvSenseBudget.Pass pass = EnvSenseBudget.begin(0L, 1_000_000_000L);
        assertFalse(pass.exhausted(999_999_999L));
    }
}
