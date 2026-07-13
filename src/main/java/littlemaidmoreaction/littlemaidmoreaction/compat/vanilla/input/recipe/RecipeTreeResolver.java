package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.recipe;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.*;

/**
 * 配方树求解器 — BFS 反向搜索，从目标物品回溯到可用原材料。
 *
 * <p>纯逻辑算法，不依赖 Minecraft 世界状态，可单元测试。</p>
 *
 * <h3>示例 — 原木→木棍</h3>
 * <pre>{@code
 * Map<Item, Integer> available = Map.of(Items.OAK_LOG, 3);
 * RecipeChain chain = RecipeTreeResolver.resolve(
 *     Items.STICK, 12, available, recipeIndex, 10);
 * // chain.steps = [LOG→PLANKS, PLANKS→STICK]
 * // chain.cost   = {OAK_LOG: 2}
 * }</pre>
 *
 * <h3>算法</h3>
 * <ol>
 *   <li>从 target 开始 BFS，每层查找能产出该物品的配方</li>
 *   <li>对配方的每个 Ingredient: 在 available 中? → 直接消耗 : → 入队递归</li>
 *   <li>visited Set 防止循环依赖</li>
 *   <li>选择策略: 优先 Ingredient 能匹配 available 的配方 &gt; 产出率最高 &gt; BFS 天然最短路径</li>
 *   <li>拓扑排序 → 正向执行步骤</li>
 * </ol>
 */
public final class RecipeTreeResolver {

    private RecipeTreeResolver() {}

    /** 默认最大搜索深度。 */
    public static final int DEFAULT_MAX_DEPTH = 10;

    /**
     * 求解配方链。
     *
     * @param target      目标产物
     * @param targetCount 目标数量
     * @param available   女仆背包可用物品 (Item → 数量)
     * @param index       配方索引
     * @param maxDepth    最大搜索深度
     * @param registryAccess 注册表访问 (可为null，仅用于 getResultItem)
     * @return RecipeChain 或 null (不可达)
     */
    public static RecipeChain resolve(
            Item target, int targetCount,
            Map<Item, Integer> available,
            RecipeIndex index,
            int maxDepth,
            RegistryAccess registryAccess) {

        if (target == null || targetCount <= 0 || available == null || index == null) {
            return null;
        }

        // 如果目标物品已经足够 → 不需要合成
        int alreadyHave = available.getOrDefault(target, 0);
        if (alreadyHave >= targetCount) {
            return new RecipeChain(target, targetCount, List.of(), Map.of());
        }

        // BFS 队列: (item, needed)
        Deque<Node> queue = new ArrayDeque<>();
        Set<Item> visited = new LinkedHashSet<>();
        List<RawStep> rawSteps = new ArrayList<>();

        int needed = targetCount - alreadyHave;
        queue.add(new Node(target, needed, 0));
        visited.add(target);

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (node.depth > maxDepth) return null;

            // 此物品已在 available 中且数量足够 → 不需要合成
            if (available.getOrDefault(node.item, 0) >= node.needed) {
                continue;
            }

            // 查找产出此物品的配方
            List<CraftingRecipe> recipes = index.recipesProducing(node.item);
            if (recipes.isEmpty()) {
                LittleMaidMoreAction.LOGGER.debug("[RecipeTree] unreachable: {} (no recipes found)", node.item);
                return null;
            }

            // 选择最佳配方
            CraftingRecipe best = selectBestRecipe(recipes, available, index, registryAccess);
            ItemStack result = best.getResultItem(registryAccess);
            int perCraft = result.getCount();
            int craftCount = (int) Math.ceil((double) node.needed / perCraft);

            // ★ 聚合相同原料的需求量（多槽位配方如木棍需要2个木板）
            Map<Item, Integer> neededPerItem = new LinkedHashMap<>();
            for (Ingredient ing : best.getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] matches = ing.getItems();
                if (matches.length == 0) continue;
                Item matchItem = matches[0].getItem();
                int perIngredient = matches[0].getCount();
                int totalNeeded = craftCount * perIngredient;
                neededPerItem.merge(matchItem, totalNeeded, Integer::sum);
            }

            Set<Item> deps = new LinkedHashSet<>();
            List<Item> subGoals = new ArrayList<>();

            for (var entry : neededPerItem.entrySet()) {
                Item matchItem = entry.getKey();
                int totalNeeded = entry.getValue();
                int have = available.getOrDefault(matchItem, 0);

                if (have < totalNeeded) {
                    if (visited.contains(matchItem)) {
                        LittleMaidMoreAction.LOGGER.debug("[RecipeTree] cycle detected: {} ← {}", node.item, matchItem);
                        return null;
                    }
                    deps.add(matchItem);
                    subGoals.add(matchItem);
                    queue.add(new Node(matchItem, totalNeeded - have, node.depth + 1));
                    visited.add(matchItem);
                }
            }

