package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

/** 目标状态读取 (v72 补全) — 覆盖 impl/condition/target 查询 */
public final class TargetStateReader {
    private TargetStateReader() {}

    /** 目标主手物品 */
    public static ItemStack getHoldingItem(LivingEntity t) { return t.getMainHandItem(); }

    /** 目标主手物品注册名; 空手返回 "air" (镜像旧仓库语义) */
    public static String getHoldingItemId(LivingEntity t) {
//? if 1.20.1 {
        var s = t.getMainHandItem(); return s.isEmpty() ? "air" : ForgeRegistries.ITEMS.getKey(s.getItem()).toString();
//?} else {
        var s = t.getMainHandItem(); return s.isEmpty() ? "air" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
//?}
    }
}
