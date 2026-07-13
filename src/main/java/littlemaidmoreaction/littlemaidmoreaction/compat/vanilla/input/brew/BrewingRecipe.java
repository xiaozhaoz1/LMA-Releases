package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.brew;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record BrewingRecipe(Item input, Item ingredient, ItemStack output) {

    public boolean matches(ItemStack inputStack, ItemStack ingredientStack) {
        return inputStack.is(input) && ingredientStack.is(ingredient);
    }
}
