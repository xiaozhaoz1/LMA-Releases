package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.fakeplayer.FakePlayerInteract;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 女仆全局右键门面 (v79.38.2) — 距离检查 + 模拟右键。
 * 供所有右键互动需求使用: BlockInteractPipeline / AI 工具 interact_block / 未来功能。
 * 绑定方块丢失处理 (气泡 + 清绑定) 是 block_interact 任务语义 → 在管线内。
 */
public final class BlockInteractService {

    private BlockInteractService() {}

    /**
     * 对目标方块执行右键交互。
     *
     * @return true=交互成功, false=距离外/交互失败 (方块存在性由调用方按语义处理)
     */
    public static boolean interact(ServerLevel world, EntityMaid maid, BlockPos pos) {
        // ① 距离检查 (Cloth Config 配置)
        if (!pos.closerToCenterThan(maid.position(), ActiveTaskConfig.BI_INTERACT_DISTANCE.get())) return false;

        // ② 模拟右键 (公共样板见 FakePlayerInteract)
        return FakePlayerInteract.rightClick(world, maid, pos, Direction.UP);
    }
}
