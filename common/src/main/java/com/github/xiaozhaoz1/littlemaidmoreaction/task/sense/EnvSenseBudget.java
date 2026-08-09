package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

/**
 * 广播 pass 墙钟预算 (v79.3 D3) — 多女仆并发扫描成本叠加防冻安全网。
 *
 * <p>超限跳过快照刷新 (边沿只延后不误报 — PREV 保留旧快照, 下轮对比仍正确)。
 * 时钟注入 (now 参数化) 纯 JVM 可测。ScanJob 路径已有 ScanBudget 4ms 硬停, 不重复。
 */
public final class EnvSenseBudget {

    /** 单次广播 pass 墙钟上限 (8ms — 200t 周期摊销 &lt; 0.04ms/tick) */
    public static final long DEFAULT_MAX_NANOS = 8_000_000L;

    /** pass 句柄 — deadlineNanos 绝对时刻 */
    public record Pass(long deadlineNanos) {
        /** 是否超限 (now 注入可测) */
        public boolean exhausted(long nowNanos) {
            return nowNanos > deadlineNanos;
        }
    }

    private EnvSenseBudget() {}

    /** 开启一个预算 pass */
    public static Pass begin(long nowNanos, long maxNanos) {
        return new Pass(nowNanos + maxNanos);
    }
}
