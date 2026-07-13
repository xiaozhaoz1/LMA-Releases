package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import littlemaidmoreaction.littlemaidmoreaction.core.cache.ConditionCache;
import littlemaidmoreaction.littlemaidmoreaction.core.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ConditionMatcher} 的单元测试。
 *
 * <p>使用简单的 HashMap 封装作为测试缓存，避免 Minecraft 依赖。</p>
 */
class ConditionMatcherTest {

    /**
     * 测试用 ConditionCache 实现 — 用给定 Map 做数据源。
     * 不依赖 Minecraft RuleContext / ConditionRegistry。
     */
    private static class TestCache extends ConditionCache {
        private final Map<String, String> delegate;

        TestCache(Map<String, String> data) {
            super(null);
            this.delegate = new HashMap<>(data);
        }

        @Override
        public String get(String key) {
            return delegate.getOrDefault(key, "0");
        }
    }

    // --- 布尔条件 ---

    @Test
    @DisplayName("boolean condition passes when value is true")
    void booleanCondition_true() {
        var cache = new TestCache(Map.of("is_on_fire", "true"));
        ConditionDef cond = new ConditionDef("is_on_fire");
        assertTrue(ConditionMatcher.evaluate(cond, cache));
    }

    @Test
    @DisplayName("boolean condition fails when value is false")
    void booleanCondition_false() {
        var cache = new TestCache(Map.of("is_on_fire", "false"));
        ConditionDef cond = new ConditionDef("is_on_fire");
        assertFalse(ConditionMatcher.evaluate(cond, cache));
    }

    // --- 比较操作符 ---

    @Test
    @DisplayName("comparison LT passes when value is less than threshold")
    void comparison_lt() {
        var cache = new TestCache(Map.of("health_ratio", "0.3"));
        ConditionDef cond = new ConditionDef("health_ratio", ":<:", "0.5");
        assertTrue(ConditionMatcher.evaluate(cond, cache));
    }

    @Test
    @DisplayName("comparison GT passes when value is greater than threshold")
    void comparison_gt() {
        var cache = new TestCache(Map.of("health_ratio", "0.8"));
        ConditionDef cond = new ConditionDef("health_ratio", ":>:", "0.5");
        assertTrue(ConditionMatcher.evaluate(cond, cache));
    }

    @Test
    @DisplayName("comparison EQ passes when value equals threshold")
    void comparison_eq() {
        var cache = new TestCache(Map.of("health_ratio", "0.5"));
        ConditionDef cond = new ConditionDef("health_ratio", ":=:", "0.5");
        assertTrue(ConditionMatcher.evaluate(cond, cache));
    }

    @Test
    @DisplayName("comparison NEQ passes when value differs from threshold")
    void comparison_neq() {
        var cache = new TestCache(Map.of("health_ratio", "0.3"));
        ConditionDef cond = new ConditionDef("health_ratio", ":!=:", "0.5");
        assertTrue(ConditionMatcher.evaluate(cond, cache));
    }

    // --- keyMath / valMath / $引用 ---

    @Test
    @DisplayName("keyMath subtraction transforms actual value before comparison")
    void keyMath() {
        // health_ratio=5, keyMath "-" "10" → -5, compare -5 < 0 → true
        // Without keyMath: 5 < 0 → false
        var cache = new TestCache(Map.of("health_ratio", "5"));
        ConditionDef cond = new ConditionDef("health_ratio", ":<:", "0", "-", "10", null, null);
        assertTrue(ConditionMatcher.evaluate(cond, cache));
    }

    @Test
    @DisplayName("valMath addition transforms expected value before comparison")
    void valMath() {
        // health_ratio=0.5, val=0.3, valMath "+" "0.3" → 0.6, 0.5 < 0.6 → true
        // Without valMath: 0.5 < 0.3 → false
        var cache = new TestCache(Map.of("health_ratio", "0.5"));
        ConditionDef cond = new ConditionDef("health_ratio", ":<:", "0.3", null, null, "+", "0.3");
        assertTrue(ConditionMatcher.evaluate(cond, cache));
    }

    @Test
    @DisplayName("$ reference resolves to another cached key value")
    void dollarReference() {
        // health_ratio=0.3, val=$threshold → cache.get("threshold")="0.5"
        var cache = new TestCache(Map.of("health_ratio", "0.3", "threshold", "0.5"));
        ConditionDef cond = new ConditionDef("health_ratio", ":<:", "$threshold");
        assertTrue(ConditionMatcher.evaluate(cond, cache));
    }

    // --- 扁平列表匹配 (matchMode ALL / ANY) ---

    @Test
    @DisplayName("flat ALL mode: passes when all conditions match")
    void flatMatch_all_allMatch() {
        var cache = new TestCache(Map.of("c1", "true", "c2", "true"));
        List<ConditionDef> conds = List.of(
            new ConditionDef("c1"),
            new ConditionDef("c2")
        );
        RuleDef rule = new RuleDef(1, "test", true, "event", 1.0, 0, 0,
            MatchMode.ALL, conds, List.of());
        assertTrue(ConditionMatcher.matches(rule, cache));
    }

    @Test
    @DisplayName("flat ALL mode: fails when not all conditions match")
    void flatMatch_all_someFail() {
        var cache = new TestCache(Map.of("c1", "true", "c2", "false", "c3", "true"));
        List<ConditionDef> conds = List.of(
            new ConditionDef("c1"),
            new ConditionDef("c2"),
            new ConditionDef("c3")
        );
        RuleDef rule = new RuleDef(1, "test", true, "event", 1.0, 0, 0,
            MatchMode.ALL, conds, List.of());
        assertFalse(ConditionMatcher.matches(rule, cache));
    }

    @Test
    @DisplayName("flat ANY mode: passes when first condition matches")
    void flatMatch_any_firstMatch() {
        var cache = new TestCache(Map.of("c1", "true", "c2", "false", "c3", "false"));
        List<ConditionDef> conds = List.of(
            new ConditionDef("c1"),
            new ConditionDef("c2"),
            new ConditionDef("c3")
        );
        RuleDef rule = new RuleDef(1, "test", true, "event", 1.0, 0, 0,
            MatchMode.ANY, conds, List.of());
        assertTrue(ConditionMatcher.matches(rule, cache));
    }

    @Test
    @DisplayName("flat ANY mode: passes when second condition matches after first fails")
    void flatMatch_any_secondMatch() {
        var cache = new TestCache(Map.of("c1", "false", "c2", "true", "c3", "false"));
        List<ConditionDef> conds = List.of(
            new ConditionDef("c1"),
            new ConditionDef("c2"),
            new ConditionDef("c3")
        );
        RuleDef rule = new RuleDef(1, "test", true, "event", 1.0, 0, 0,
            MatchMode.ANY, conds, List.of());
        assertTrue(ConditionMatcher.matches(rule, cache));
    }
}
