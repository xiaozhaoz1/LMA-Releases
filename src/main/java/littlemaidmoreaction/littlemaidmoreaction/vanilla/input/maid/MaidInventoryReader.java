package littlemaidmoreaction.littlemaidmoreaction.vanilla.input.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** 女仆物品栏读取 — 纯查询, 无副作用 */
public final class MaidInventoryReader {
    private MaidInventoryReader() {}

    /** 读取女仆全部物品栏(背包+双手) → Map<Item, Integer> */
    public static Map<Item, Integer> readAll(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        Map<Item, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) result.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return result;
    }

    /** 仅读取背包(不含双手) */
    public static Map<Item, Integer> readBackpack(EntityMaid maid) {
        var inv = maid.getAvailableBackpackInv();
        Map<Item, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) result.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return result;
    }
}
