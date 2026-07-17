package littlemaidmoreaction.littlemaidmoreaction.vanilla;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.SlotLayout;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.execute.BellExecute;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.execute.CraftExecute;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.execute.FurnaceExecute;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.execute.JukeboxExecute;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** v30: compat/vanilla 统一任务入口 */
public final class VanillaTasks {
    private VanillaTasks() {}

    public static boolean craft(ServerLevel world, EntityMaid maid, BlockPos pos, String target) {
        return CraftExecute.execute(world, maid, pos, target);
    }
    public static boolean furnace(ServerLevel world, EntityMaid maid, BlockPos pos, String inputItemId,
                                   SlotLayout slots) {
        return FurnaceExecute.execute(world, maid, pos, inputItemId, slots);
    }
    public static boolean jukebox(ServerLevel world, EntityMaid maid, BlockPos pos, String target) {
        return JukeboxExecute.execute(world, maid, pos, target);
    }
    public static boolean bell(ServerLevel world, EntityMaid maid, BlockPos pos) {
        return BellExecute.execute(world, maid, pos);
    }
}
