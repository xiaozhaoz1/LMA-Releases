package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * 节流/冷却工具 (v79.40) — 统一时间戳节流, 当 CD 间隔用只需设置一个数字。
 *
 * <p>收编 4 处手写节流 (MaidEmojiApi / BellRingPipeline / EnvSenseBroadcaster /
 * ChainHarvestExecute): 每处手写 {@code last != 0 && now - last < interval} + 防溢出。
 * 键: PD 根键 {@code lma_throttle_<key>} (自过期语义 — 残留无害)。
 * 防溢出: {@code last > now} (时钟回绕/跨 session) 视为过期放行。
 */
public final class ThrottleUtil {

    private static final String PREFIX = "lma_throttle_";

    private ThrottleUtil() {}

    /**
     * 是否已过冷却间隔 (放行则更新时间戳)。
     *
     * @param key           节流标识 (如 "emoji" / "bell_ring" / "chain_bubble")
     * @param intervalTicks 冷却间隔 (tick); 传间隔数字即当 CD 用
     * @return true = 已过间隔 (本次调用标记, 下次从此刻起算); false = 冷却中
     */
    public static boolean shouldFire(EntityMaid maid, String key, long intervalTicks) {
        long now = maid.level().getGameTime();
        long last = maid.getPersistentData().getLong(PREFIX + key);
        // v79.49: 判定委托 ThrottleMath 纯函数 (零行为变化, JVM 可测)
        if (ThrottleMath.isCoolingDown(now, last, intervalTicks)) {
            return false;  // 冷却中
        }
        maid.getPersistentData().putLong(PREFIX + key, now);
        return true;
    }

    /** 剩余冷却 tick (0 = 可放行) — 供显示/CD 提示 */
    public static long cooldownRemaining(EntityMaid maid, String key, long intervalTicks) {
        long now = maid.level().getGameTime();
        long last = maid.getPersistentData().getLong(PREFIX + key);
        return ThrottleMath.cooldownRemaining(now, last, intervalTicks);
    }
}
