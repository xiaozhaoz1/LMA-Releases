package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RetryPolicy 重试策略单元测试 — 纯算法, 无 Minecraft 依赖。
 */
class RetryPolicyTest {

    @Test
    @DisplayName("NEVER: 任何次数都不重试")
    void never_neverRetries() {
        assertFalse(RetryPolicy.NEVER.shouldRetry(0));
        assertFalse(RetryPolicy.NEVER.shouldRetry(1));
        assertFalse(RetryPolicy.NEVER.shouldRetry(100));
        assertEquals(0, RetryPolicy.NEVER.maxRetries());
    }

    @Test
    @DisplayName("ALWAYS: 无限重试 (含大数)")
    void always_alwaysRetries() {
        assertTrue(RetryPolicy.ALWAYS.shouldRetry(0));
        assertTrue(RetryPolicy.ALWAYS.shouldRetry(1));
        assertTrue(RetryPolicy.ALWAYS.shouldRetry(1_000_000));
    }

    @Test
    @DisplayName("fixed(N): 前 N 次重试, 第 N 次后停止")
    void fixed_retriesExactlyNTimes() {
        RetryPolicy fixed3 = RetryPolicy.fixed(3);
        assertTrue(fixed3.shouldRetry(0), "第 1 次重试应允许");
        assertTrue(fixed3.shouldRetry(1), "第 2 次重试应允许");
        assertTrue(fixed3.shouldRetry(2), "第 3 次重试应允许");
        assertFalse(fixed3.shouldRetry(3), "第 4 次应停止");
        assertFalse(fixed3.shouldRetry(4));
        assertEquals(3, fixed3.maxRetries());
    }

    @Test
    @DisplayName("fixed(0) 等价 NEVER")
    void fixedZero_equalsNever() {
        RetryPolicy rp = RetryPolicy.fixed(0);
        assertFalse(rp.shouldRetry(0));
    }

    @Test
    @DisplayName("工厂方法返回一致策略")
    void factories_consistent() {
        assertSame(RetryPolicy.NEVER, RetryPolicy.never());
        assertSame(RetryPolicy.ALWAYS, RetryPolicy.always());
    }
}
