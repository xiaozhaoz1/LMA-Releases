package littlemaidmoreaction.littlemaidmoreaction.core.cache;

import littlemaidmoreaction.littlemaidmoreaction.core.model.MatchMode;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleIndex 单元测试 — 纯 Java，不依赖 Minecraft 运行时。
 *
 * <p>通过 {@link RuleIndex#rebuild(Collection)} 包级方法注入测试规则，
 * 避免对 {@code RuleActionStorage}（需要 Forge）和 Minecraft 文件系统的依赖。</p>
 */
class RuleIndexTest {

    @BeforeEach
    @SuppressWarnings("unchecked")
    void resetIndex() throws Exception {
        // 重置 INDEX 为空 Map，确保测试隔离
        Field indexField = RuleIndex.class.getDeclaredField("INDEX");
        indexField.setAccessible(true);
        indexField.set(null, Map.of());
    }

    // ===== 辅助构造 =====

    private static RuleDef rule(int id, String name, String eventId, int priority) {
        return new RuleDef(id, name, true, eventId, 1.0, 0, priority,
                MatchMode.ALL, List.of(), List.of(), List.of());
    }

    // ===== 测试用例 =====

    @Test
    void rebuildBuildsIndexFromRulesWithDifferentEventIds() {
        List<RuleDef> testRules = List.of(
                rule(1, "r1", "event_a", 10),
                rule(2, "r2", "event_b", 20),
                rule(3, "r3", "event_a", 30)
        );
        RuleIndex.rebuild(testRules);

        assertEquals(2, RuleIndex.indexedEvents().size(),
                "应有 2 个不同事件 ID");
        assertTrue(RuleIndex.indexedEvents().contains("event_a"),
                "应包含 event_a");
        assertTrue(RuleIndex.indexedEvents().contains("event_b"),
                "应包含 event_b");
        assertEquals(3, RuleIndex.totalRules(),
                "总规则数应为 3");
    }

    @Test
    void getByEventReturnsCorrectRulesForEachEventId() {
        List<RuleDef> testRules = List.of(
                rule(1, "r1", "event_a", 10),
                rule(2, "r2", "event_b", 20),
                rule(3, "r3", "event_a", 30)
        );
        RuleIndex.rebuild(testRules);

        List<RuleDef> eventARules = RuleIndex.getByEvent("event_a");
        assertEquals(2, eventARules.size(),
                "event_a 应有 2 条规则");
        assertTrue(eventARules.stream().allMatch(r -> r.eventId().equals("event_a")),
                "所有 event_a 规则应匹配事件 ID");

        List<RuleDef> eventBRules = RuleIndex.getByEvent("event_b");
        assertEquals(1, eventBRules.size(),
                "event_b 应有 1 条规则");
        assertEquals("event_b", eventBRules.get(0).eventId());
    }

    @Test
    void getByEventReturnsEmptyListForUnknownEventId() {
        List<RuleDef> result = RuleIndex.getByEvent("nonexistent_event");
        assertNotNull(result, "未知事件的返回不应为 null");
        assertTrue(result.isEmpty(), "未知事件应返回空列表");
    }

    @Test
    void rulesSortedByPriorityDescending() {
        List<RuleDef> testRules = List.of(
                rule(1, "low",    "event_x", 10),
                rule(2, "high",   "event_x", 100),
                rule(3, "medium", "event_x", 50)
        );
        RuleIndex.rebuild(testRules);

        List<RuleDef> result = RuleIndex.getByEvent("event_x");
        assertEquals(3, result.size());

        // 验证优先级降序: 100 > 50 > 10
        assertEquals(100, result.get(0).priority(), "第一条应为最高优先级");
        assertEquals(50, result.get(1).priority(), "第二条应为中优先级");
        assertEquals(10, result.get(2).priority(), "第三条应为最低优先级");

        // 验证 ID 对应
        assertEquals("high", result.get(0).name());
        assertEquals("medium", result.get(1).name());
        assertEquals("low", result.get(2).name());
    }
}
