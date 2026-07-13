package littlemaidmoreaction.littlemaidmoreaction.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleDefTest {

    @Test
    @DisplayName("simple() factory sets defaults: enabled, chance=1.0, cooldown=0, priority=0, ALL")
    void simple_factory() {
        var rule = RuleDef.simple(1, "test", "maid_tick",
            List.of(new ConditionDef("is_on_fire")),
            List.of(new ActionStep("play_anim")));
        assertTrue(rule.enabled());
        assertEquals(1.0, rule.chance());
        assertEquals(0, rule.cooldown());
        assertEquals(0, rule.priority());
        assertEquals(MatchMode.ALL, rule.matchMode());
    }

    @Test
    @DisplayName("full() factory with compat parameter")
    void full_withCompat() {
        var rule = RuleDef.full(200, "ysm-rule", "maid_interact",
            0.5, 100, 50, MatchMode.ALL,
            List.of(), List.of(),
            List.of("ysm"));
        assertEquals(200, rule.id());
        assertEquals("ysm-rule", rule.name());
        assertEquals(0.5, rule.chance());
        assertEquals(100, rule.cooldown());
        assertEquals(50, rule.priority());
        assertEquals(List.of("ysm"), rule.compat());
    }

    @Test
    @DisplayName("null compat defaults to empty list")
    void nullCompat_defaults() {
        var rule = new RuleDef(1, "t", true, "e", 1.0, 0, 0,
            MatchMode.ALL, List.of(), List.of(), null);
        assertEquals(List.of(), rule.compat());
    }

    @Test
    @DisplayName("null conditions defaults to empty list")
    void nullConditions_defaults() {
        var rule = new RuleDef(1, "t", true, "e", 1.0, 0, 0,
            MatchMode.ALL, null, List.of());
        assertEquals(List.of(), rule.conditions());
    }

    @Test
    @DisplayName("minMatch ALL returns condition count")
    void minMatch_all() {
        var rule = RuleDef.simple(1, "t", "e",
            List.of(new ConditionDef("a"), new ConditionDef("b")),
            List.of());
        assertEquals(2, rule.minMatch());
    }

    @Test
    @DisplayName("minMatch ANY returns 1")
    void minMatch_any() {
        var rule = RuleDef.full(1, "t", "e", 1.0, 0, 0, MatchMode.ANY,
            List.of(new ConditionDef("a"), new ConditionDef("b")),
            List.of());
        assertEquals(1, rule.minMatch());
    }
}
