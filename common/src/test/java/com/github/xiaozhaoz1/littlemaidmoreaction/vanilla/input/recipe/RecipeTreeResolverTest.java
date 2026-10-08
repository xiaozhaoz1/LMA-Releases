package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecipeTreeResolver + RecipeIndex + RecipeChain 数据结构测试 (纯 JVM 安全子集)。
 *
 * <p><b>可测性边界</b> (2026-08-11b 实测, 错题 #174): mock/子类化/实例化任何 MC 类型
 * (Item/ItemStack/Ingredient/CraftingRecipe) 都会触发其类初始化 → {@code FeatureElement
 * clinit → Registries.clinit → BuiltInRegistries 初始化失败} (纯 JVM 无注册表数据)。
 * 因此真实解析用例 (需 Item/配方实例) 在此铁律下不可行 — 测试仅覆盖:
 * <ul>
 *   <li>RecipeIndex 纯数据结构 (Map 驱动, 零 MC 类型实例)</li>
 *   <li>RecipeChain/RecipeStep 纯数据结构 (recipe/target 字段传 null — totalOutput 不依赖)</li>
 *   <li>resolve 的 null 防御首层 (null target — 不触 MC 类型实例化)</li>
 * </ul>
 * 真实解析算法路径 (单步/多步链/拓扑/深度) 依赖 MC 配方类型, 覆盖手段 = gametest
 * (有 MC 环境) — 见 COMMON.md §11 测试专项注记。
 */
class RecipeTreeResolverTest {

    // ─── RecipeIndex 查询测试 ────────────────────────────────────

    @Test
    @DisplayName("空索引: size 0 且查询恒空")
    void recipeIndex_emptyByDefault() {
        RecipeIndex idx = new RecipeIndex(Map.of(), Map.of(), Map.of());
        assertEquals(0, idx.size());
        assertTrue(idx.allCrafting().isEmpty());
        assertTrue(idx.byKey(ResourceLocation.tryParse("minecraft:stick")).isEmpty());
    }

    @Test
    @DisplayName("索引实例独立: 两次构造互不影响")
    void recipeIndex_cacheIsIndependent() {
        RecipeIndex idx1 = new RecipeIndex(Map.of(), Map.of(), Map.of());
        RecipeIndex idx2 = new RecipeIndex(Map.of(), Map.of(), Map.of());
        assertNotSame(idx1, idx2);
        assertEquals(0, idx1.size());
        assertEquals(0, idx2.size());
    }

    // ─── RecipeChain 数据结构测试 ─────────────────────────────────

    @Test
    @DisplayName("空步骤链: target/count/steps/cost 各字段语义")
    void recipeChain_emptySteps() {
        RecipeChain chain = new RecipeChain(null, 4, List.of(), Map.of());
        assertNull(chain.target());
        assertEquals(4, chain.targetCount());
        assertEquals(0, chain.stepCount());
        assertTrue(chain.steps().isEmpty());
        assertTrue(chain.cost().isEmpty());
    }

    @Test
    @DisplayName("RecipeStep 总产出 = outputCount × craftCount (recipe 字段不参与)")
    void recipeStep_totalOutput() {
        RecipeChain.RecipeStep step = new RecipeChain.RecipeStep(null, null, 4, 3, java.util.Set.of(), java.util.List.of());
        assertEquals(12, step.totalOutput());
    }

    // ─── RecipeTreeResolver null 防御 (唯一纯 JVM 可达路径) ────────

    @Test
    @DisplayName("null 目标 → null (其余参数防御依赖 MC 类型实例, 纯 JVM 不可达 — 见类注释)")
    void resolve_nullTarget_returnsNull() {
        RecipeIndex idx = new RecipeIndex(Map.of(), Map.of(), Map.of());
        assertNull(RecipeTreeResolver.resolve(null, 1, Map.of(), idx, 10, null));
    }

    // ─── 默认常量测试 ─────────────────────────────────────────────

    @Test
    @DisplayName("默认最大深度 = 10 (正数)")
    void defaultMaxDepth_isPositive() {
        assertTrue(RecipeTreeResolver.DEFAULT_MAX_DEPTH > 0);
        assertEquals(10, RecipeTreeResolver.DEFAULT_MAX_DEPTH);
    }
}
