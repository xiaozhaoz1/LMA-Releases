package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.craft;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.VanillaInputRegistry;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.VanillaOutputRegistry;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.recipe.RecipeIndex;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.recipe.RecipeTreeResolver;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.item.ItemSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/** v24: 合成任务编排 — 通过 Registry 聚合 input/output providers */
public final class CraftExecute {
    private CraftExecute() {}

    public static boolean execute(ServerLevel world, EntityMaid maid, BlockPos pos, String target) {
        if (target.isEmpty()) return false;
        ResourceLocation rl = ResourceLocation.tryParse(target);
        if (rl == null) return false;
        Item targetItem = ForgeRegistries.ITEMS.getValue(rl);
        if (targetItem == null) return false;

        // === input: 读材料 (Registry聚合) ===
        Map<Item, Integer> available = VanillaInputRegistry.readAllItems(maid);
        available.remove(targetItem);

        IItemHandler maidInv = maid.getAvailableInv(true);
        var idx = RecipeIndex.get(world);

        // === 计算材料上限 ===
        var probe = RecipeTreeResolver.resolve(
            targetItem, 1, available, idx, 10, world.registryAccess());
        if (probe == null || probe.cost().isEmpty()) return false;

        int materialBatches = Integer.MAX_VALUE;
        for (var e : probe.cost().entrySet()) {
            if (e.getValue() <= 0) continue;
            materialBatches = Math.min(materialBatches, available.getOrDefault(e.getKey(), 0) / e.getValue());
        }
        if (materialBatches <= 0) return false;

        // ★ v24: 空间上限 (Registry聚合)
        ItemStack sampleOutput = probe.steps().get(probe.steps().size() - 1).recipe()
            .getResultItem(world.registryAccess());
        int outputPerBatch = sampleOutput.getCount();
        if (outputPerBatch <= 0) return false;
        int totalSpace = VanillaInputRegistry.totalSpace(maid, sampleOutput);
        int spaceBatches = totalSpace / outputPerBatch;
        if (spaceBatches <= 0) return false;
        int finalBatches = Math.min(materialBatches, spaceBatches);
        if (finalBatches <= 0) return false;

        // === 用最终批数解析配方 ===
        var chain = RecipeTreeResolver.resolve(
            targetItem, finalBatches, available, idx, 10, world.registryAccess());
        if (chain == null || chain.steps().isEmpty()) return false;

        // === output: 提取材料 + 生成产物 (Registry聚合) ===
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
                step.recipe().getResultItem(world.registryAccess()).getCount() * finalBatches));
        }
        world.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }
}
