package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TaskDispatcher#shouldPreempt} 优先级策略纯函数测试 (v79)。
 *
 * <p>裁定: 等优先级 = 抢占 (树内 12 任务全默认 0, GUI_INIT/TLM_SWITCH/绑定提交
 * 依赖等优先级抢占); 仅新任务严格更低 → 拒绝。
 */
class PriorityPolicyTest {

    @Test
    @DisplayName("等优先级 (全默认 0) → 抢占 (保留既有切换行为)")
    void shouldPreempt_equalPriority_preempts() {
        assertTrue(TaskDispatcher.shouldPreempt(0, 0), "等优先级必须可抢占 — 既有功能依赖");
        assertTrue(TaskDispatcher.shouldPreempt(3, 3));
    }

    @Test
    @DisplayName("新任务更高优先级 → 抢占")
    void shouldPreempt_higherPriority_preempts() {
        assertTrue(TaskDispatcher.shouldPreempt(0, 1));
        assertTrue(TaskDispatcher.shouldPreempt(2, 5));
    }

    @Test
    @DisplayName("新任务严格更低 → 拒绝")
    void shouldPreempt_lowerPriority_rejects() {
        assertFalse(TaskDispatcher.shouldPreempt(1, 0), "新任务更低优先级 → 拒绝");
        assertFalse(TaskDispatcher.shouldPreempt(5, 2));
    }
}
