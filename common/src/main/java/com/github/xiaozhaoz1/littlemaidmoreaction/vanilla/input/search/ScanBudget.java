package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

/**
 * 全局每 tick 扫描预算池 (v77.5 移植自 Numen SearchBudget) — 所有女仆共享,
 * 防止多女仆同时扫描堆积 CPU (每女仆独立预算会随女仆数线性叠加)。
 *
 * <p>每 tick 重置 (tick 号 keyed — /tick freeze 不重置); 池内先到先得。
 * 毫秒上限为硬停 (wall-clock), 检查/扫描/区块加载为软计数。
 * 纯 JVM 可测 (tick 注入)。
 */
public final class ScanBudget {

    /** 候选存在性检查上限 */
    public static final int MAX_CHECKS_PER_TICK = 128;
    /** 16³ section 扫描上限 (一个 permit = 一个 section) */
    public static final int MAX_SECTION_SCANS_PER_TICK = 256;
    /** 区块强制加载上限 (昂贵) */
    public static final int MAX_CHUNK_LOADS_PER_TICK = 2;
    /** 毫秒硬停 (4ms ≈ 8% of 50ms tick) */
    public static final long MAX_NANOS_PER_TICK = 4_000_000L;

    private int poolChecks;
    private int poolSections;
    private int poolChunkLoads;
    private long deadlineNanos;
    private int budgetTick = -1;

    /** 每 tick 刷新 (tick 号变化才重置 — 冻结 tick 不重置) */
    public void refresh(int serverTick) {
        if (serverTick == budgetTick) return;
        budgetTick = serverTick;
        poolChecks = MAX_CHECKS_PER_TICK;
        poolSections = MAX_SECTION_SCANS_PER_TICK;
        poolChunkLoads = MAX_CHUNK_LOADS_PER_TICK;
        deadlineNanos = System.nanoTime() + MAX_NANOS_PER_TICK;
    }

    /** 显式重置 (测试注入) */
    public void resetForTick(int tick) {
        budgetTick = -1;
        refresh(tick);
    }

    /** 候选检查 — 消耗 1 池 (超时/池空 → false) */
    public boolean tryCheck() {
        if (poolChecks <= 0 || System.nanoTime() >= deadlineNanos) return false;
        poolChecks--;
        return true;
    }

    /** section 扫描 — 消耗 1 池 */
    public boolean trySectionScan() {
        if (poolSections <= 0 || System.nanoTime() >= deadlineNanos) return false;
        poolSections--;
        return true;
    }

    /** 区块强制加载 — 消耗 1 池 */
    public boolean tryChunkLoad() {
        if (poolChunkLoads <= 0 || System.nanoTime() >= deadlineNanos) return false;
        poolChunkLoads--;
        return true;
    }

    /** 测试钩子: 当前 tick 剩余 section 预算 */
    public int sectionsRemaining() { return Math.max(0, poolSections); }

    /** 全局单例 (女仆场景共享) */
    public static final ScanBudget GLOBAL = new ScanBudget();
}
