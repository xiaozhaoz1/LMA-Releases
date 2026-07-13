package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * 女仆物品栏空间计算 — 按栏位类型细分。
 * <p>三种栏位对应不同 IItemHandler:
 * <ul>
 *   <li>ofBackpack → maid.getAvailableBackpackInv()</li>
 *   <li>ofHands → maid.getHandsInvWrapper()</li>
 *   <li>ofAll → maid.getAvailableInv(true)</li>
 * </ul>
 */
public final class MaidInventorySpace {
    private MaidInventorySpace() {}

    /** 主背包空间(不含双手): 空槽=stack上限, 同物品槽=上限-存量 */
    public static int ofBackpack(EntityMaid maid, ItemStack sample) {
        return calculate(maid.getAvailableBackpackInv(), sample);
    }

    /** 双手空间(主手+副手) */
    public static int ofHands(EntityMaid maid, ItemStack sample) {
        return calculate(maid.getHandsInvWrapper(), sample);
    }

    /** 全部空间(背包+双手) ★ 合成任务用此方法 */
    public static int ofAll(EntityMaid maid, ItemStack sample) {
        return calculate(maid.getAvailableInv(true), sample);
    }

    /** 遍历物品栏计算可容纳指定物品的总数量 */
    static int calculate(IItemHandler inv, ItemStack sample) {
        if (inv == null || sample == null || sample.isEmpty()) return 0;
        int maxStack = sample.getMaxStackSize();
        int total = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                total += maxStack;
            } else if (ItemStack.isSameItemSameTags(stack, sample)) {
                total += Math.max(0, maxStack - stack.getCount());
            }
        }
        return total;
    }
}
