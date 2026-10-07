package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token.TokenStealMath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 偷吃 Token 判定守护 (v79.66o, 用户裁定 2026-09-20 ✓)。
 *
 * <p>固化三件事 (改口径必须先改这里 + CHANGELOG):
 * <ol>
 *   <li><b>6000t 冷却从"上一次概率判定"起算</b> ✓ (不是"命中了才计时") ⇒ cd 一转完立即判定 ✓</li>
 *   <li><b>5% 概率</b>语义 (≤0 永不 / ≥1 必中 / 边界)</li>
 *   <li><b>8 格内</b>才偷 (平方距离, 边界含 8.0 ✓)</li>
 * </ol>
 */
class TokenStealMathTest {

    @Test
    @DisplayName("冷却: 从**上次判定**起算 — 未到点不判, 一到点就判")
    void cooldownCountsFromLastAttempt() {
        // 从未判定过 ⇒ 立即可判 ✓ (第一次心跳就进入判定) 
        assertTrue(TokenStealMath.cooldownElapsed(Long.MIN_VALUE, 12345L, 6000), "从未判定应立即可判");

        // 上次判定 = 1000 ⇒ 1000+5999 还没到 6000 ⇒ 不判 ✓
        assertFalse(TokenStealMath.cooldownElapsed(1000L, 1000L + 5999L, 6000), "差 1t 不应判定");
        // 正好 6000 ⇒ 判定 ✓ (边界含)
        assertTrue(TokenStealMath.cooldownElapsed(1000L, 1000L + 6000L, 6000), "满 6000t 应判定");
        // 超时很久 (女仆卸载后回来) ⇒ 判定 ✓
        assertTrue(TokenStealMath.cooldownElapsed(1000L, 1000L + 999999L, 6000), "远超间隔应判定");

        // interval ≤ 0 ⇒ 视为无冷却 (调试口径) ✓
        assertTrue(TokenStealMath.cooldownElapsed(1000L, 1000L, 0), "interval=0 应视为无冷却");
        assertTrue(TokenStealMath.cooldownElapsed(1000L, 1000L, -5), "interval<0 应视为无冷却");
    }

    @Test
    @DisplayName("概率: 0.05 = 5%, 边界与钳制")
    void chanceSemantics() {
        // roll ∈ [0,1): 小于 0.05 命中 ⇒ 0.000 与 0.049 命中; 0.05 与 0.999 不命中 ✓
        assertTrue(TokenStealMath.rollHit(0.05f, 0.0f), "roll=0 必中 (5% 窗口下界)");
        assertTrue(TokenStealMath.rollHit(0.05f, 0.0499f), "roll<0.05 应命中");
        assertFalse(TokenStealMath.rollHit(0.05f, 0.05f), "roll=0.05 不命中 (半开区间)");
        assertFalse(TokenStealMath.rollHit(0.05f, 0.999f), "roll 大 ⇒ 不命中");

        // 钳制: ≤0 永不 / ≥1 必中 ✓
        assertFalse(TokenStealMath.rollHit(0f, 0f), "chance=0 ⇒ 永不");
        assertFalse(TokenStealMath.rollHit(-0.5f, 0f), "chance<0 ⇒ 永不");
        assertTrue(TokenStealMath.rollHit(1f, 0.999f), "chance=1 ⇒ 必中");
        assertTrue(TokenStealMath.rollHit(2f, 0.999f), "chance>1 ⇒ 必中");
    }

    @Test
    @DisplayName("距离: 8 格内 (含边界), 负数上限视为不可达")
    void rangeSemantics() {
        assertTrue(TokenStealMath.withinRange(0.0, 8.0), "贴身 ✓");
        assertTrue(TokenStealMath.withinRange(64.0, 8.0), "正好 8 格 ⇒ 含边界 ✓");
        assertFalse(TokenStealMath.withinRange(64.01, 8.0), "略超 8 格 ⇒ 不偷 ✓");
        assertFalse(TokenStealMath.withinRange(10_000.0, 8.0), "很远 ⇒ 不偷 ✓");
        assertFalse(TokenStealMath.withinRange(1.0, -1.0), "负上限 ⇒ 一律不偷 (防护) ✓");
    }
}
