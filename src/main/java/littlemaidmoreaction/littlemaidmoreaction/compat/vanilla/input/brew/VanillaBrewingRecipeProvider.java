package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.brew;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;

import java.util.List;

/**
 * 原版炼药配方提供者 — 委托给 PotionBrewing 公共 API。
 * 注意: PotionBrewing.mix(reagent, input) 参数顺序与 hasMix(input, reagent) 相反。
 */
public class VanillaBrewingRecipeProvider implements IBrewingRecipeProvider {

    @Override public String id() { return "vanilla"; }

    @Override
    public boolean hasMix(ItemStack input, ItemStack ingredient) {
        return PotionBrewing.hasMix(input, ingredient);
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        return PotionBrewing.mix(ingredient, input);
    }

    @Override
    public boolean isIngredient(ItemStack stack) {
        return PotionBrewing.isIngredient(stack);
    }

    @Override
    public boolean isValidBase(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION)
            || stack.is(Items.LINGERING_POTION) || stack.is(Items.GLASS_BOTTLE);
    }

    @Override
    public List<BrewingRecipe> getAllRecipes() {
        return List.of();
    }
}
