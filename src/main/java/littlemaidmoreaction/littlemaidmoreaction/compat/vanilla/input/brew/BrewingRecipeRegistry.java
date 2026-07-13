package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.brew;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 炼药配方注册中心 — 联合查询所有 IBrewingRecipeProvider。
 */
public final class BrewingRecipeRegistry {

    private static final List<IBrewingRecipeProvider> PROVIDERS = new ArrayList<>();

    static {
        register(new VanillaBrewingRecipeProvider());
    }

    private BrewingRecipeRegistry() {}

    public static void register(IBrewingRecipeProvider provider) {
        for (IBrewingRecipeProvider existing : PROVIDERS) {
            if (existing.id().equals(provider.id())) return;
        }
        PROVIDERS.add(provider);
    }

    public static boolean hasMix(ItemStack input, ItemStack ingredient) {
        if (input.isEmpty() || ingredient.isEmpty()) return false;
        for (IBrewingRecipeProvider p : PROVIDERS) {
            if (p.hasMix(input, ingredient)) return true;
        }
        return false;
    }

    public static ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (input.isEmpty() || ingredient.isEmpty()) return ItemStack.EMPTY;
        for (IBrewingRecipeProvider p : PROVIDERS) {
            ItemStack result = p.getOutput(input, ingredient);
            if (!result.isEmpty()) return result;
        }
        return ItemStack.EMPTY;
    }

    public static boolean isIngredient(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (IBrewingRecipeProvider p : PROVIDERS) {
            if (p.isIngredient(stack)) return true;
        }
        return false;
    }

    public static boolean isValidBase(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (IBrewingRecipeProvider p : PROVIDERS) {
            if (p.isValidBase(stack)) return true;
        }
        return false;
    }

    public static List<BrewingRecipe> getAllRecipes() {
        List<BrewingRecipe> all = new ArrayList<>();
        for (IBrewingRecipeProvider p : PROVIDERS) {
            all.addAll(p.getAllRecipes());
        }
        return all;
    }

    public static int providerCount() {
        return PROVIDERS.size();
    }
}
