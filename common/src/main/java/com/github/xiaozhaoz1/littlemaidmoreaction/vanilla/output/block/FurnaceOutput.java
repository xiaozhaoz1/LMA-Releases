package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.SlotLayout;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.item.ItemSpawner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
//? if 1.20.1 {
import net.minecraftforge.items.IItemHandler;
//?} else {
import net.neoforged.neoforge.items.IItemHandler;
//?}
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

/** v32.1: SlotLayout OptionalInt */
public final class FurnaceOutput {
    private FurnaceOutput() {}

    public static boolean collectResult(AbstractFurnaceBlockEntity furnace, EntityMaid maid,
                                         SlotLayout slots) {
        int outSlot = slots.slot("output").orElse(2);
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
        int inSlot = slots.slot("input").orElse(0);
        ItemStack input = furnace.getItem(inSlot);
        if (!input.isEmpty()) return false;
        if (inputItemId.isEmpty()) return false;
//? if 1.20.1 {
        var ti = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(inputItemId));
//?} else {
//? if 1.20.1 {
        var ti = BuiltInRegistries.ITEM.getValue(ResourceLocation.tryParse(inputItemId));
//?} else {
        var ti = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(inputItemId));
//?}
//?}
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
        int fuelSlot = slots.slot("fuel").orElse(1);
        ItemStack fuel = furnace.getItem(fuelSlot);
        if (!fuel.isEmpty()) return false;
        IItemHandler inv = maid.getAvailableInv(true);
//? if 1.20.1 {
        var ti = inputItemId.isEmpty() ? null : ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(inputItemId));
//?} else {
//? if 1.20.1 {
        var ti = inputItemId.isEmpty() ? null : BuiltInRegistries.ITEM.getValue(ResourceLocation.tryParse(inputItemId));
//?} else {
        var ti = inputItemId.isEmpty() ? null : BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(inputItemId));
//?}
//?}
        int s = ItemsUtil.findStackSlot(inv, stack ->
            AbstractFurnaceBlockEntity.isFuel(stack) && (ti == null || !stack.is(ti)));
        if (s < 0) return false;
        int toTake = Math.min(64, inv.getStackInSlot(s).getCount());
        furnace.setItem(fuelSlot, inv.extractItem(s, toTake, false).copy());
        furnace.setChanged();
        return true;
    }
}
