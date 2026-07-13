package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.bell;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;

/** v23: 敲钟编排 */
public final class BellExecute {
    private BellExecute() {}

    public static void execute(ServerLevel world, EntityMaid maid, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BellBlock bell)) return;
        bell.attemptToRing(maid, world, pos, null);
        world.playSound(null, pos, SoundEvents.BELL_BLOCK,
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
