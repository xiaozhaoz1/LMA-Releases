package littlemaidmoreaction.littlemaidmoreaction.vanilla.input.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 配方反向索引 — 参考 JEI 的 {@code RecipeManager.getAllRecipesFor()} 模式。
 *
 * <p>构建三个索引：
 * <ul>
 *   <li><b>byOutput</b>: 输出物品 → 产生该物品的配方列表</li>
 *   <li><b>byInput</b>:  输入物品 → 消耗该物品的配方列表</li>
 *   <li><b>byId</b>:     配方ID → 配方</li>
 * </ul>
 *
 * <p>使用 {@link WeakHashMap} 按 {@link RecipeManager} 实例缓存，
 * 世界重载时旧的 RecipeManager 被 GC → 旧索引自动清理。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * RecipeIndex idx = RecipeIndex.get(maid.level());
 * List<CraftingRecipe> recipes = idx.recipesProducing(Items.STICK);
 * }</pre>
 */
public final class RecipeIndex {

    private static final Map<RecipeManager, RecipeIndex> CACHE = new WeakHashMap<>();

    private final Map<Item, List<CraftingRecipe>> byOutput = new HashMap<>();
    private final Map<Item, List<CraftingRecipe>> byInput = new HashMap<>();
    private final Map<ResourceLocation, Recipe<?>> byId = new HashMap<>();

    private RecipeIndex(RecipeManager rm, RegistryAccess registryAccess) {
        rebuild(rm, registryAccess);
    }

    /** 测试用构造函数 — 直接注入索引数据。 */
    RecipeIndex(Map<Item, List<CraftingRecipe>> byOutput,
                Map<Item, List<CraftingRecipe>> byInput,
                Map<ResourceLocation, Recipe<?>> byId) {
        this.byOutput.putAll(byOutput);
        this.byInput.putAll(byInput);
        this.byId.putAll(byId);
    }

    // ─── 静态工厂 ───────────────────────────────────────────────

    /**
     * 获取当前世界的配方索引（懒加载 + 自动缓存清理）。
     *
     * @param level 服务端世界
     * @return 该世界的配方索引（可能被多个调用方共享）
     */
    public static RecipeIndex get(Level level) {
        RecipeManager rm = level.getRecipeManager();
        return CACHE.computeIfAbsent(rm, k -> new RecipeIndex(rm, level.registryAccess()));
    }

    // ─── 构建 ───────────────────────────────────────────────────

    private void rebuild(RecipeManager rm, RegistryAccess registryAccess) {
        byOutput.clear();
        byInput.clear();
        byId.clear();

        List<CraftingRecipe> all = rm.getAllRecipesFor(RecipeType.CRAFTING);
        for (CraftingRecipe recipe : all) {
            if (recipe.isSpecial()) continue; // 跳过旗帜/烟火之星等特殊配方

            ResourceLocation id = recipe.getId();
            ItemStack result = recipe.getResultItem(registryAccess);
            if (result.isEmpty()) continue;

            // byOutput
            Item outputItem = result.getItem();
            byOutput.computeIfAbsent(outputItem, k -> new ArrayList<>()).add(recipe);

            // byInput: 展开每个 Ingredient 的所有可能物品
            for (Ingredient ing : recipe.getIngredients()) {
                if (ing.isEmpty()) continue;
                for (ItemStack match : ing.getItems()) {
                    byInput.computeIfAbsent(match.getItem(), k -> new ArrayList<>()).add(recipe);
                }
            }

            // byId
            byId.put(id, recipe);
        }
    }

    // ─── 查询 ───────────────────────────────────────────────────

    /** 产出指定物品的所有合成配方。 */
    public List<CraftingRecipe> recipesProducing(Item output) {
        return byOutput.getOrDefault(output, List.of());
    }

    /** 消耗指定物品的所有合成配方（该物品作为原料）。 */
    public List<CraftingRecipe> recipesConsuming(Item input) {
        return byInput.getOrDefault(input, List.of());
    }

    /** 按配方ID精确查找。 */
    public Optional<Recipe<?>> byKey(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** 全部合成配方（已过滤特殊配方）。 */
    public Collection<CraftingRecipe> allCrafting() {
        return List.copyOf(byId.values().stream()
            .filter(r -> r instanceof CraftingRecipe)
            .map(r -> (CraftingRecipe) r)
            .toList());
    }

    /** 已索引的配方总数。 */
    public int size() {
        return byId.size();
    }
}
