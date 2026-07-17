package littlemaidmoreaction.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import littlemaidmoreaction.littlemaidmoreaction.api.ItemResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.DirectionalPlaceContext;

/** v30: ItemsUtil 替代手写遍历 */
public final class PlaceBlockExecute {
    private PlaceBlockExecute() {}

    public static boolean execute(ServerLevel world, EntityMaid maid, String blockId, int ox, int oy, int oz) {
        Item item = ItemResolver.resolve(blockId);
        if (!(item instanceof BlockItem blockItem)) return false;

        BlockPos pos = maid.blockPosition().offset(ox, oy, oz);
        var inv = maid.getAvailableInv(false);
        int slot = ItemsUtil.findStackSlot(inv, s -> s.getItem() == blockItem);
        if (slot < 0) return false;

        var stack = inv.getStackInSlot(slot);
        if (!world.getBlockState(pos).isAir()) {
            BlockPos alt = findAirNeighbor(world, pos);
            if (alt == null) return false;
            pos = alt;
        }
        var ctx = new DirectionalPlaceContext(world, pos.below(), Direction.UP, stack, Direction.UP);
        var result = blockItem.place(ctx);
        if (result.consumesAction()) {
            stack.shrink(1);
            return true;
        }
        return false;
    }

    private static BlockPos findAirNeighbor(ServerLevel world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos p = pos.relative(dir);
            if (world.getBlockState(p).isAir()) return p;
        }
        return null;
    }
}
