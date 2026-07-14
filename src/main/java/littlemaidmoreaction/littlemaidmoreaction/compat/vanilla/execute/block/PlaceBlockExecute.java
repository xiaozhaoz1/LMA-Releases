package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ItemResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.DirectionalPlaceContext;

/** 方块放置 — 从女仆背包找方块物品放置到指定位置 */
public final class PlaceBlockExecute {
    private PlaceBlockExecute() {}

    public static boolean execute(ServerLevel world, EntityMaid maid, String blockId, int ox, int oy, int oz) {
        Item item = ItemResolver.resolve(blockId);
        if (!(item instanceof BlockItem blockItem)) return false;

        BlockPos pos = maid.blockPosition().offset(ox, oy, oz);
        var inv = maid.getAvailableInv(false);

        for (int i = 0; i < inv.getSlots(); i++) {
            var stack = inv.getStackInSlot(i);
            if (stack.getItem() == blockItem) {
                // 找可放置位置
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
