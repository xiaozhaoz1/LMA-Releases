package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.SlotLayout;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.item.ItemSpawner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** 熔炉方块输出 — slots 参数支持自定义栏位布局。 */
public final class FurnaceOutput {
    private FurnaceOutput() {}

    public static boolean collectResult(AbstractFurnaceBlockEntity furnace, EntityMaid maid,
                                         SlotLayout slots) {
        int outSlot = slots.slot("output");
        ItemStack result = furnace.getItem(outSlot);
        if (result.isEmpty()) return false;
        ItemStack copy = result.copy();
        furnace.setItem(outSlot, ItemStack.EMPTY);
        furnace.setChanged();
        ItemSpawner.spawnForPickup(maid, copy);
        return true;
    }

    public static boolean addInput(AbstractFurnaceBlockEntity furnace, EntityMaid maid,
                                    String inputItemId, SlotLayout slots) {
        int inSlot = slots.slot("input");
        ItemStack input = furnace.getItem(inSlot);
        if (!input.isEmpty()) return false;
        if (inputItemId.isEmpty()) return false;
        var ti = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(inputItemId));
        if (ti == null) return false;
        IItemHandler inv = maid.getAvailableInv(true);
        int s = ItemsUtil.findStackSlot(inv, stack -> stack.is(ti));
        if (s < 0) return false;
        int toTake = Math.min(8, inv.getStackInSlot(s).getCount());
        furnace.setItem(inSlot, inv.extractItem(s, toTake, false).copy());
        furnace.setChanged();
        return true;
    }

    public static boolean addFuel(AbstractFurnaceBlockEntity furnace, EntityMaid maid,
                                   String inputItemId, SlotLayout slots) {
        int fuelSlot = slots.slot("fuel");
        ItemStack fuel = furnace.getItem(fuelSlot);
        if (!fuel.isEmpty()) return false;
        IItemHandler inv = maid.getAvailableInv(true);
        var ti = inputItemId.isEmpty() ? null : ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(inputItemId));
        int s = ItemsUtil.findStackSlot(inv, stack ->
            AbstractFurnaceBlockEntity.isFuel(stack) && (ti == null || !stack.is(ti)));
        if (s < 0) return false;
        int toTake = Math.min(64, inv.getStackInSlot(s).getCount());
        furnace.setItem(fuelSlot, inv.extractItem(s, toTake, false).copy());
        furnace.setChanged();
        return true;
    }
}
