package com.github.xiaozhaoz1.littlemaidmoreaction.task.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

/**
 * v79.62 空闲可爱动作 (借鉴 soulcraft-mcbot choosePlayerReaction) —
 * 主人 8 格内 idle 时随机做: 挥手 / 歪头 (看主人旁边) / 回头 (看主人)。
 *
 * <p>Core 行为 (DefaultBehaviorBrain 注册, 优先级 15); 冷却 8-20s 随机 (start 里重新节流)。
 * 防御点: 主人为 null 不动作; 无任何 PD 写入 (无泄漏)。
 */
public class MaidCuteIdleBehavior extends MaidCheckRateTask {

    private static final Random RAND = new Random();
    private static final double MASTER_RANGE = 8.0;

    public MaidCuteIdleBehavior() {
        super(java.util.Collections.emptyMap());
        setMaxCheckRate(160 + RAND.nextInt(240));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!super.checkExtraStartConditions(level, maid)) return false;
        LivingEntity owner = maid.getOwner();
        return owner != null && maid.distanceToSqr(owner) <= MASTER_RANGE * MASTER_RANGE;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity owner = maid.getOwner();
        switch (RAND.nextInt(3)) {
            case 0: // 挥手
                maid.swing(InteractionHand.MAIN_HAND);
                break;
            case 1: // 歪头 — 看主人旁边随机位置
                if (owner != null) {
                    double ox = owner.getX() + (RAND.nextDouble() - 0.5) * 3;
                    double oy = owner.getEyeY() + (RAND.nextDouble() - 0.5) * 1.5;
                    double oz = owner.getZ() + (RAND.nextDouble() - 0.5) * 3;
                    maid.getLookControl().setLookAt(ox, oy, oz, 10f, 10f);
                }
                break;
            case 2: // 回头 — 看主人
                if (owner != null) maid.getLookControl().setLookAt(owner, 15f, 15f);
                break;
        }
        setMaxCheckRate(160 + RAND.nextInt(240));
    }
}

