package littlemaidmoreaction.littlemaidmoreaction.vanilla.input.recipe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配方链结果 record — 包含从原材料到目标产物的完整执行计划。
 *
 * @param target      目标产物
 * @param targetCount 目标数量
 * @param steps       正向排列的执行步骤（先执行索引0）
 * @param cost        原材料总消耗 (Item → 数量)
 */
public record RecipeChain(
    Item target,
    int targetCount,
    List<RecipeStep> steps,
    Map<Item, Integer> cost
) {
    /** 总步骤数。 */
    public int stepCount() { return steps.size(); }

    /**
     * 单步配方步骤。
     *
     * @param recipe      合成配方
     * @param output      本步产物
     * @param outputCount 单次合成产出数量
     * @param craftCount  需执行次数
     * @param dependsOn   依赖哪些前置步骤的产物 (Item 集合)
     */
    public record RecipeStep(
        CraftingRecipe recipe,
        Item output,
        int outputCount,
        int craftCount,
        Set<Item> dependsOn
    ) {
        /** 本步总产出数量 = outputCount × craftCount。 */
        public int totalOutput() { return outputCount * craftCount; }
    }
}
