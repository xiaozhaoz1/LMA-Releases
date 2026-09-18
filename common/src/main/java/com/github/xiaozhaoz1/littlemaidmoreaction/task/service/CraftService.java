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

        // Phase 1: 预验证 — 按步骤逐步模拟流转 (v79.62.3 多步链修复)
        // 不再一次性检查整条链的全部原料 (中间产物尚未合成, 必然误判不足 —
        // 多步链如 原木→木板→木棍 永远无法启动)。改为沿链序模拟:
        //   本步原料 = 模拟中间产物池 (前序步骤产出) + 背包; 任一不足 → false;
        //   扣除后把本步产物记入模拟池, 进入下一步。
        // 假设：单线程服务器（Forge server thread）。预验证与执行之间无需锁，
        // 因为不存在并发库存修改。如迁移到异步模型，需在此处加锁。
        // v79.62.1: 用解析树记录的选定变体 (selectedInputs) 匹配 — 不再 matches[0]
        // (配方允许多种原料如任意木板, 背包有第二种变体时 matches[0] 误判不足)
        Map<Item, Integer> simPool = new java.util.HashMap<>();
        Map<Item, Integer> simAvail = new java.util.HashMap<>();
        for (int i = 0; i < maidInv.getSlots(); i++) {
            ItemStack st = maidInv.getStackInSlot(i);
            if (!st.isEmpty()) simAvail.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        for (var step : chain.steps()) {
            var selected = step.selectedInputs();
            int selIdx = 0;
            for (Ingredient ing : step.recipe().getIngredients()) {
                if (ing.isEmpty()) continue;
                Item item = selIdx < selected.size() ? selected.get(selIdx) : ing.getItems()[0].getItem();
                selIdx++;
                int per = selectedCountOf(ing, item);
                int need = step.craftCount() * per;
                int pooled = simPool.getOrDefault(item, 0);
                int takePool = Math.min(pooled, need);
                need -= takePool;
                if (takePool > 0) {
                    if (pooled == takePool) simPool.remove(item);
                    else simPool.put(item, pooled - takePool);
                }
                if (need > 0) {
                    int have = simAvail.getOrDefault(item, 0);
                    if (have < need) return false;
                    simAvail.put(item, have - need);
                }
            }
            simPool.merge(step.output(), step.totalOutput(), Integer::sum);
        }

        // Phase 2: 执行 — 中间产物池流转 (v79.62.3 多步链修复)
        // 每步产物先进内存池 (不再直接地面交付); 下一步扣料顺序 = 池优先 → 背包;
        // 全部完成后池内剩余 (含最终产物) 统一走原有交付 (地面 spawn + 拾取)。
        // 保险: 任一步原料不足 (预验证已阻止, 理论上不可达) → 立即返回, 不交付任何产物,
        // 杜绝"原料不够仍产出"的多给。
        Map<Item, Integer> midPool = new java.util.HashMap<>();
        for (var step : chain.steps()) {
            var selected = step.selectedInputs();
            int selIdx = 0;
            for (Ingredient ing : step.recipe().getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] matches = ing.getItems();
                if (matches.length == 0) continue;
                Item item = selIdx < selected.size() ? selected.get(selIdx) : matches[0].getItem();
                selIdx++;
                int need = step.craftCount() * selectedCountOf(ing, item);
                // 中间产物池优先
                int pooled = midPool.getOrDefault(item, 0);
                int takePool = Math.min(pooled, need);
                need -= takePool;
                if (takePool > 0) {
                    if (pooled == takePool) midPool.remove(item);
                    else midPool.put(item, pooled - takePool);
                }
                // 不够再动真实背包
                for (int i = 0; i < maidInv.getSlots() && need > 0; i++) {
                    if (maidInv.getStackInSlot(i).is(item)) {
                        ItemStack extracted = maidInv.extractItem(i,
                            Math.min(need, maidInv.getStackInSlot(i).getCount()), false);
                        need -= extracted.getCount();
                        ItemSpawner.spawnRemainingIfAny(maid, extracted);
                    }
                }
                if (need > 0) return false; // 保险: 原料不够 → 不交付任何产物
            }
            midPool.merge(step.output(), step.totalOutput(), Integer::sum);
        }
        // 统一交付: 池内剩余中间产物 + 最终产物 (原有交付方式不变)
        for (var entry : midPool.entrySet()) {
            if (entry.getValue() > 0) {
                VanillaOutputRegistry.deliver(maid, new ItemStack(entry.getKey(), entry.getValue()));
            }
        }
        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.SoundOutput.playAt(
                world, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /**
     * 选定变体的单份数量 (v79.62.3 算错防护) — 扣料按 selectedInputs 的变体,
     * need 必须用该变体自己的 count, 而不是 matches[0].getCount() (tag 内变体
     * 数量不同时 — 如 A 槽 1 个/B 槽 2 个 — 用 matches[0] 会把扣料数量算错:
     * 多扣少扣/预验证与实际执行漂移)。找不到时回退 1 (保守不炸)。
     */
    private static int selectedCountOf(Ingredient ing, Item item) {
        for (ItemStack m : ing.getItems()) {
            if (m.is(item)) return m.getCount();
        }
        return 1;
    }
}
