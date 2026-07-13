package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupBuilderTest {

    private static final String ACTION_A = "action_a";
    private static final String ACTION_B = "action_b";
    private static final String ACTION_C = "action_c";
    private static final String CONFLICTING = "conflicting";
    private static final Set<String> REGISTERED_IDS = Set.of(
        ACTION_A, ACTION_B, ACTION_C, CONFLICTING
    );

    @BeforeEach
    void setUp() {
        ActionRegistry.register(new TestAction(ACTION_A, "Action A"));
        ActionRegistry.register(new TestAction(ACTION_B, "Action B"));
        ActionRegistry.register(new TestAction(ACTION_C, "Action C"));
        ActionRegistry.register(new TestAction(CONFLICTING, "Conflicting With A",
            List.of(ACTION_A)));
    }

    @AfterEach
    void tearDown() {
        for (String id : REGISTERED_IDS) {
            ActionRegistry.unregister(id);
        }
    }

    @Test
    @DisplayName("simple sequence yields single parallel group")
    void simpleSequence_singleGroup() {
        List<ActionStep> steps = List.of(
            new ActionStep(ACTION_A),
            new ActionStep(ACTION_B),
            new ActionStep(ACTION_C)
        );
        List<ParallelGroup> groups = GroupBuilder.build(steps);
        assertEquals(1, groups.size());
        ParallelGroup group = groups.get(0);
        assertEquals(3, group.actions().size());
        assertEquals(ACTION_A, group.actions().get(0).typeId());
        assertEquals(ACTION_B, group.actions().get(1).typeId());
        assertEquals(ACTION_C, group.actions().get(2).typeId());
        assertFalse(group.isAsync());
        assertFalse(group.isConditional());
        assertFalse(group.isCancel());
    }

    @Test
    @DisplayName("WAIT splits [A, B, wait, C] into [[A,B], [wait], [C]]")
    void wait_splitsGroup() {
        List<ActionStep> steps = List.of(
            new ActionStep(ACTION_A),
            new ActionStep(ACTION_B),
            new ActionStep("wait"),
            new ActionStep(ACTION_C)
        );
        List<ParallelGroup> groups = GroupBuilder.build(steps);
        assertEquals(3, groups.size());
        ParallelGroup g0 = groups.get(0);
        assertEquals(2, g0.actions().size());
        assertEquals(ACTION_A, g0.actions().get(0).typeId());
        assertEquals(ACTION_B, g0.actions().get(1).typeId());
        assertFalse(g0.isAsync());
        ParallelGroup g1 = groups.get(1);
        assertTrue(g1.actions().isEmpty());
        assertTrue(g1.isAsync());
        assertEquals(2, g1.resumeIndex());
        assertFalse(g1.isCancel());
        ParallelGroup g2 = groups.get(2);
        assertEquals(1, g2.actions().size());
        assertEquals(ACTION_C, g2.actions().get(0).typeId());
        assertFalse(g2.isAsync());
    }

    @Test
    @DisplayName("REPEAT splits [A, repeat(5), C] into [[A], [repeat(5)], [C]]")
    void repeat_splitsGroup() {
        List<ActionStep> steps = List.of(
            new ActionStep(ACTION_A),
            new ActionStep("repeat", Map.of("count", "5")),
            new ActionStep(ACTION_C)
        );
        List<ParallelGroup> groups = GroupBuilder.build(steps);
        assertEquals(3, groups.size());
        ParallelGroup g0 = groups.get(0);
        assertEquals(1, g0.actions().size());
        assertEquals(ACTION_A, g0.actions().get(0).typeId());
        ParallelGroup g1 = groups.get(1);
        assertTrue(g1.actions().isEmpty());
        assertTrue(g1.isAsync());
        assertEquals(1, g1.resumeIndex());
        assertEquals(1, g1.repeatIdx());
        assertEquals(5, g1.repeatCount());
        ParallelGroup g2 = groups.get(2);
        assertEquals(1, g2.actions().size());
        assertEquals(ACTION_C, g2.actions().get(0).typeId());
    }

    @Test
    @DisplayName("CANCEL_EVENT splits [A, cancel_event, B] into [[A], [cancel], [B]]")
    void cancelEvent_splitsGroup() {
        List<ActionStep> steps = List.of(
            new ActionStep(ACTION_A),
            new ActionStep("cancel_event"),
            new ActionStep(ACTION_B)
        );
        List<ParallelGroup> groups = GroupBuilder.build(steps);
        assertEquals(3, groups.size());
        ParallelGroup g0 = groups.get(0);
        assertEquals(1, g0.actions().size());
        assertEquals(ACTION_A, g0.actions().get(0).typeId());
        ParallelGroup g1 = groups.get(1);
        assertTrue(g1.actions().isEmpty());
        assertTrue(g1.isCancel());
        assertFalse(g1.isAsync());
        ParallelGroup g2 = groups.get(2);
        assertEquals(1, g2.actions().size());
        assertEquals(ACTION_B, g2.actions().get(0).typeId());
    }

    @Test
    @DisplayName("conditional removed — treated as unknown, skipped")
    void conditionalRemoved_skipped() {
        ActionStep unknownStep = new ActionStep("conditional",
            Map.of("condition", "$health_ratio < 0.5"));
        List<ActionStep> steps = List.of(
            new ActionStep(ACTION_A),
            unknownStep,
            new ActionStep(ACTION_B)
        );
        List<ParallelGroup> groups = GroupBuilder.build(steps);
        // conditional removed → treated as unknown action → skipped
        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).actions().size());
        assertEquals(ACTION_A, groups.get(0).actions().get(0).typeId());
        assertEquals(ACTION_B, groups.get(0).actions().get(1).typeId());
    }

    @Test
    @DisplayName("conflicting actions split [A, conflicting] into [[A], [conflicting]]")
    void conflictingActions_splitGroup() {
        List<ActionStep> steps = List.of(
            new ActionStep(ACTION_A),
            new ActionStep(CONFLICTING)
        );
        List<ParallelGroup> groups = GroupBuilder.build(steps);
        assertEquals(2, groups.size());
        assertEquals(1, groups.get(0).actions().size());
        assertEquals(ACTION_A, groups.get(0).actions().get(0).typeId());
        assertEquals(1, groups.get(1).actions().size());
        assertEquals(CONFLICTING, groups.get(1).actions().get(0).typeId());
    }

    @Test
    @DisplayName("empty sequence yields empty list")
    void emptySequence_emptyList() {
        List<ParallelGroup> groups = GroupBuilder.build(List.of());
        assertTrue(groups.isEmpty());
    }

    @Test
    @DisplayName("unknown action type is skipped")
    void unknownAction_skipped() {
        List<ActionStep> steps = List.of(
            new ActionStep(ACTION_A),
            new ActionStep("unknown_type"),
            new ActionStep(ACTION_B)
        );
        List<ParallelGroup> groups = GroupBuilder.build(steps);
        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).actions().size());
        assertEquals(ACTION_A, groups.get(0).actions().get(0).typeId());
        assertEquals(ACTION_B, groups.get(0).actions().get(1).typeId());
    }

    private static class TestAction implements IAction {
        private final String id;
        private final String displayName;
        private final List<String> conflicts;

        TestAction(String id, String displayName) {
            this(id, displayName, List.of());
        }

        TestAction(String id, String displayName, List<String> conflicts) {
            this.id = id;
            this.displayName = displayName;
            this.conflicts = conflicts;
        }

        @Override
        public String id() { return id; }

        @Override
        public String displayName() { return displayName; }

        @Override
        public ActionCategory category() { return ActionCategory.MAID; }

        @Override
        public List<TypedParam<?>> params() { return List.of(); }

        @Override
        public List<String> conflicts() { return conflicts; }
    }
}
