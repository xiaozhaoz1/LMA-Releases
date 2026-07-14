package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.function.Predicate;

/**
 * 物品栏查询工具 — 消除手动 for-loop 遍历。
 * 两个重载: 女仆背包 (EntityMaid) + 通用容器 (IItemHandler)
 */
public final class InventoryHelper {
    private InventoryHelper() {}

    /** 在女仆背包(含双手槽)中查找第一个匹配物品的槽位, -1=未找到 */
    public static int findSlot(EntityMaid maid, Predicate<ItemStack> filter) {
        return findSlot(maid.getAvailableInv(true), filter);
    }

    /** 在 IItemHandler 中查找第一个匹配物品的槽位, -1=未找到 */
    public static int findSlot(IItemHandler handler, Predicate<ItemStack> filter) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (filter.test(handler.getStackInSlot(i))) return i;
        }
        return -1;
    }

    /** 统计女仆背包中匹配物品的总数 */
    public static int count(EntityMaid maid, Predicate<ItemStack> filter) {
        return count(maid.getAvailableInv(true), filter);
    }

    /** 统计 IItemHandler 中匹配物品的总数 */
    public static int count(IItemHandler handler, Predicate<ItemStack> filter) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (filter.test(s)) total += s.getCount();
        }
        return total;
    }
}
