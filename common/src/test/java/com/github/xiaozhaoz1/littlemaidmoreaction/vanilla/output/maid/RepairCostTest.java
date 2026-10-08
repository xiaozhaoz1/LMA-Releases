package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MaidStateWriter#repairCostFor} 纯 JVM 测试 (v79.48) — 1 点耐久消耗纯函数。
 */
class RepairCostTest {

    @Test
    @DisplayName("好感度消耗乘区映射: Lv3 默认 0.5 → 2 XP/点 (原版 Mending 水平)")
    void cost_lv3() {
        assertEquals(2, MaidStateWriter.repairCostFor(0.5), "4 × 0.5 = 2");
    }

    @Test
    @DisplayName("Lv1 0.9 → 4 (round 3.6=4); Lv2 0.75 → 3; 1.0 → 4 (基数)")
    void cost_mid() {
        assertEquals(4, MaidStateWriter.repairCostFor(0.9), "round(3.6) = 4");
        assertEquals(3, MaidStateWriter.repairCostFor(0.75), "round(3.0) = 3");
        assertEquals(4, MaidStateWriter.repairCostFor(1.0), "4 × 1.0");
    }

    @Test
    @DisplayName("下限 1: 乘区极低也至少 1 XP/点")
    void cost_floor() {
        assertEquals(1, MaidStateWriter.repairCostFor(0.1), "round(0.4) = 0 → max 1");
        assertEquals(1, MaidStateWriter.repairCostFor(0.0));
    }
}
