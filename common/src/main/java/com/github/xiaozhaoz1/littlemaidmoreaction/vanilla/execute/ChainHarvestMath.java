package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

/**
 * 连锁采集纯决策内核 (v79.61 架构批 3a C1: ChainHarvestExecute 抽取) —
 * 零 MC 依赖, 纯 JVM 可测 (错题 #174 铁律: MC 类型不进纯测试)。
 *
 * <p>抽取边界: 原 execute/tryStartVein/charge/vRange 内联的计算逻辑 (蓄力时长/
 * 耐久预算裁剪/消耗乘区/扫描垂直范围/扫描预算), 行为零变化; MC 编排 (扫描/寻路/破块/
 * 气泡) 保留在 ChainHarvestExecute。
 */
final class ChainHarvestMath {

    /** 扫描垂直范围下限 (±Y — 区块高 5 地下 5, 用户裁定) */
    private static final int V_RANGE_MIN = 5;
    /** 寻路预算系数 — BlockScanner 结果预算 = radius² / 16 (硬编码收敛) */
    private static final int SCAN_BUDGET_DIVISOR = 16;
    /** 木系垂直范围 — 树高无上限 (云杉/丛林 10-30 格, 原 ±6 树顶不可见) */
    private static final int WOOD_V_RANGE = 12;

    private ChainHarvestMath() {}

    /** 蓄力时长 (tick) — 脉块数 × 单块间隔 / 好感度效率乘区 */
    static long chargeTicks(int veinSize, int intervalTicks, double speedMultiplier) {
        return (long) (veinSize * intervalTicks / speedMultiplier);
    }

    /** 耐久预算裁剪后块数 — (剩余耐久-保留值) 与脉块数取小, 下限 0 (预算负 → 0) */
    static int durabilityCropSize(int veinSize, int remainingDurability, int reserve) {
        int budget = remainingDurability - reserve;
        return Math.min(veinSize, Math.max(0, budget));
    }

    /** 好感度消耗乘区后耐久消耗 — broken>0 时最低 1 点 (broken<=0 → 0) */
    static int durabilityCost(int broken, double costMultiplier) {
        if (broken <= 0) return 0;
        return Math.max(1, (int) (broken * costMultiplier));
    }

    /** 扫描垂直范围 — WOOD 12 / ORE max(V_RANGE_MIN, 挖穿深度) */
    static int vRange(boolean wood, int digDownDepth) {
        return wood ? WOOD_V_RANGE : Math.max(V_RANGE_MIN, digDownDepth);
    }

    /** 扫描结果预算 — radius² / SCAN_BUDGET_DIVISOR (BlockScanner 结果预算) */
    static int scanBudget(int radius) {
        return radius * radius / SCAN_BUDGET_DIVISOR;
    }
}
