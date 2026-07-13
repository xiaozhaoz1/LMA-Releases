package littlemaidmoreaction.littlemaidmoreaction.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RuleDef}、{@link ConditionDef}、{@link ActionStep}
 * 及 {@link ConditionOperator} 的单元测试。
 */
class ModelTest {

    // ==================== RuleDef ====================

    @Test
    @DisplayName("RuleDef minMatch ALL 模式返回条件总数")
    void minMatch_ALL() {
        var rule = new RuleDef(1, "test", true, "event", 1.0, 0, 0, MatchMode.ALL,
                List.of(new ConditionDef("a"), new ConditionDef("b")),
                List.of(new ActionStep("act")));
        assertEquals(2, rule.minMatch());
    }

    @Test
    @DisplayName("RuleDef minMatch ANY 模式返回 1")
    void minMatch_ANY() {
        var rule = new RuleDef(1, "test", true, "event", 1.0, 0, 0, MatchMode.ANY,
                List.of(new ConditionDef("a"), new ConditionDef("b")),
                List.of(new ActionStep("act")));
        assertEquals(1, rule.minMatch());
    }

    // ★ v10: ConditionNode 树形条件测试已移除 — 功能未使用

    // ==================== ConditionDef ====================

    @Test
    @DisplayName("ConditionDef 布尔条件和比较条件构造器")
    void conditionDefConstructors() {
        // 布尔条件 — 仅有 key
        var boolCond = new ConditionDef("is_on_fire");
        assertEquals("is_on_fire", boolCond.key());
        assertNull(boolCond.op());
        assertNull(boolCond.val());

        // 比较条件 — key op val
        var cmpCond = new ConditionDef("health_ratio", ":<:", "0.5");
        assertEquals("health_ratio", cmpCond.key());
        assertEquals(":<:", cmpCond.op());
        assertEquals("0.5", cmpCond.val());
    }

    // ==================== ConditionOperator ====================

    @Test
    @DisplayName("ConditionOperator EQ 智能类型比较")
    void conditionOperatorSmartEq() {
        // 数值近似比较
        assertTrue(ConditionOperator.EQ.test("0.5", "0.5"));
        assertTrue(ConditionOperator.EQ.test("100", "100.0000000001"));

        // 精确字符串
        assertTrue(ConditionOperator.EQ.test("hello", "hello"));
        assertFalse(ConditionOperator.EQ.test("hello", "world"));

        // 布尔字符串
        assertTrue(ConditionOperator.EQ.test("true", "true"));
    }

    @Test
    @DisplayName("ConditionOperator CONTAINS 子串匹配")
    void conditionOperatorContains() {
        assertTrue(ConditionOperator.CONTAINS.test("minecraft:zombie", "zombie"));
        assertFalse(ConditionOperator.CONTAINS.test("minecraft:zombie", "skeleton"));
    }

    @Test
    @DisplayName("ConditionOperator IN 列表匹配")
    void conditionOperatorIn() {
        assertTrue(ConditionOperator.IN.test("zombie", "zombie, skeleton, spider"));
        assertTrue(ConditionOperator.IN.test(" skeleton ", "zombie, skeleton, spider"));
        assertFalse(ConditionOperator.IN.test("creeper", "zombie, skeleton, spider"));
    }

    // ==================== ActionStep ====================

    @Test
    @DisplayName("ActionStep 携带参数构造")
    void actionStepWithParams() {
        var params = Map.of("anim_name", "execution", "speed", "1.5");
        var step = new ActionStep("play_anim", params);
        assertEquals("play_anim", step.typeId());
        assertEquals(2, step.params().size());
        assertEquals("execution", step.params().get("anim_name"));
        assertEquals("1.5", step.params().get("speed"));
    }

    // ==================== ActionStep ====================
}
