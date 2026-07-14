package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.item.ItemSpawner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** 熔炉方块输出 — 纯命令, 有副作用。 */
public final class FurnaceOutput {
    private FurnaceOutput() {}

    /** 取出 slot[2] 产物 → 生成到女仆身边 */
    public static boolean collectResult(AbstractFurnaceBlockEntity furnace, EntityMaid maid) {
        ItemStack result = furnace.getItem(2);
        if (result.isEmpty()) return false;
        ItemStack copy = result.copy();
        furnace.setItem(2, ItemStack.EMPTY);
        furnace.setChanged();
        ItemSpawner.spawnForPickup(maid, copy);
        return true;
    }

    /** 向 slot[0] 添加材料 (从女仆背包提取) */
    public static boolean addInput(AbstractFurnaceBlockEntity furnace, EntityMaid maid, String inputItemId) {
        ItemStack input = furnace.getItem(0);
        if (!input.isEmpty()) return false;
        if (inputItemId.isEmpty()) return false;
        var ti = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(inputItemId));
        if (ti == null) return false;
        IItemHandler inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.is(ti)) {
                int toTake = Math.min(8, stack.getCount());
                furnace.setItem(0, inv.extractItem(i, toTake, false).copy());
                furnace.setChanged();
                return true;
            }
        }
        return false;
    }

    /** 向 slot[1] 添加燃料 (从女仆背包提取, 排除原料物品) */
    public static boolean addFuel(AbstractFurnaceBlockEntity furnace, EntityMaid maid, String inputItemId) {
        ItemStack fuel = furnace.getItem(1);
        if (!fuel.isEmpty()) return false;
        IItemHandler inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (AbstractFurnaceBlockEntity.isFuel(stack)) {
                if (!inputItemId.isEmpty()) {
                    var ti = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(inputItemId));
                    if (ti != null && stack.is(ti)) continue;
                }
                int toTake = Math.min(64, stack.getCount());
                furnace.setItem(1, inv.extractItem(i, toTake, false).copy());
                furnace.setChanged();
                return true;
            }
        }
        return false;
    }
}
