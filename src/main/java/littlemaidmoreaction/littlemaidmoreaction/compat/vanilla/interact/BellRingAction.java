package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * 女仆敲钟互动 (v12.3 — +视觉反馈+任务完成)。
 *
 * <p>女仆走到钟旁 → 挥臂敲击 → 播放钟声 → 标记任务完成。</p>
 */
@RuleAction
public class BellRingAction extends AbstractFunctionalBlockInteraction {

    @Override
    public String id() { return "bell_ring"; }

    @Override
    public String displayName() { return "敲钟"; }

    @Override
    protected String defaultBlockId() { return "minecraft:bell"; }

    @Override
    protected List<String> validActions() {
        return List.of("ring");
    }

    @Override
    protected void doInteract(BlockPos pos, BlockState state, EntityMaid maid,
                               Map<String, String> params, String action) {
        if (!(state.getBlock() instanceof BellBlock bellBlock)) return;

        Direction facing = maid.getDirection();
        Direction hitDir = (facing.getAxis() != Direction.Axis.Y) ? facing : Direction.NORTH;

        boolean rang = bellBlock.attemptToRing(maid, maid.level(), pos, hitDir);
        if (!rang) {
            bellBlock.attemptToRing(maid.level(), pos, null);
        }

        // 挥臂 + 音效 + 标记完成
        playInteractionFeedback(maid, pos, SoundEvents.BELL_BLOCK);
        completeFlowTask(maid);
    }
}
