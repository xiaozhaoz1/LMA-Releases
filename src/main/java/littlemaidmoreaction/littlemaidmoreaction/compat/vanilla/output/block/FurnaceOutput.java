package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.FurnaceSlotMapping;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.item.ItemSpawner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** 熔炉方块输出 — slots 参数支持自定义栏位布局。 */
public final class FurnaceOutput {
    private FurnaceOutput() {}

    /** 取出产物 */
    public static boolean collectResult(AbstractFurnaceBlockEntity furnace, EntityMaid maid,
                                         FurnaceSlotMapping slots) {
        ItemStack result = furnace.getItem(slots.output());
        if (result.isEmpty()) return false;
        ItemStack copy = result.copy();
        furnace.setItem(slots.output(), ItemStack.EMPTY);
        furnace.setChanged();
        ItemSpawner.spawnForPickup(maid, copy);
        return true;
    }

    /** 向输入槽添加材料 */
    public static boolean addInput(AbstractFurnaceBlockEntity furnace, EntityMaid maid,
                                    String inputItemId, FurnaceSlotMapping slots) {
        ItemStack input = furnace.getItem(slots.input());
        if (!input.isEmpty()) return false;
        if (inputItemId.isEmpty()) return false;
        var ti = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(inputItemId));
        if (ti == null) return false;
        IItemHandler inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.is(ti)) {
                int toTake = Math.min(8, stack.getCount());
                furnace.setItem(slots.input(), inv.extractItem(i, toTake, false).copy());
                furnace.setChanged();
                return true;
            }
        }
        return false;
    }

    /** 向燃料槽添加燃料 (排除原料物品) */
    public static boolean addFuel(AbstractFurnaceBlockEntity furnace, EntityMaid maid,
                                   String inputItemId, FurnaceSlotMapping slots) {
        ItemStack fuel = furnace.getItem(slots.fuel());
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
                furnace.setItem(slots.fuel(), inv.extractItem(i, toTake, false).copy());
                furnace.setChanged();
                return true;
            }
        }
        return false;
    }
}
