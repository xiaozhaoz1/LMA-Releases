package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RecoveryLadder} 纯 JVM 测试 (v79.61x) — 恢复阶梯推进语义
 * (重试上限 / 换档 / 穿透 / 耗尽 / 重置 - Numen 原语义逐项验证).
 */
class RecoveryLadderTest {

    /** 档位: A 接 "occluded", B 接 "no_path", C 接 "occluded" */
    private static RecoveryLadder<String> ladder() {
        return RecoveryLadder.of(List.of(
                new RecoveryLadder.Rung<>(s -> s.equals("occluded"), 2),
                new RecoveryLadder.Rung<>(s -> s.equals("no_path"), 1),
                new RecoveryLadder.Rung<>(s -> s.equals("occluded"), 1)));
    }

    @Test
    @DisplayName("构建校验: 空阶梯拒绝 / maxAttempts < 1 拒绝")
    void construction_guards() {
        assertThrows(IllegalArgumentException.class, () -> RecoveryLadder.of(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> RecoveryLadder.of(List.of(new RecoveryLadder.Rung<>(s -> true, 0))));
    }

    @Test
    @DisplayName("重试上限: 档 A 接住且未超 2 次 → 停留 +1")
    void retry_within_limit() {
        var l = ladder();
        l.enterCurrent();                       // A 第 1 次
        assertTrue(l.advance("occluded"));      // A 第 2 次 (重试)
        assertEquals(0, l.currentIndex(), "仍在 A");
        assertEquals(2, l.attemptsOn(0));
    }

    @Test
    @DisplayName("超限换档: A 用满 2 次后同一失败 → 找下一档接得住的 (跳过 B)")
    void advance_past_limit_skips_non_matching() {
        var l = ladder();
        l.enterCurrent();                       // A 1
        assertTrue(l.advance("occluded"));      // A 2
        assertTrue(l.advance("occluded"));      // A 超限 → 跳过 B(no_path 不接) → C
        assertEquals(2, l.currentIndex(), "进 C");
        assertEquals(1, l.attemptsOn(2));
    }

    @Test
    @DisplayName("穿透: 前置类失败 (no_material) 无档接 → 耗尽 false")
    void prerequisite_falls_through() {
        var l = ladder();
        l.enterCurrent();
        assertFalse(l.advance("no_material"), "无档声明接前置类失败 → 穿透");
        assertTrue(l.exhausted());
    }

    @Test
    @DisplayName("耗尽: 所有档用完 → false")
    void exhausts() {
        var l = ladder();
        l.enterCurrent();                       // A 1
        assertTrue(l.advance("occluded"));      // A 2
        assertTrue(l.advance("occluded"));      // C 1
        assertFalse(l.advance("occluded"));     // 全耗尽
        assertTrue(l.exhausted());
    }

    @Test
    @DisplayName("重置: 回第一档, 计数清零")
    void reset() {
        var l = ladder();
        l.enterCurrent();
        l.advance("occluded");
        l.advance("occluded");                  // 到 C
        assertEquals(2, l.currentIndex());
        l.reset();
        assertEquals(0, l.currentIndex());
        assertEquals(0, l.attemptsOn(2));
        assertFalse(l.exhausted());
    }

    @Test
    @DisplayName("耗尽后 advance 恒 false (无副作用)")
    void advance_after_exhausted() {
        var l = ladder();
        l.enterCurrent();
        l.advance("occluded");
        l.advance("occluded");
        l.advance("occluded");                  // 耗尽
        assertTrue(l.exhausted());
        assertFalse(l.advance("occluded"));
    }
}
