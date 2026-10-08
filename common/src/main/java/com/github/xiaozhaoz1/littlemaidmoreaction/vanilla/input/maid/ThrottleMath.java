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

    /**
     * **同一气泡分级冷却** (用户裁定 2026-09-14): 第 1 次后 5 秒, 第 2 次后 10 秒, 第 3 次起 30 秒循环。
     *
     * @param repeat 已显示次数 (1 = 刚显示第 1 次) — &lt;1 按 1 处理, &gt;3 按 3 处理
     * @return 下次允许显示前需等待的 tick (100 / 200 / 600)
     */
    public static long bubbleInterval(int repeat) {
        if (repeat <= 1) return 100L;
        return repeat == 2 ? 200L : 600L;
    }
}
