package littlemaidmoreaction.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.task.pipeline.BlockInteractPipeline;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.fakeplayer.FakePlayerInteract;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import littlemaidmoreaction.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 女仆右键交互服务 — 对目标方块执行右键模拟。
 *
 * <p>距离检查(5格) → 方块存在检查 → {@link FakePlayerInteract} 模拟右键。
 * 方块被破坏时自动发气泡提示并清除绑定。
 */
public final class BlockInteractService {

    private BlockInteractService() {}

    /**
     * 对目标方块执行右键交互。
     *
     * @return true=交互成功, false=方块不存在/太远/交互失败
     */
    public static boolean interact(ServerLevel world, EntityMaid maid, BlockPos pos) {
        // ① 距离检查 (v67.2: Cloth Config 配置)
        if (!pos.closerToCenterThan(maid.position(), ActiveTaskConfig.BI_INTERACT_DISTANCE.get())) return false;

        // ② 方块存在检查 — 被破坏则气泡提示 + 清除绑定
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            maid.getChatBubbleManager().addTextChatBubble("❌ 绑定方块已丢失");
            TaskConfigs.get(maid, "block_interact").remove(BlockInteractPipeline.KEY_POS);
            return false;
        }

        // ③ 模拟右键 (公共样板见 FakePlayerInteract)
        return FakePlayerInteract.rightClick(world, maid, pos, Direction.UP);
    }
}
