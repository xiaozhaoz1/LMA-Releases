package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.api.VanillaInputRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.service.*;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.ProgressNotifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 熔炉管道 (Phase 2) — 查烧炼配方 → 检查原材料 → 写规则 → FurnaceInteractAction 执行。
 */
public final class FurnacePipeline implements TaskPipeline {

    @Override public String taskType() { return "furnace"; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return execute(level, maid, ctx);
    }

    @Override
    public PipelineResult execute(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        LittleMaidMoreAction.LOGGER.info("[V16] [Furnace] ====== START: target={}", target);

        Item targetItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(target));
        if (targetItem == null) {
            ProgressNotifier.notify(maid, "无效的目标物品: " + target);
            return PipelineResult.failed("无效的目标物品: " + target);
        }

        // Read inventories (Registry聚合)
        Map<Item, Integer> allItems = VanillaInputRegistry.readAllItems(maid);

        // Find smelting recipes that produce the target, and check if maid has the ingredient
        List<SmeltingRecipe> recipes = level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING);
        Item foundIngredient = null;
        int ingredientCount = 0;
        for (SmeltingRecipe recipe : recipes) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.is(targetItem)) continue;
            // Check each ingredient item against maid's inventory
            for (ItemStack ing : recipe.getIngredients().get(0).getItems()) {
                int have = allItems.getOrDefault(ing.getItem(), 0);
                if (have > 0) {
                    foundIngredient = ing.getItem();
                    ingredientCount = have;
                    LittleMaidMoreAction.LOGGER.info("[V16] [Furnace] found smeltable: {} (x{}) → {}",
                            foundIngredient, have, targetItem);
                    break;
                }
            }
            if (foundIngredient != null) break;
        }

        if (foundIngredient == null) {
            // Fallback: just check if target itself is in inventory
            int direct = allItems.getOrDefault(targetItem, 0);
            if (direct > 0) {
                foundIngredient = targetItem;
                ingredientCount = direct;
                LittleMaidMoreAction.LOGGER.info("[V16] [Furnace] target item directly in inventory: {} x{}", targetItem, direct);
            }
        }

        if (foundIngredient == null) {
            String msg = ProgressNotifier.noSmeltable(target);
            LittleMaidMoreAction.LOGGER.info("[V16] [Furnace] no smeltable material found for {}", target);
            ProgressNotifier.notify(maid, msg);
            return PipelineResult.failed(msg);
        }

        // ★ v18: 写原料ID → Brain读lma_task_input作为插入材料
        String taskId = ctx.taskId();
        String ingredientKey = ForgeRegistries.ITEMS.getKey(foundIngredient).toString();
        maid.getPersistentData().putString("lma_task_input", ingredientKey);
        String msg = "烧炼任务已启动: " + foundIngredient + " → " + targetItem + " (x" + ingredientCount + ")";
        ProgressNotifier.notify(maid, msg);
        LittleMaidMoreAction.LOGGER.info("[V16] [Furnace] ====== END: {}", msg);
        return PipelineResult.ok(msg);
    }
}
