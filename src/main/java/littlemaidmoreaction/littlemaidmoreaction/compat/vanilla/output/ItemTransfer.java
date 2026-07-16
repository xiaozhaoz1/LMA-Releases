package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemHandlerHelper;
import java.util.function.Predicate;

public final class ItemTransfer {
    private static final System.Logger LOG = System.getLogger("LMA-V16-ItemTransfer");
    private ItemTransfer() {}

    public static ItemStack extractFromMaid(EntityMaid maid, Predicate<ItemStack> filter, int count) {
        if (count <= 0) return ItemStack.EMPTY;
        LOG.log(System.Logger.Level.INFO, "[V16] [ItemTransfer] extractFromMaid: filter test, count={0}", count);
        var inv = maid.getAvailableInv(true);
        ItemStack result = ItemStack.EMPTY;
        int remaining = count;
        for (int i = 0; i < inv.getSlots() && remaining > 0; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && filter.test(s)) {
                int take = Math.min(s.getCount(), remaining);
                ItemStack extracted = inv.extractItem(i, take, false);
                if (!extracted.isEmpty()) {
                    if (result.isEmpty()) { result = extracted; }
                    else { result.grow(extracted.getCount()); }
                    remaining -= take;
                }
            }
        }
        if (!result.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "[V16] [ItemTransfer] extracted: {0} x{1}", result.getItem(), result.getCount());
        }
        return result;
    }

    public static ItemStack extractFromBlock(BlockEntity be, int slot, int count) {
        if (be == null) return ItemStack.EMPTY;
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER)
            .map(handler -> handler.extractItem(slot, count, false))
            .orElse(ItemStack.EMPTY);
    }

    public static ItemStack insertIntoBlock(BlockEntity be, int slot, ItemStack stack) {
        if (be == null || stack.isEmpty()) return stack;
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER)
            .map(handler -> handler.insertItem(slot, stack, false))
            .orElse(stack);
    }

    public static ItemStack insertIntoMaid(EntityMaid maid, ItemStack stack) {
        if (stack.isEmpty()) return stack;
        return ItemHandlerHelper.insertItem(maid.getAvailableInv(false), stack, false);
    }
}
