package com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;

/**
 * 导航三件套 (v79.5) — navigateTo/arrived/keepAlive 样板收敛 (ArmTransfer/ChainHarvest 内联同款)。
 */
public final class NavigationUtil {

    private NavigationUtil() {}

    /** 设置导航目标 (NavigationMemory + Brain walk/look 记忆) */
    public static void navigateTo(EntityMaid maid, BlockPos target) {
        NavigationMemory.setNavTarget(maid, target);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, 1.0F, 2);
    }

    /** 到达判定 — 距目标中心 < 3 格 (distSqr < 9.0) */
    public static boolean arrived(EntityMaid maid, BlockPos target) {
        return target.distToCenterSqr(maid.position()) < 9.0;
    }

    /**
     * 原地心跳 — 刷新导航记忆 + 任务心跳 (防 TaskTickHandler 60s 超时看门狗杀活任务,
     * ChainHarvestExecute.keepAlive 同款)。长运行等待场景每 tick 调用。
     */
    public static void keepAlive(ServerLevel world, EntityMaid maid) {
        NavigationMemory.setNavTarget(maid, maid.blockPosition());
        NavigationMemory.setNavStartTick(maid, world.getGameTime());
        TaskStateManager.heartbeat(maid, world.getGameTime());
    }
}
