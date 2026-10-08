package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ChainHarvestMath 纯内核单测 (v79.61 架构批 3a C1) —
 * 零 MC 依赖 (错题 #174 铁律: MC 类型不进纯测试)。
 */
class ChainHarvestMathTest {

    @Test
    void 蓄力时长_基本与效率乘区() {
        assertEquals(200L, ChainHarvestMath.chargeTicks(10, 20, 1.0));
        assertEquals(100L, ChainHarvestMath.chargeTicks(10, 20, 2.0));
        assertEquals(10L, ChainHarvestMath.chargeTicks(3, 7, 2.0));   // 10.5 → 截断 10
    }

    @Test
    void 耐久预算裁剪_上限下限() {
        assertEquals(10, ChainHarvestMath.durabilityCropSize(10, 100, 20));  // 预算充足 → 全量
        assertEquals(5, ChainHarvestMath.durabilityCropSize(10, 25, 20));    // 预算 5
        assertEquals(0, ChainHarvestMath.durabilityCropSize(10, 20, 20));    // 预算恰 0
        assertEquals(0, ChainHarvestMath.durabilityCropSize(10, 15, 20));    // 预算负 → 0
    }

    @Test
    void 耐久消耗乘区_最低一点() {
        assertEquals(0, ChainHarvestMath.durabilityCost(0, 0.5));
        assertEquals(5, ChainHarvestMath.durabilityCost(5, 1.0));
        assertEquals(2, ChainHarvestMath.durabilityCost(5, 0.5));
        assertEquals(1, ChainHarvestMath.durabilityCost(5, 0.1));   // 0.5 → 截断 0 → 最低 1 点
    }

    @Test
    void 扫描垂直范围_木系固定矿系对齐挖穿深度() {
        assertEquals(12, ChainHarvestMath.vRange(true, 3));    // WOOD 固定 12
        assertEquals(5, ChainHarvestMath.vRange(false, 3));    // ORE 下限 5
        assertEquals(8, ChainHarvestMath.vRange(false, 8));    // ORE 对齐挖穿深度
    }

    @Test
    void 扫描预算_半径平方除系数() {
        assertEquals(16, ChainHarvestMath.scanBudget(16));
        assertEquals(1, ChainHarvestMath.scanBudget(4));
        assertEquals(0, ChainHarvestMath.scanBudget(2));    // 整数除法 4/16=0
    }
}
