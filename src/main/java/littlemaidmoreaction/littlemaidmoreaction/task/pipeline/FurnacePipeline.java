package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.VanillaInputRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 熔炉管道 (Phase 2) — 查烧炼配方 → 检查原材料 → 写规则 → FurnaceInteractAction 执行。
 */
public final class FurnacePipeline implements TaskPipeline {

    @Override public String taskType() { return "furnace"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s) { return w.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity; }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("smelt", "熔炉烧炼", StepType.CRAFT, List.of())); }

    /** v44: 纯验证 — 仅检查是否有可烧炼材料，不写NBT/日志/通知 */
    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        Item targetItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(target));
        if (targetItem == null) return PipelineResult.failed("无效的目标物品: " + target);

        Map<Item, Integer> allItems = VanillaInputRegistry.readAllItems(maid);
        for (SmeltingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.is(targetItem)) continue;
            for (ItemStack ing : recipe.getIngredients().get(0).getItems()) {
                if (allItems.getOrDefault(ing.getItem(), 0) > 0) return PipelineResult.ok("");
            }
        }
        // Fallback: target本身在背包中
        if (allItems.getOrDefault(targetItem, 0) > 0) return PipelineResult.ok("");
        return PipelineResult.failed("no smeltable material for " + target);
    }

}
