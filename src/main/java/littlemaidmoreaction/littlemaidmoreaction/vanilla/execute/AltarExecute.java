package littlemaidmoreaction.littlemaidmoreaction.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import littlemaidmoreaction.littlemaidmoreaction.api.ItemResolver;
import littlemaidmoreaction.littlemaidmoreaction.api.VanillaConstants;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.altar.AltarOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/** v30: ItemsUtil 替代手写遍历 */
public final class AltarExecute {
    private AltarExecute() {}

    public static boolean execute(ServerLevel world, EntityMaid maid, String itemId, int range) {
        var item = ItemResolver.resolve(itemId);
        if (item == null) return false;

        List<TileEntityAltar> structures = findAltars(maid, range);
        if (structures.isEmpty()) return false;

        int totalPlaced = 0;
        for (TileEntityAltar ref : structures) {
            ItemStack stack = ItemsUtil.getStack(maid, s -> s.is(item));
            if (stack.isEmpty()) break;

            int placed = AltarOutput.placeItems(ref, maid, stack);
            if (placed > 0) {
                totalPlaced += placed;
                AltarOutput.tryTriggerCraft(maid, ref);
            }
        }
        return totalPlaced > 0;
    }

    private static List<TileEntityAltar> findAltars(EntityMaid maid, int range) {
        BlockPos center = maid.blockPosition();
        List<TileEntityAltar> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-range, -VanillaConstants.SEARCH_VERTICAL, -range), center.offset(range, VanillaConstants.SEARCH_VERTICAL, range))) {
            BlockEntity te = maid.level().getBlockEntity(pos);
            if (te instanceof TileEntityAltar altar && altar.isCanPlaceItem()) {
                int hash = System.identityHashCode(altar.getCanPlaceItemPosList());
                if (seen.add(hash)) result.add(altar);
            }
        }
        result.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(center), b.getBlockPos().distSqr(center)));
        return result;
    }
}
