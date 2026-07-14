package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.function.Predicate;

/**
 * 物品转移原语 — 安全提取/插入/跨 handler 转移。
 * 所有方法返回结果而非抛异常；调用方检查返回值。
 *
 * <p>使用约定:
 * <pre>{@code
 * ItemStack taken = ItemMover.tryExtract(source, slot, count);
 * ItemStack remainder = ItemMover.tryInsert(target, taken);
 * if (!remainder.isEmpty()) ItemMover.tryInsert(source, remainder); // rollback
 * }</pre>
 */
public final class ItemMover {
    private ItemMover() {}

    /**
     * 从 handler 指定槽位提取物品。
     *
     * @return 实际提取的 ItemStack (empty=失败/无物品)
     */
    public static ItemStack tryExtract(IItemHandler handler, int slot, int count) {
        if (handler == null || count <= 0 || slot < 0 || slot >= handler.getSlots())
            return ItemStack.EMPTY;
        return handler.extractItem(slot, count, false);
    }

    /**
     * 将 stack 插入 handler，遍历所有槽位。
     *
     * @return 未插入的剩余部分 (empty=全部成功)
     */
    public static ItemStack tryInsert(IItemHandler handler, ItemStack stack) {
        if (handler == null || stack.isEmpty()) return stack;
        ItemStack remaining = stack.copy();
        for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
            remaining = handler.insertItem(i, remaining, false);
        }
        return remaining;
    }

    /**
     * 从 source 向 target 转移最多 maxCount 个满足 filter 的物品。
     * 逐个槽位提取→插入；插入失败自动回退到原槽位(或任意空槽)。
     *
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
                // 回退: 先尝试原槽位, 满了则任意空槽
                ItemStack leftover = source.insertItem(i, remainder, false);
                if (!leftover.isEmpty()) tryInsert(source, leftover);
                transferred += (taken.getCount() - remainder.getCount());
            } else {
                transferred += taken.getCount();
            }
        }
        return transferred;
    }
}
