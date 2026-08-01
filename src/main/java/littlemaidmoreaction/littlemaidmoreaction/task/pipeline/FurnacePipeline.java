package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.SlotLayout;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.VanillaInputRegistry;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.VanillaTasks;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskMetaData;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import littlemaidmoreaction.littlemaidmoreaction.task.service.ItemFilters;
import littlemaidmoreaction.littlemaidmoreaction.task.service.TaskConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 熔炉管道 — 查烧炼配方 → 检查原材料 → 执行。
 * v67.2/v67.3: 黑白名单过滤 (Cloth Config furnace.blacklist/whitelist, per-maid 可覆盖)。
 */
public final class FurnacePipeline implements TaskPipeline {

    @Override public String taskType() { return "furnace"; }
    @Override public boolean isLongRunning() { return true; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return w.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity; }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("smelt", "熔炉烧炼", StepType.CRAFT, List.of())); }

    /** v67.3: 黑白名单配置 GUI (per-maid) */
    @Override @javax.annotation.Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, "furnace");
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        Map<Item, Integer> allItems = VanillaInputRegistry.readAllItems(maid);
        List<String> black = effectiveBlack(maid);
        List<String> white = effectiveWhite(maid);

        // v64: 空 target — 检查是否有任何可烧炼材料
        if (target.isEmpty()) {
            for (SmeltingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
                for (ItemStack ing : recipe.getIngredients().get(0).getItems()) {
                    if (!ItemFilters.isAllowed(ing.getItem().toString(), black, white)) continue;
                    if (allItems.getOrDefault(ing.getItem(), 0) > 0) return PipelineResult.ok("");
                }
            }
            // 将可作燃料的物品也视为有效 (如原木→木炭)
            for (SmeltingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (!ItemFilters.isAllowed(result.getItem().toString(), black, white)) continue;
                if (allItems.getOrDefault(result.getItem(), 0) > 0) return PipelineResult.ok("");
            }
            return PipelineResult.failed("无可烧炼材料");
        }

        Item targetItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(target));
        if (targetItem == null) return PipelineResult.failed("无效的目标物品: " + target);
        if (!ItemFilters.isAllowed(targetItem.toString(), black, white)) return PipelineResult.failed("目标物品在黑/白名单之外: " + target);

        for (SmeltingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.is(targetItem)) continue;
            for (ItemStack ing : recipe.getIngredients().get(0).getItems()) {
                if (!ItemFilters.isAllowed(ing.getItem().toString(), black, white)) continue;
                if (allItems.getOrDefault(ing.getItem(), 0) > 0) return PipelineResult.ok("");
            }
        }
        if (allItems.getOrDefault(targetItem, 0) > 0) return PipelineResult.ok("");
        return PipelineResult.failed("no smeltable material for " + target);
    }

    public static IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                String ingredientKey = resolveSmeltIngredient(w, m);
                if (ingredientKey.isEmpty()) return TaskResult.FAILED;
                TaskMetaData.setInput(m, ingredientKey);
                VanillaTasks.furnace(w, m, p, ingredientKey, SlotLayout.FURNACE);
                return TaskResult.SUCCESS;
            }
        };
    }

    private static String resolveSmeltIngredient(ServerLevel level, EntityMaid maid) {
        String target = TaskMetaData.getTarget(maid);
        if (target.isEmpty()) return "";
        Item targetItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(target));
        if (targetItem == null) return "";
        List<String> black = effectiveBlack(maid);
        List<String> white = effectiveWhite(maid);
        if (!ItemFilters.isAllowed(targetItem.toString(), black, white)) return "";
        Map<Item, Integer> allItems = VanillaInputRegistry.readAllItems(maid);
        for (SmeltingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING)) {
            if (!recipe.getResultItem(level.registryAccess()).is(targetItem)) continue;
            for (ItemStack ing : recipe.getIngredients().get(0).getItems()) {
                if (!ItemFilters.isAllowed(ing.getItem().toString(), black, white)) continue;
                if (allItems.getOrDefault(ing.getItem(), 0) > 0)
                    return ForgeRegistries.ITEMS.getKey(ing.getItem()).toString();
            }
        }
        if (allItems.getOrDefault(targetItem, 0) > 0) return target;
        return "";
    }

    /** v67.3: 生效黑名单 — per-maid 覆盖全局 (静态上下文用 TaskConfigs) */
    private static List<String> effectiveBlack(EntityMaid maid) {
        return ItemFilters.effective(
                ItemFilters.maidList(TaskConfigs.get(maid, "furnace"), ItemFilters.KEY_BLACKLIST),
                ActiveTaskConfig.FURNACE_BLACKLIST.get());
    }

    /** v67.3: 生效白名单 — per-maid 覆盖全局 (静态上下文用 TaskConfigs) */
    private static List<String> effectiveWhite(EntityMaid maid) {
        return ItemFilters.effective(
                ItemFilters.maidList(TaskConfigs.get(maid, "furnace"), ItemFilters.KEY_WHITELIST),
                ActiveTaskConfig.FURNACE_WHITELIST.get());
    }
}