            rawSteps.add(new RawStep(best, node.item, perCraft, craftCount, deps));
        }

        // 拓扑排序: 依赖关系 → 正向执行顺序
        List<RecipeChain.RecipeStep> orderedSteps = topologicalSort(rawSteps);
        if (orderedSteps == null) return null;

        // 计算原材料消耗
        Map<Item, Integer> cost = calculateCost(rawSteps, available);

        return new RecipeChain(target, targetCount, orderedSteps, cost);
    }

    // ─── 配方选择 ───────────────────────────────────────────────

    private static CraftingRecipe selectBestRecipe(
            List<CraftingRecipe> recipes, Map<Item, Integer> available,
            RecipeIndex index, RegistryAccess registryAccess) {

        if (recipes.size() == 1) return recipes.get(0);

        CraftingRecipe best = recipes.get(0);
        int bestScore = score(best, available, index, registryAccess);

        for (int i = 1; i < recipes.size(); i++) {
            int s = score(recipes.get(i), available, index, registryAccess);
            if (s > bestScore) {
                best = recipes.get(i);
                bestScore = s;
            }
        }
        LittleMaidMoreAction.LOGGER.info("[RecipeTree] selectBestRecipe: {} recipes, best={} score={}",
                recipes.size(), best.getId(), bestScore);
        return best;
    }

    private static int score(CraftingRecipe recipe, Map<Item, Integer> available,
                              RecipeIndex index, RegistryAccess registryAccess) {
        int directMatch = 0;
        int producible = 0;
        int ingredientCount = 0;

        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;
            ingredientCount++;
            for (ItemStack match : ing.getItems()) {
                if (available.containsKey(match.getItem())) {
                    directMatch++;
                    break;
                }
                if (!index.recipesProducing(match.getItem()).isEmpty()) {
                    producible++;
                    break;
                }
            }
        }

        return directMatch * 100 + producible * 10 - ingredientCount;
    }

    // ─── 拓扑排序 ───────────────────────────────────────────────

    private static List<RecipeChain.RecipeStep> topologicalSort(List<RawStep> rawSteps) {
        Map<Item, RawStep> byOutput = new HashMap<>();
        for (RawStep rs : rawSteps) {
            byOutput.put(rs.outputItem, rs);
        }

        List<RecipeChain.RecipeStep> ordered = new ArrayList<>();
        Set<Item> produced = new HashSet<>();

        int prevSize = -1;
        while (ordered.size() < rawSteps.size()) {
            if (ordered.size() == prevSize) {
                LittleMaidMoreAction.LOGGER.debug("[RecipeTree] topological sort failed: cycle detected");
                return null;
            }
            prevSize = ordered.size();

            for (RawStep rs : rawSteps) {
                if (produced.contains(rs.outputItem)) continue;
                boolean depsMet = true;
                for (Item dep : rs.dependsOn) {
                    if (!produced.contains(dep) && byOutput.containsKey(dep)) {
                        depsMet = false;
                        break;
                    }
                }
                if (depsMet) {
                    ordered.add(new RecipeChain.RecipeStep(
                        rs.recipe, rs.outputItem,
                        rs.perCraft, rs.craftCount,
                        Set.copyOf(rs.dependsOn)));
                    produced.add(rs.outputItem);
                }
            }
        }

        return ordered;
    }

    // ─── 成本计算 ───────────────────────────────────────────────

    private static Map<Item, Integer> calculateCost(
            List<RawStep> rawSteps, Map<Item, Integer> available) {

        Map<Item, Integer> cost = new HashMap<>();

        for (RawStep rs : rawSteps) {
            for (Ingredient ing : rs.recipe.getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] matches = ing.getItems();
                if (matches.length == 0) continue;

                Item item = matches[0].getItem();
                int perCraft = matches[0].getCount();
                int total = rs.craftCount * perCraft;

                if (available.containsKey(item)) {
                    cost.merge(item, total, Integer::sum);
                }
            }
        }

        return cost;
    }

    // ─── 内部数据结构 ───────────────────────────────────────────

    private record Node(Item item, int needed, int depth) {}

    private record RawStep(
        CraftingRecipe recipe,
        Item outputItem,
        int perCraft,
        int craftCount,
        Set<Item> dependsOn
    ) {}
}
