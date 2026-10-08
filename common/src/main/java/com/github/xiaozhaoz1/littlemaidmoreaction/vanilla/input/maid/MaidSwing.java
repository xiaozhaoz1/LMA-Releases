package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;

/**
 * 女仆挥臂动画原语 (v79.61x 重复抽取 — vanilla/input/maid IO 原语, 无业务语义)。
 *
 * <p>收敛 6 处同形 {@code world.getGameTime() % interval == 0 → maid.swing(MAIN_HAND)}
 * 节拍摆动 (ArmTransfer 取/放 ×2 / Crank / Power / Mix / RunningBelt), 摆动节奏单点定义。
 */
public final class MaidSwing {

    private MaidSwing() {}

    /** 当前游戏 tick 命中节拍 (间隔 interval) → 挥主手; 否则不动 */
    public static void onInterval(EntityMaid maid, int intervalTicks) {
        long now = maid.level() != null ? maid.level().getGameTime() : 0;
        if (now % intervalTicks == 0) {
            maid.swing(InteractionHand.MAIN_HAND);
        }
    }
}
