package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/**
 * v79.62.3 防御塔手办合成序列化器 — 双平台均用 SimpleCraftingRecipeSerializer
 * (1.20.1 Factory create(ResourceLocation, CraftingBookCategory) / 1.21.1 create(CraftingBookCategory)).
 */
public final class DefenseGarageKitRecipeSerializer
        extends SimpleCraftingRecipeSerializer<DefenseGarageKitRecipe> {

    /** 静态单例 (Recipe.getSerializer 引用 + 注册点使用) */
    public static final DefenseGarageKitRecipeSerializer INSTANCE =
            new DefenseGarageKitRecipeSerializer();

    private DefenseGarageKitRecipeSerializer() {
        super(new FactoryImpl());
    }

    /** 双平台工厂适配 (stonecutter 分支) */
    private static final class FactoryImpl implements
            SimpleCraftingRecipeSerializer.Factory<DefenseGarageKitRecipe> {
        @Override
        public DefenseGarageKitRecipe create(
//? if 1.20.1 {
                net.minecraft.resources.ResourceLocation id, CraftingBookCategory category) {
            return new DefenseGarageKitRecipe(id, category);
//?} else {
                CraftingBookCategory category) {
            return new DefenseGarageKitRecipe(category);
//?}
        }
    }
}
