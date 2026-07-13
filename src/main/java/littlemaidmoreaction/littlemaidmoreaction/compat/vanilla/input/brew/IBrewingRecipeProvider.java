package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.brew;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 炼药配方提供者 SPI。
 * 通过 {@link BrewingRecipeRegistry#register(IBrewingRecipeProvider)} 注册。
 */
public interface IBrewingRecipeProvider {
    String id();
    boolean hasMix(ItemStack input, ItemStack ingredient);
    ItemStack getOutput(ItemStack input, ItemStack ingredient);
    boolean isIngredient(ItemStack stack);
    boolean isValidBase(ItemStack stack);
    List<BrewingRecipe> getAllRecipes();
}
