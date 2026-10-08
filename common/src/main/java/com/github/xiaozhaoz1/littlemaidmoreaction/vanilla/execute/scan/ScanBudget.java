package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.scan;

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

    /**
     * 测试钩子 (2026-09-21) — 覆写本 tick 的**墙钟死线**。
     *
     * <p><b>⚠ 仅测试用; 生产路径不得调用</b> (生产死线一律由 {@link #refresh(int)} 写 `now + MAX_NANOS_PER_TICK`)。
     * 用途: 让单测能把"池计数"与"墙钟门"**分开确定性验证** —
     * 传 {@code Long.MAX_VALUE} = 不设死线(纯池计数); 传"已过期值" = 只验时间门。
     *
     * <p><b>调用时序</b>: 必须在 {@link #resetForTick(int)} / {@link #refresh(int)} **之后**调用,
     * 否则会被其覆写 (二者都会重写 `deadlineNanos`)。若将来 `refresh` 逻辑改为不写 `deadlineNanos`, 需复查本钩子。
     *
     * <p>为什么需要: 原 `while (trySectionScan())` 同时受两条门影响 ⇒ 繁忙机器上 256 次跨 4ms 即早停
     * (`expected: <256> but was: <245>`, 见 build-logs/PLAN-scanbudget-flake.md)。
     */
    void overrideDeadlineForTest(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
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
