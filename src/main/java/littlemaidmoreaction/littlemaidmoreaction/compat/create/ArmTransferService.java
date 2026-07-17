package littlemaidmoreaction.littlemaidmoreaction.compat.create;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 机械臂搬运服务 — 原子化 input→compute→output 三步模式。
 */
public final class ArmTransferService {
    private ArmTransferService() {}

    // ── Input ──

    public static ItemStack readSourceItem(EntityMaid maid, BlockPos sourcePos) {
        IItemHandler handler = getHandler(maid, sourcePos);
        if (handler == null) return ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack readMaidItem(EntityMaid maid) {
        var inv = maid.getAvailableInv(false);
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty()) return stack.copy();
        }
        return ItemStack.EMPTY;
    }

    // ── Compute ──

    public static int computeExtractCount(EntityMaid maid, ItemStack sourceItem) {
        if (sourceItem.isEmpty()) return 0;
        int maxTake = Math.min(sourceItem.getCount(), sourceItem.getMaxStackSize());
        var inv = maid.getAvailableInv(false);
        int totalSpace = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot.isEmpty()) { totalSpace += sourceItem.getMaxStackSize(); }
            else if (ItemStack.isSameItemSameTags(slot, sourceItem)) {
                totalSpace += slot.getMaxStackSize() - slot.getCount();
            }
        }
        return Math.min(maxTake, Math.max(totalSpace, 0));
    }

    public static int computeDepositCount(EntityMaid maid, BlockPos targetPos, ItemStack maidItem) {
        if (maidItem.isEmpty()) return 0;
        IItemHandler handler = getHandler(maid, targetPos);
        if (handler == null) return 0;
        ItemStack remainder = ItemHandlerHelper.insertItem(handler, maidItem.copy(), true);
        return maidItem.getCount() - remainder.getCount();
    }

    // ── Output ──

    /** 从源容器提取物品到女仆背包, 返回实际提取数 */
    public static int executeExtract(EntityMaid maid, BlockPos sourcePos, ItemStack item, int count) {
        IItemHandler handler = getHandler(maid, sourcePos);
        if (handler == null) return 0;
        var maidInv = maid.getAvailableInv(false);
        int actual = 0;
        for (int slot = 0; slot < handler.getSlots() && actual < count; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!ItemStack.isSameItemSameTags(stack, item)) continue;
            ItemStack extracted = handler.extractItem(slot, Math.min(count - actual, stack.getCount()), false);
            if (extracted.isEmpty()) continue;
            int before = extracted.getCount();
            ItemStack leftover = ItemHandlerHelper.insertItem(maidInv, extracted, false);
            int after = leftover.getCount();
            actual += (before - after);
            if (after > 0) handler.insertItem(slot, leftover, false);
        }
        return actual;
    }

    /** 从女仆背包存入目标容器, 返回实际存入数 */
    public static int executeDeposit(EntityMaid maid, BlockPos targetPos, ItemStack item, int count) {
        IItemHandler handler = getHandler(maid, targetPos);
        if (handler == null) return 0;
        var inv = maid.getAvailableInv(false);
        int actual = 0;
        for (int slot = 0; slot < inv.getSlots() && actual < count; slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!ItemStack.isSameItemSameTags(stack, item)) continue;
            int take = Math.min(count - actual, stack.getCount());
            ItemStack taken = inv.extractItem(slot, take, false);
            if (taken.isEmpty()) continue;
            int before = taken.getCount();
            ItemStack leftover = ItemHandlerHelper.insertItem(handler, taken, false);
            int after = leftover.getCount();
            actual += (before - after);
            if (after > 0) inv.insertItem(slot, leftover, false);
        }
        return actual;
    }

    // ── Query ──

    public static boolean isSourceEmpty(EntityMaid maid, BlockPos pos) {
        return readSourceItem(maid, pos).isEmpty();
    }

    public static boolean hasInventorySpace(EntityMaid maid) {
        var inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    public static boolean isValidContainer(EntityMaid maid, BlockPos pos) {
        if (pos == null || !maid.isWithinRestriction(pos)) return false;
        return getHandler(maid, pos) != null;
    }

    private static IItemHandler getHandler(EntityMaid maid, BlockPos pos) {
        if (pos == null) return null;
        BlockEntity be = maid.level().getBlockEntity(pos);
        if (be == null) return null;
        for (var dir : Direction.values()) {
            var handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir).resolve().orElse(null);
            if (handler != null) return handler;
        }
        return null;
    }
}
