package littlemaidmoreaction.littlemaidmoreaction.core.registry;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegistryTest {

    // ---------------------------------------------------------------------------
    //  ConditionRegistry  tests
    // ---------------------------------------------------------------------------

    @AfterEach
    void tearDown() {
        // 清理 ConditionRegistry
        for (String k : ConditionRegistry.getAllKeys()) {
            ConditionRegistry.unregister(k);
        }
        // 清理 ActionRegistry
        for (String id : ActionRegistry.getAllIds()) {
            ActionRegistry.unregister(id);
        }
    }

    @Test
    void testConditionRegisterAndGet() {
        ICondition c = mockCondition("health", ConditionCategory.MAID, ConditionValueType.NUM, false);
        ConditionRegistry.register(c);
        assertSame(c, ConditionRegistry.get("health"));
        assertTrue(ConditionRegistry.has("health"));
    }

    @Test
    void testConditionDuplicateKeyOverwrites() {
        ICondition c1 = mockCondition("key1", ConditionCategory.MAID, ConditionValueType.BOOL, false);
        ICondition c2 = mockCondition("key1", ConditionCategory.MAID, ConditionValueType.BOOL, false);
        ConditionRegistry.register(c1);
        ConditionRegistry.register(c2);
        assertSame(c2, ConditionRegistry.get("key1"));
        assertEquals(1, ConditionRegistry.size());
    }

    @Test
    void testConditionGetByCategory() {
        ConditionRegistry.register(mockCondition("a", ConditionCategory.MAID, ConditionValueType.NUM, false));
        ConditionRegistry.register(mockCondition("b", ConditionCategory.MAID, ConditionValueType.STR, false));
        ConditionRegistry.register(mockCondition("c", ConditionCategory.WORLD, ConditionValueType.NUM, false));

        List<ICondition> maid = ConditionRegistry.getByCategory(ConditionCategory.MAID);
        assertEquals(2, maid.size());

        List<ICondition> world = ConditionRegistry.getByCategory(ConditionCategory.WORLD);
        assertEquals(1, world.size());

        List<ICondition> target = ConditionRegistry.getByCategory(ConditionCategory.TARGET);
        assertTrue(target.isEmpty());
    }

    @Test
    void testConditionGetByValueType() {
        ConditionRegistry.register(mockCondition("a", ConditionCategory.MAID, ConditionValueType.BOOL, false));
        ConditionRegistry.register(mockCondition("b", ConditionCategory.MAID, ConditionValueType.NUM, false));
        ConditionRegistry.register(mockCondition("c", ConditionCategory.WORLD, ConditionValueType.BOOL, false));

        List<ICondition> bools = ConditionRegistry.getByValueType(ConditionValueType.BOOL);
        assertEquals(2, bools.size());

        List<ICondition> nums = ConditionRegistry.getByValueType(ConditionValueType.NUM);
        assertEquals(1, nums.size());

        List<ICondition> strs = ConditionRegistry.getByValueType(ConditionValueType.STR);
        assertTrue(strs.isEmpty());
    }

    @Test
    void testConditionGetAllKeys() {
        ConditionRegistry.register(mockCondition("k1", ConditionCategory.MAID, ConditionValueType.NUM, false));
        ConditionRegistry.register(mockCondition("k2", ConditionCategory.TARGET, ConditionValueType.BOOL, false));

        Set<String> keys = ConditionRegistry.getAllKeys();
        assertEquals(Set.of("k1", "k2"), keys);
    }

    @Test
    void testConditionUnregister() {
        ConditionRegistry.register(mockCondition("tmp", ConditionCategory.MAID, ConditionValueType.NUM, false));
        assertTrue(ConditionRegistry.has("tmp"));

        ConditionRegistry.unregister("tmp");
        assertFalse(ConditionRegistry.has("tmp"));
        assertNull(ConditionRegistry.get("tmp"));
    }

    @Test
    void testConditionGetStaticConditions() {
        ConditionRegistry.register(mockCondition("dyn", ConditionCategory.MAID, ConditionValueType.NUM, false));
        ConditionRegistry.register(mockCondition("stc", ConditionCategory.META, ConditionValueType.BOOL, true));

        List<ICondition> staticConds = ConditionRegistry.getStaticConditions();
        assertEquals(1, staticConds.size());
        assertEquals("stc", staticConds.get(0).key());

        Collection<ICondition> all = ConditionRegistry.getAll();
        assertEquals(2, all.size());
    }

    // ---------------------------------------------------------------------------
    //  ActionRegistry  tests
    // ---------------------------------------------------------------------------

    @Test
    void testActionRegisterAndGet() {
        IAction a = mockAction("attack", ActionCategory.COMBAT);
        ActionRegistry.register(a);
        assertSame(a, ActionRegistry.get("attack"));
        assertTrue(ActionRegistry.has("attack"));
    }

    @Test
    void testActionAreConflicting() {
        IAction a1 = mockAction("attack", ActionCategory.COMBAT);
        IAction a2 = mockAction("heal", ActionCategory.COMBAT, "attack");
        ActionRegistry.register(a1);
        ActionRegistry.register(a2);

        // a2 声明与 attack 冲突
        assertTrue(ActionRegistry.areConflicting("heal", "attack"));
        // attack 没有声明与 heal 冲突（单向声明）
        assertFalse(ActionRegistry.areConflicting("attack", "heal"));
        // 无冲突的动作
        assertFalse(ActionRegistry.areConflicting("attack", "nonexistent"));
    }

    @Test
    void testActionGetByCategory() {
        ActionRegistry.register(mockAction("atk1", ActionCategory.COMBAT));
        ActionRegistry.register(mockAction("atk2", ActionCategory.COMBAT));
        ActionRegistry.register(mockAction("move", ActionCategory.MOVEMENT));

        List<IAction> combat = ActionRegistry.getByCategory(ActionCategory.COMBAT);
        assertEquals(2, combat.size());

        List<IAction> movement = ActionRegistry.getByCategory(ActionCategory.MOVEMENT);
        assertEquals(1, movement.size());

        List<IAction> visual = ActionRegistry.getByCategory(ActionCategory.VISUAL);
        assertTrue(visual.isEmpty());
    }

    // ---------------------------------------------------------------------------
    //  Helper  factories  (anonymous  inner  classes)
    // ---------------------------------------------------------------------------

    private static ICondition mockCondition(String key,
                                            ConditionCategory cat,
                                            ConditionValueType valType,
                                            boolean isStatic) {
        return new ICondition() {
            @Override public String key() { return key; }
            @Override public String displayName() { return key; }
            @Override public ConditionCategory category() { return cat; }
            @Override public ConditionValueType valueType() { return valType; }
            @Override public boolean isStatic() { return isStatic; }
            @Override
            public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
                return "mock";
            }
        };
    }

    private static IAction mockAction(String id, ActionCategory cat) {
        return mockAction(id, cat, List.of());
    }

    private static IAction mockAction(String id, ActionCategory cat, String... conflicts) {
        return mockAction(id, cat, List.of(conflicts));
    }

    private static IAction mockAction(String id, ActionCategory cat, List<String> conflicts) {
        return new IAction() {
            @Override public String id() { return id; }
            @Override public String displayName() { return id; }
            @Override public ActionCategory category() { return cat; }
            @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() {
                return List.of();
            }
            @Override public List<String> conflicts() { return conflicts; }
        };
    }
}
