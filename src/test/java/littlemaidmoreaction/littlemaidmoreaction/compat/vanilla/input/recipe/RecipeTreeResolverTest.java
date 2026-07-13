package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecipeTreeResolver + RecipeIndex + RecipeChain 数据结构测试。
 *
 * <p><b>注意</b>：本测试不引用 {@code Items.*} 等静态字段（触发 Minecraft 注册表初始化）。
 * 完整配方链算法测试需要在游戏内运行（集成测试）。
 */
class RecipeTreeResolverTest {

    // ─── RecipeIndex 查询测试 ────────────────────────────────────

    @Test
    void recipeIndex_emptyByDefault() {
        RecipeIndex idx = new RecipeIndex(Map.of(), Map.of(), Map.of());
        assertEquals(0, idx.size());
        assertTrue(idx.allCrafting().isEmpty());
        assertTrue(idx.byKey(ResourceLocation.tryParse("minecraft:stick")).isEmpty());
    }

    @Test
    void recipeIndex_cacheIsIndependent() {
        RecipeIndex idx1 = new RecipeIndex(Map.of(), Map.of(), Map.of());
        RecipeIndex idx2 = new RecipeIndex(Map.of(), Map.of(), Map.of());
        assertNotSame(idx1, idx2);
        assertEquals(0, idx1.size());
        assertEquals(0, idx2.size());
    }

    // ─── RecipeChain 数据结构测试 ─────────────────────────────────

    @Test
    void recipeChain_emptySteps() {
        RecipeChain chain = new RecipeChain(null, 4, List.of(), Map.of());
        assertNull(chain.target());
        assertEquals(4, chain.targetCount());
        assertEquals(0, chain.stepCount());
        assertTrue(chain.steps().isEmpty());
        assertTrue(chain.cost().isEmpty());
    }

    // ─── RecipeTreeResolver 空指针防护测试 ────────────────────────

    @Test
    void resolve_nullTarget_returnsNull() {
        RecipeIndex idx = new RecipeIndex(Map.of(), Map.of(), Map.of());
        assertNull(RecipeTreeResolver.resolve(null, 1, Map.of(), idx, 10, null));
    }

    @Test
    void resolve_zeroCount_returnsNull() {
        RecipeIndex idx = new RecipeIndex(Map.of(), Map.of(), Map.of());
        assertNull(RecipeTreeResolver.resolve(null, 0, Map.of(), idx, 10, null));
    }

    @Test
    void resolve_nullAvailable_returnsNull() {
        RecipeIndex idx = new RecipeIndex(Map.of(), Map.of(), Map.of());
        assertNull(RecipeTreeResolver.resolve(null, 1, null, idx, 10, null));
    }

    @Test
    void resolve_nullIndex_returnsNull() {
        assertNull(RecipeTreeResolver.resolve(null, 1, Map.of(), null, 10, null));
    }

    @Test
    void resolve_maxDepthZero_returnsNull() {
        RecipeIndex idx = new RecipeIndex(Map.of(), Map.of(), Map.of());
        assertNull(RecipeTreeResolver.resolve(null, 4, Map.of(), idx, 0, null));
    }

    // ─── 默认常量测试 ─────────────────────────────────────────────

    @Test
    void defaultMaxDepth_isPositive() {
        assertTrue(RecipeTreeResolver.DEFAULT_MAX_DEPTH > 0);
        assertEquals(10, RecipeTreeResolver.DEFAULT_MAX_DEPTH);
    }
}
