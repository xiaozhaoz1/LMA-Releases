package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaInputRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaOutputRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.recipe.RecipeIndex;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.recipe.RecipeTreeResolver;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.item.ItemSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
//? if 1.20.1 {
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
//?} else {
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.core.registries.BuiltInRegistries;
//?}

import java.util.Map;

/**
 * 合成链执行服务 (v79.61x execute 瘦身样本 3 归位) — 原 CraftExecute 单拍合成编排:
 * 配方链解析 → 预验证 → 执行 (取料+交付) → 音效。行为零变化 (逐行搬移, 仅包/类名变更)。
 *
 * <p>Phase 1 预验证 / Phase 2 执行两阶段 — 单线程服务器假设 (Forge server thread),
 * 预验证与执行之间无锁; 迁移异步模型需加锁 (原注释)。
 */
public final class CraftService {

    private CraftService() {}

    /** @return true if a craft chain was executed */
    public static boolean execute(ServerLevel world, EntityMaid maid, BlockPos pos, String target) {
        if (target.isEmpty()) return false;
        ResourceLocation rl = ResourceLocation.tryParse(target);
        if (rl == null) return false;
//? if 1.20.1 {
        Item targetItem = ForgeRegistries.ITEMS.getValue(rl);
//?} else {
        Item targetItem = BuiltInRegistries.ITEM.get(rl);
//?}
        if (targetItem == null) return false;

        Map<Item, Integer> available = VanillaInputRegistry.readAllItems(maid);
        available.remove(targetItem);

        IItemHandler maidInv = maid.getAvailableInv(true);
        var idx = RecipeIndex.get(world);

        var chain = RecipeTreeResolver.resolve(
            targetItem, VanillaConstants.CRAFT_BATCH_SIZE, available, idx,
            VanillaConstants.RECIPE_MAX_DEPTH, world.registryAccess());
        if (chain == null || chain.steps().isEmpty()) return false;

        ItemStack sampleOutput = chain.steps().get(chain.steps().size() - 1).recipe()
            .getResultItem(world.registryAccess());
        if (VanillaInputRegistry.totalSpace(maid, sampleOutput) <= 0) return false;

        // Phase 1: 预验证 — 只读统计库存
        // 假设：单线程服务器（Forge server thread）。预验证与执行之间无需锁，
        // 因为不存在并发库存修改。如迁移到异步模型，需在此处加锁。
        for (var step : chain.steps()) {
            for (Ingredient ing : step.recipe().getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] matches = ing.getItems();
                if (matches.length == 0) continue;
                int need = step.craftCount() * matches[0].getCount();
                var slots = ItemsUtil.getFilterStackSlots(maidInv, s -> s.is(matches[0].getItem()));
                int have = slots.stream().mapToInt(i -> maidInv.getStackInSlot(i).getCount()).sum();
                if (have < need) return false;
            }
        }

        // Phase 2: 执行
        for (var step : chain.steps()) {
            for (Ingredient ing : step.recipe().getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] matches = ing.getItems();
                if (matches.length == 0) continue;
                int need = step.craftCount() * matches[0].getCount();
                for (int i = 0; i < maidInv.getSlots() && need > 0; i++) {
                    if (maidInv.getStackInSlot(i).is(matches[0].getItem())) {
                        ItemStack extracted = maidInv.extractItem(i,
                            Math.min(need, maidInv.getStackInSlot(i).getCount()), false);
                        need -= extracted.getCount();
                        ItemSpawner.spawnRemainingIfAny(maid, extracted);
                    }
                }
            }
            VanillaOutputRegistry.deliver(maid, new ItemStack(
                step.recipe().getResultItem(world.registryAccess()).getItem(),
                step.recipe().getResultItem(world.registryAccess()).getCount()));
        }
        world.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }
}
