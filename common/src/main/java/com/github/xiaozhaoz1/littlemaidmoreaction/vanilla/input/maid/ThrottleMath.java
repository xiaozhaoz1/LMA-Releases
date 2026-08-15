package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid;

/**
 * 节流判定纯函数 (v79.49 抽离 — WatchdogMath 同款模式) — 零 MC 依赖, JVM 可测。
 *
 * <p>从 ThrottleUtil 抽出 (原方法签名含 EntityMaid — 不可纯 JVM 测);
 * 语义与 ThrottleUtil 原实现等价 (零行为变化)。
 */
public final class ThrottleMath {

    private ThrottleMath() {}

    /**
     * 是否冷却中 — last==0 (未标记) 或时钟回退 (last &gt; now, 跨 session/回绕) → 放行。
     * 与 ThrottleUtil.shouldFire 原判定逐字等价。
     */
    public static boolean isCoolingDown(long now, long last, long interval) {
        return last != 0 && last <= now && now - last < interval;
    }

    /** 剩余冷却 tick (0 = 可放行) — 未标记/时钟回退 → 0 */
    public static long cooldownRemaining(long now, long last, long interval) {
        if (last == 0 || last > now) return 0;
        return Math.max(0, interval - (now - last));
    }
}
