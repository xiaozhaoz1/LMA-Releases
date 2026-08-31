package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MaidFavorability#forLevel} 纯函数测试 (审计 T1 — #174 铁律:
 * 好感度双乘区原耦合 EntityMaid+ActiveTaskConfig 零测试, 抽纯内核后 JVM 直测)。
 */
public class MaidFavorabilityTest {

    @Test
    @DisplayName("总开关关闭 → 恒 1.0")
    void disabled_alwaysOne() {
        assertEquals(1.0, MaidFavorability.forLevel(3, false, 0.9, 0.75, 0.5));
        assertEquals(1.0, MaidFavorability.forLevel(0, false, 0.9, 0.75, 0.5));
    }

    @Test
    @DisplayName("Lv0/未知等级 → 1.0")
    void levelZeroOrUnknown_one() {
        assertEquals(1.0, MaidFavorability.forLevel(0, true, 1.1, 1.25, 1.5));
        assertEquals(1.0, MaidFavorability.forLevel(-1, true, 1.1, 1.25, 1.5));
        assertEquals(1.0, MaidFavorability.forLevel(9, true, 1.1, 1.25, 1.5));
    }

    @Test
    @DisplayName("Lv1/2/3 → 对应档位参数")
    void levels_pickMatchingTier() {
        assertEquals(1.1, MaidFavorability.forLevel(1, true, 1.1, 1.25, 1.5));
        assertEquals(1.25, MaidFavorability.forLevel(2, true, 1.1, 1.25, 1.5));
        assertEquals(1.5, MaidFavorability.forLevel(3, true, 1.1, 1.25, 1.5));
    }

    @Test
    @DisplayName("消耗线同形 (0.9/0.75/0.5)")
    void costLine_sameShape() {
        assertEquals(1.0, MaidFavorability.forLevel(0, true, 0.9, 0.75, 0.5));
        assertEquals(0.9, MaidFavorability.forLevel(1, true, 0.9, 0.75, 0.5));
        assertEquals(0.75, MaidFavorability.forLevel(2, true, 0.9, 0.75, 0.5));
        assertEquals(0.5, MaidFavorability.forLevel(3, true, 0.9, 0.75, 0.5));
    }

    // ── workTicks 纯函数 (v79.61x 重复抽取) ──

    @Test
    @DisplayName("效率计时 = base / speed 向下取整")
    void workTicks_dividesBySpeed() {
        assertEquals(100, MaidFavorability.workTicks(100, 1.0));
        assertEquals(80, MaidFavorability.workTicks(100, 1.25));
        assertEquals(66, MaidFavorability.workTicks(100, 1.5));
        assertEquals(33, MaidFavorability.workTicks(40, 1.2));
    }

    @Test
    @DisplayName("效率计时下限 1 (speed 极大不为 0); 非法 speed 防御为 base")
    void workTicks_floorOne() {
        assertEquals(1, MaidFavorability.workTicks(1, 100.0));
        assertEquals(40, MaidFavorability.workTicks(40, 0.0));   // speed<=0 → 无加乘, 返回 base
        assertEquals(40, MaidFavorability.workTicks(40, -1.0));  // 同上
        assertEquals(40, MaidFavorability.workTicks(40, Double.NaN));
    }
}
