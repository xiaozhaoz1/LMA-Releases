package littlemaidmoreaction.littlemaidmoreaction.vanilla.output.container;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** 容器交互输出 — 物品存取原语 */
public final class ContainerOutput {
    private ContainerOutput() {}

    /** 从女仆背包提取物品并存入容器。溢出自动退还女仆。 */
    public static boolean depositItem(EntityMaid maid, IItemHandler container, Item item, int count) {
        var inv = maid.getAvailableInv(false);
        int remaining = count;
        for (int i = 0; i < inv.getSlots() && remaining > 0; i++) {
            var stack = inv.getStackInSlot(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                var toDeposit = inv.extractItem(i, take, false);
                if (!toDeposit.isEmpty()) {
                    int before = toDeposit.getCount();
                    for (int j = 0; j < container.getSlots() && !toDeposit.isEmpty(); j++)
                        toDeposit = container.insertItem(j, toDeposit, false);
                    if (!toDeposit.isEmpty()) // 容器满了，退还女仆
                        inv.insertItem(i, toDeposit, false);
                    remaining -= (before - toDeposit.getCount());
                }
            }
        }
        return remaining < count;
    }

    /** 从容器提取物品并存入女仆背包。溢出自动退还容器。 */
    public static boolean withdrawItem(EntityMaid maid, IItemHandler container, Item item, int count) {
        var inv = maid.getAvailableInv(false);
        int remaining = count;
        for (int i = 0; i < container.getSlots() && remaining > 0; i++) {
            var stack = container.getStackInSlot(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                var extracted = container.extractItem(i, take, false);
                if (!extracted.isEmpty()) {
                    int before = extracted.getCount();
                    for (int j = 0; j < inv.getSlots() && !extracted.isEmpty(); j++)
                        extracted = inv.insertItem(j, extracted, false);
                    if (!extracted.isEmpty()) // 女仆背包满了，退还容器
                        container.insertItem(i, extracted, false);
                    remaining -= (before - extracted.getCount());
                }
            }
        }
        return remaining < count;
    }
}
