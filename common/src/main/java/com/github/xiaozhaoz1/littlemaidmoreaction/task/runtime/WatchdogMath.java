package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

/**
 * 看门狗纯函数 (v79) — 从 TaskTickHandler 内联逻辑提取, 纯 JVM 可测。
 *
 * <p>语义 (v64 迁自 TaskEngine, 原样保留):
 * <ul>
 *   <li>{@code lastTick == 0} — 未初始化, 永不超时</li>
 *   <li>{@code lastTick > now} — 时钟回绕/跨 session 残留; 由 {@link #isStale} 单独判定
 *       (小偏差跳过 — 防溢出, 原 continue 语义), 大偏差视为过期 → 清理</li>
 *   <li>正常路径 — {@code now - lastTick > timeout + tolerance} 判超时; tolerance 补偿
 *       心跳节流 (v79: FLOW_TICK 每 {@link GameTickPipelineManager#HEARTBEAT_INTERVAL} tick
 *       一写, 有效超时上浮 ≤ 心跳间隔)</li>
 * </ul>
 */
public final class WatchdogMath {

    /** 跨 session 时间戳偏差上限 (1.728M tick = 24 小时) — 超过视为过期残留 */
    public static final long MAX_SKEW = 1_728_000L;

    private WatchdogMath() {}

    /** 是否超时 — lastTick==0 未初始化 / lastTick>now 防溢出均 false */
    public static boolean isTimedOut(long now, long lastTick, int timeout, int tolerance) {
        if (lastTick == 0) return false;
        if (lastTick > now) return false;
        return now - lastTick > timeout + tolerance;
    }

    /** 是否过期残留 (lastTick 在未来且偏差超上限 — 跨 session 时钟漂移) */
    public static boolean isStale(long now, long lastTick) {
        return lastTick > now && lastTick - now > MAX_SKEW;
    }
}
