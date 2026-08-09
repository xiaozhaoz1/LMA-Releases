package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

import javax.annotation.Nullable;

/** 物品 ID 解析 — 消除 21 处 ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id)) 重复 */
public final class ItemResolver {
    private ItemResolver() {}

    /** 按 ID 字符串解析物品，失败返回 null */
    @Nullable
    public static Item resolve(String itemId) {
        var rl = ResourceLocation.tryParse(itemId);
//? if 1.20.1 {
        return rl != null ? ForgeRegistries.ITEMS.getValue(rl) : null;
//?} else {
//? if 1.20.1 {
        return rl != null ? BuiltInRegistries.ITEM.getValue(rl) : null;
//?} else {
        return rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
//?}
//?}
    }
    /** 检查物品 ID 是否有效 */
    public static boolean exists(String itemId) { return resolve(itemId) != null; }
}
