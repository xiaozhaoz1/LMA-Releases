package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.craft;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.VanillaConstants;
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

/** v29: 合成任务编排 — pre-verify + constants */
public final class CraftExecute {
    private CraftExecute() {}

    public static boolean execute(ServerLevel world, EntityMaid maid, BlockPos pos, String target) {
        if (target.isEmpty()) return false;
        ResourceLocation rl = ResourceLocation.tryParse(target);
        if (rl == null) return false;
        Item targetItem = ForgeRegistries.ITEMS.getValue(rl);
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

        // Phase 1: 预验证
        for (var step : chain.steps()) {
            for (Ingredient ing : step.recipe().getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] matches = ing.getItems();
                if (matches.length == 0) continue;
                int need = step.craftCount() * matches[0].getCount();
                for (int i = 0; i < maidInv.getSlots() && need > 0; i++) {
                    if (maidInv.getStackInSlot(i).is(matches[0].getItem()))
                        need -= maidInv.getStackInSlot(i).getCount();
                }
                if (need > 0) return false;
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
