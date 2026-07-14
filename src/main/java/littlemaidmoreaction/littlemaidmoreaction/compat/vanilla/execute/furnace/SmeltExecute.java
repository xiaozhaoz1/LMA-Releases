package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.furnace;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ItemResolver;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.SlotLayout;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.BlockSearch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/** v30: SlotLayout + ItemsUtil */
public final class SmeltExecute {
    private SmeltExecute() {}

    public static boolean execute(ServerLevel world, EntityMaid maid, String inputItemId,
                                   String fuelItemId, int range, SlotLayout slots) {
        Item toSmelt = ItemResolver.resolve(inputItemId);
        if (toSmelt == null) return false;
        Item fuel = fuelItemId.isEmpty() ? null : ItemResolver.resolve(fuelItemId);

        var results = BlockSearch.findBlocks(world, maid.blockPosition(), range, 4,
            (pos, state) -> world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity);
        if (results.isEmpty()) return false;

        var te = world.getBlockEntity(results.get(0).pos());
        if (!(te instanceof AbstractFurnaceBlockEntity furnace)) return false;
        var handler = te.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) return false;

        var inv = maid.getAvailableInv(false);
        int inSlot = slots.slot("input");
        int fuelSlot = slots.slot("fuel");

        // 1. 加输入
        var inputSlotStack = handler.getStackInSlot(inSlot);
        if (inputSlotStack.isEmpty() || inputSlotStack.is(toSmelt)) {
            int s = ItemsUtil.findStackSlot(inv, stack -> stack.is(toSmelt));
            if (s >= 0) {
                var taken = inv.extractItem(s, 1, false);
                var leftover = handler.insertItem(inSlot, taken, false);
                if (!leftover.isEmpty()) inv.insertItem(s, leftover, false);
            }
        }

        // 2. 加燃料
        var fuelSlotStack = handler.getStackInSlot(fuelSlot);
        if (fuelSlotStack.isEmpty() || fuelSlotStack.getCount() < 4) {
            int s = ItemsUtil.findStackSlot(inv, stack ->
                stack.getBurnTime(RecipeType.SMELTING) > 0
                    && (fuel == null || stack.is(fuel))
                    && !stack.is(toSmelt));
            if (s >= 0) {
                var taken = inv.extractItem(s, 1, false);
                var leftover = handler.insertItem(fuelSlot, taken, false);
                if (!leftover.isEmpty()) inv.insertItem(s, leftover, false);
            }
        }

        maid.getPersistentData().putLong("lma_flow_tick", world.getGameTime());
        return true;
    }
}
