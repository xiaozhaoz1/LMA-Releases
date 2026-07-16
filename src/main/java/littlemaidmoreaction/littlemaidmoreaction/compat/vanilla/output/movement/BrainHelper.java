package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.movement;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/** Brain 操作工具 — 消除直接 brain.setMemory/eraseMemory 调用 */
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
    /** AI 是否被冻结 */
    public static boolean isFrozen(LivingEntity entity) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING);
    }
    /** 清除寻路目标 */
    public static void clearWalkTarget(EntityMaid maid) {
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
    /** 清除视线目标 */
    public static void clearLookTarget(EntityMaid maid) {
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }
    /** ★ v35.1: 清除 CANT_REACH_WALK_TARGET — 恢复 AI 移动能力 */
    public static void clearCantReachTarget(EntityMaid maid) {
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }
}
