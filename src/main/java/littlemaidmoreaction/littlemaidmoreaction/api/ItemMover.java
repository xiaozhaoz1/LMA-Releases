package littlemaidmoreaction.littlemaidmoreaction.api;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.function.Predicate;

/**
 * 物品转移原语 — 安全提取/插入/跨 handler 转移。
 */
public final class ItemMover {
    private ItemMover() {}

    public static ItemStack tryExtract(IItemHandler handler, int slot, int count) {
        if (handler == null || count <= 0 || slot < 0 || slot >= handler.getSlots())
            return ItemStack.EMPTY;
        return handler.extractItem(slot, count, false);
    }

    public static ItemStack tryInsert(IItemHandler handler, ItemStack stack) {
        if (handler == null || stack.isEmpty()) return stack;
        ItemStack remaining = stack.copy();
        for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
            remaining = handler.insertItem(i, remaining, false);
        }
        return remaining;
    }

    /**
     * 从 source 向 target 转移物品。插入失败自动回退。
     * @return 实际转移的数量
     */
    public static int transfer(IItemHandler source, IItemHandler target,
                               Predicate<ItemStack> filter, int maxCount) {
        if (source == null || target == null || maxCount <= 0) return 0;
        int transferred = 0;
        for (int i = 0; i < source.getSlots() && transferred < maxCount; i++) {
            ItemStack inSlot = source.getStackInSlot(i);
            if (inSlot.isEmpty() || !filter.test(inSlot)) continue;
            int canTake = Math.min(maxCount - transferred, inSlot.getCount());
            ItemStack taken = tryExtract(source, i, canTake);
            if (taken.isEmpty()) continue;
            ItemStack remainder = tryInsert(target, taken);
            if (!remainder.isEmpty()) {
                ItemStack leftover = source.insertItem(i, remainder, false);
                if (!leftover.isEmpty()) {
                    ItemStack stillLeft = tryInsert(source, leftover);
                    if (!stillLeft.isEmpty()) {
                        LittleMaidMoreAction.LOGGER.warn(
                            "[ItemMover] rollback failed: {} {} items lost (slot {} → target)",
                            stillLeft.getCount(), stillLeft.getHoverName().getString(), i);
                    }
                }
                transferred += (taken.getCount() - remainder.getCount());
            } else {
                transferred += taken.getCount();
            }
        }
        return transferred;
    }
}
