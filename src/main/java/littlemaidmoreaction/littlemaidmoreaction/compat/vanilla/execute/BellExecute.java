package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;

/** v29: 敲钟编排 */
public final class BellExecute {
    private BellExecute() {}

    /** @return true if bell was rung */
    public static boolean execute(ServerLevel world, EntityMaid maid, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BellBlock bell)) return false;
        bell.attemptToRing(maid, world, pos, null);
        world.playSound(null, pos, SoundEvents.BELL_BLOCK,
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }
}
