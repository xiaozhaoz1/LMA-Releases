package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PassiveRotation} 轮转纯函数测试 (v79) — 确定性/环形覆盖/边界。
 */
class PassiveRotationTest {

    @Test
    @DisplayName("startIndex 确定性: 同参同结果")
    void startIndex_deterministic() {
        assertEquals(PassiveRotation.startIndex(1000, 5, 4),
                PassiveRotation.startIndex(1000, 5, 4));
    }

    @Test
    @DisplayName("环形覆盖: size=3 连续 3 tick 覆盖全部 (不同 maidId 错开)")
    void startIndex_cyclesThroughAll() {
        // 固定 (now,maid): 起点固定; 递增 now 逐步覆盖
        boolean[] covered = new boolean[3];
        for (int i = 0; i < 3; i++) {
            int start = PassiveRotation.startIndex(1000 + i, 7, 3);
            covered[start] = true;
        }
        assertTrue(covered[0] && covered[1] && covered[2], "环形轮转覆盖全部 eligible");
    }

    @Test
    @DisplayName("maidId 错开: 不同女仆起点不同 (公平)")
    void startIndex_differentMaidsStaggered() {
        int a = PassiveRotation.startIndex(1000, 1, 4);
        int b = PassiveRotation.startIndex(1000, 2, 4);
        assertNotEquals(a, b, "相邻 maidId 起点错开");
    }

    @Test
    @DisplayName("边界: size=1 恒 0")
    void startIndex_singleEntry_alwaysZero() {
        assertEquals(0, PassiveRotation.startIndex(Long.MAX_VALUE, 42, 1));
        assertEquals(0, PassiveRotation.startIndex(0, 0, 1));
    }

    @Test
    @DisplayName("负值安全: now+maidId 为负 (越界防护) — Math.floorMod 非负")
    void startIndex_negativeSum_nonNegative() {
        int start = PassiveRotation.startIndex(0, -5, 3);
        assertTrue(start >= 0 && start < 3, "floorMod 保证非负");
    }
}
