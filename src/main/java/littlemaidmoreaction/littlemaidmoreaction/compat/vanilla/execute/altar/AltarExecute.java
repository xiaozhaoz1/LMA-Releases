package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.altar;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ItemResolver;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.altar.AltarOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/** 祭坛物品放置 — 委托 PlaceAltarItemAction */
public final class AltarExecute {
    private AltarExecute() {}

    public static boolean execute(ServerLevel world, EntityMaid maid, String itemId, int range) {
        var item = ItemResolver.resolve(itemId);
        if (item == null) return false;

        List<TileEntityAltar> structures = findAltars(maid, range);
        if (structures.isEmpty()) return false;

        int totalPlaced = 0;
        for (TileEntityAltar ref : structures) {
            ItemStack stack = findItem(maid, itemId);
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
                center.offset(-range, -4, -range), center.offset(range, 4, range))) {
            BlockEntity te = maid.level().getBlockEntity(pos);
            if (te instanceof TileEntityAltar altar && altar.isCanPlaceItem()) {
                int hash = altar.getCanPlaceItemPosList().getData().hashCode();
                if (seen.add(hash)) result.add(altar);
            }
        }
        result.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(center), b.getBlockPos().distSqr(center)));
        return result;
    }

    private static ItemStack findItem(EntityMaid maid, String itemId) {
        var item = ItemResolver.resolve(itemId);
        if (item == null) return ItemStack.EMPTY;
        var inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            var stack = inv.getStackInSlot(i);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }
}
