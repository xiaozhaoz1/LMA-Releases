package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item;

import net.minecraft.world.item.ItemStack;

/**
 * ItemStack 比较工具 (v75.4 标准化)。
 *
 * <p>原 4 份逐字节相同 helper + 7 处内联版本门控对 (AbstractFunctionalBlockInteraction /
 * MaidAssemblyInventory / MaidAssemblyPipeline / MaidAssemblyService / MaidInventorySpace /
 * WirelessChestSpace / JukeboxExecute / ArmTransferService) 收拢于此 — 双平台差异单点维护。
 */
public final class ItemStackHelper {

    private ItemStackHelper() {}

    /** 两个 ItemStack 是否为同一物品 (含组件/标签比较, 双平台门控) */
    public static boolean isSameItem(ItemStack a, ItemStack b) {
//? if 1.20.1 {
        return ItemStack.isSameItemSameTags(a, b);
//?} else {
        return ItemStack.isSameItemSameComponents(a, b);
//?}
    }
}
