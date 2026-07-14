package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.FurnaceSlotMapping;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.bell.BellExecute;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.craft.CraftExecute;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.furnace.FurnaceExecute;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.jukebox.JukeboxExecute;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** v29.1: compat/vanilla 统一任务入口 */
public final class VanillaTasks {
    private VanillaTasks() {}

    public static boolean craft(ServerLevel world, EntityMaid maid, BlockPos pos, String target) {
        return CraftExecute.execute(world, maid, pos, target);
    }
    public static boolean furnace(ServerLevel world, EntityMaid maid, BlockPos pos, String inputItemId,
                                   FurnaceSlotMapping slots) {
        return FurnaceExecute.execute(world, maid, pos, inputItemId, slots);
    }
    public static boolean jukebox(ServerLevel world, EntityMaid maid, BlockPos pos, String target) {
        return JukeboxExecute.execute(world, maid, pos, target);
    }
    public static boolean bell(ServerLevel world, EntityMaid maid, BlockPos pos) {
        return BellExecute.execute(world, maid, pos);
    }
}
