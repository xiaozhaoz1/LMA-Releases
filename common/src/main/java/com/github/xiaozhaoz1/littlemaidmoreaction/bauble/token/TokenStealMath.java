package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.token;

/**
 * 女仆「偷吃 Token」的**判定纯函数** (无 MC 依赖 ⇒ 可单测 ✓)。
 *
 * <p>用户裁定 (2026-09-20):
 * <ul>
 *   <li>每 **6000t** 掷一次 **5%** 概率; <b>计时从"上一次概率判定"之后起算</b> ⇒
 *       cd 一转完就**立即判定** (不是"命中后才开始 cd") ✓</li>
 *   <li>必须**主人 8 格内**才偷 ✓; 女仆背包**放不下就不偷** (原子) ✓</li>
 * </ul>
 *
 * <p>本类只做"该不该判 / 中不中 / 够不够近"三件纯计算 —— 物品搬运动作在
 * {@link TokenStealService} (需世界/实体) ✓。
 */
public final class TokenStealMath {

    private TokenStealMath() {}

    /**
     * 冷却是否已过 (用户裁定: **从上次判定起算** ✓)。
     *
     * @param lastAttemptTick 上次判定 tick; {@link Long#MIN_VALUE} = 从未判定过 ⇒ 立即可判 ✓
     * @param nowTick         当前 tick
     * @param interval        间隔 tick (6000); ≤0 ⇒ 视为无冷却 (调试/特殊配置) ✓
     */
    public static boolean cooldownElapsed(long lastAttemptTick, long nowTick, int interval) {
        if (interval <= 0) return true;
        if (lastAttemptTick == Long.MIN_VALUE) return true;   // 从未判定 ⇒ 第一次心跳即可判 ✓
        return nowTick - lastAttemptTick >= interval;
    }

    /**
     * 概率是否命中。
     *
     * @param chance 概率 (0.05 = 5%); ≤0 ⇒ 永不命中; ≥1 ⇒ 必中
     * @param roll   随机值 ∈ [0,1) (调用方传 {@code maid.getRandom().nextFloat()})
     */
    public static boolean rollHit(float chance, float roll) {
        if (chance <= 0f) return false;
        if (chance >= 1f) return true;
        return roll < chance;
    }

    /**
     * 距离门控 (用户裁定: **8 格内** ✓) — 传**平方距离**免开方 ✓。
     *
     * @param distSqr   实体间平方距离 ({@code maid.distanceToSqr(player)})
     * @param maxBlocks 最大格数 (8.0)
     */
    public static boolean withinRange(double distSqr, double maxBlocks) {
        if (maxBlocks < 0) return false;
        return distSqr <= maxBlocks * maxBlocks;
    }
}
