package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.movement;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/** Brain 操作工具 — 消除直接 brain.setMemory/eraseMemory 调用。
 *  <p>v79.50b: isFrozen/clearWalkTarget/clearLookTarget/clearCantReachTarget 4 死方法删
 *  (全项目零引用实证); freeze/unfreeze 为 AnimExecute/HaqiPipeline 活调用保留。 */
public final class BrainHelper {
    private BrainHelper() {}

    /** 冻结女仆 AI — 清除寻路/攻击目标，设置恐慌 */
    public static void freeze(EntityMaid maid) {
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().setMemory(MemoryModuleType.IS_PANICKING, true);
        maid.setTarget(null);
    }
    /** 解除 AI 冻结 */
    public static void unfreeze(LivingEntity entity) {
        entity.getBrain().eraseMemory(MemoryModuleType.IS_PANICKING);
    }
}
