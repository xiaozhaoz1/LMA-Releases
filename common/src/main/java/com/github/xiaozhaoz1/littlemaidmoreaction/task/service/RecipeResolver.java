package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.recipe.RecipeChain;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.recipe.RecipeIndex;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.recipe.RecipeTreeResolver;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
//? if 1.20.1 {
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

import javax.annotation.Nullable;
import java.util.Map;

public final class RecipeResolver {
    private RecipeResolver() {}

    @Nullable
    public static RecipeChain resolve(Level level, String targetItemId,
                                       Map<Item, Integer> available) {
        if (level == null) { LittleMaidMoreAction.LOGGER.warn("[RecipeResolver] level is null"); return null; }
        if (targetItemId == null) { LittleMaidMoreAction.LOGGER.warn("[RecipeResolver] targetItemId is null"); return null; }
        if (available == null) { LittleMaidMoreAction.LOGGER.warn("[RecipeResolver] available is null"); return null; }
        if (available.isEmpty()) { LittleMaidMoreAction.LOGGER.warn("[RecipeResolver] available is empty"); return null; }

        ResourceLocation rl = ResourceLocation.tryParse(targetItemId);
        if (rl == null) { LittleMaidMoreAction.LOGGER.warn("[RecipeResolver] invalid targetItemId={}", targetItemId); return null; }
        LittleMaidMoreAction.LOGGER.info("[RecipeResolver] target={}, available items={}", rl, available.keySet());

//? if 1.20.1 {
        Item target = ForgeRegistries.ITEMS.getValue(rl);
//?} else {
//? if 1.20.1 {
        Item target = BuiltInRegistries.ITEM.getValue(rl);
//?} else {
        Item target = BuiltInRegistries.ITEM.get(rl);
//?}
//?}
        if (target == null || target == Items.AIR) {
            LittleMaidMoreAction.LOGGER.warn("[RecipeResolver] target item not found in registry: {}", rl);
            return null;
        }
        LittleMaidMoreAction.LOGGER.info("[RecipeResolver] resolved target={}, removing from available", target);

        int wasAvailable = available.getOrDefault(target, 0);
        available.remove(target);
        LittleMaidMoreAction.LOGGER.info("[RecipeResolver] removed target from available (was {}), remaining={}", wasAvailable, available.size());

        RecipeIndex index = RecipeIndex.get(level);
        LittleMaidMoreAction.LOGGER.info("[RecipeResolver] RecipeIndex loaded, calling RecipeTreeResolver...");

        RecipeChain chain = RecipeTreeResolver.resolve(
            target, 1, available, index,
            RecipeTreeResolver.DEFAULT_MAX_DEPTH, level.registryAccess());

        if (chain == null) {
            LittleMaidMoreAction.LOGGER.warn("[RecipeResolver] RecipeTreeResolver returned NULL for target={}", target);
            // diagnostic: check if RecipeIndex has any recipes for this target
            var recipes = index.recipesProducing(target);
            LittleMaidMoreAction.LOGGER.info("[RecipeResolver] RecipeIndex has {} recipes producing {}", recipes.size(), target);
        } else {
            LittleMaidMoreAction.LOGGER.info("[RecipeResolver] RecipeTreeResolver returned {} steps, cost={}", chain.steps().size(), chain.cost());
        }
        return chain;
    }
}
