package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.container;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if 1.20.1 {
import net.minecraftforge.common.capabilities.ForgeCapabilities;
//?} else {
import net.neoforged.neoforge.capabilities.Capabilities;
//?}

import java.util.LinkedHashMap;
import java.util.Map;

/** 隙间箱子物品读取 — 纯查询, 无副作用 */
public final class WirelessChestReader {
    private WirelessChestReader() {}

    public static Map<Item, Integer> readAll(EntityMaid maid) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        var baubleHandler = maid.getMaidBauble();
        for (int i = 0; i < baubleHandler.getSlots(); i++) {
            ItemStack bauble = baubleHandler.getStackInSlot(i);
            if (bauble.isEmpty()) continue;
            var bindingPos = ItemWirelessIO.getBindingPos(bauble);
            if (bindingPos == null) continue;
            BlockEntity be = maid.level().getBlockEntity(bindingPos);
            if (be == null) continue;
//? if 1.20.1 {
            be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
//?} else {
            var handler = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
            if (handler != null) {
//?}
                for (int j = 0; j < handler.getSlots(); j++) {
                    ItemStack s = handler.getStackInSlot(j);
                    if (!s.isEmpty()) result.merge(s.getItem(), s.getCount(), Integer::sum);
                }
//? if 1.20.1 {
            });
//?} else {
            }
//?}
        }
        return result;
    }
}
