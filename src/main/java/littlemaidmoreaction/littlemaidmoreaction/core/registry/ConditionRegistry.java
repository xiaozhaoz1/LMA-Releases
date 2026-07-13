package littlemaidmoreaction.littlemaidmoreaction.core.registry;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 条件注册中心 v5 — 多维度索引 + 静态条件筛选 + 运行时卸载。
 *
 * <p>接受 v5 {@code core.spi.condition.ICondition} SPI 实现。</p>
 */
public final class ConditionRegistry {
    private static final Map<String, ICondition> REGISTRY = new ConcurrentHashMap<>();

    /**
     * v5 SPI 入口 — 注册新的 {@link ICondition}。
     */
    public static void register(ICondition condition) {
        ICondition old = REGISTRY.put(condition.key(), condition);
        if (old != null) logOverwrite(condition.key(), old.getClass().getSimpleName(), condition.getClass().getSimpleName());
    }

    public static void unregister(String key) { REGISTRY.remove(key); }

    public static ICondition get(String key) { return REGISTRY.get(key); }
    public static boolean has(String key) { return REGISTRY.containsKey(key); }
    public static int size() { return REGISTRY.size(); }
    public static Collection<ICondition> getAll() { return List.copyOf(REGISTRY.values()); }
    public static Set<String> getAllKeys() { return Set.copyOf(REGISTRY.keySet()); }

    public static List<ICondition> getByCategory(ConditionCategory category) {
        return REGISTRY.values().stream().filter(c -> c.category() == category).toList();
    }

    public static List<ICondition> getByValueType(ConditionValueType type) {
        return REGISTRY.values().stream().filter(c -> c.valueType() == type).toList();
    }

    public static List<ICondition> getStaticConditions() {
        return REGISTRY.values().stream().filter(ICondition::isStatic).toList();
    }

    public static Map<ConditionCategory, List<ICondition>> getGroupedByCategory() {
        Map<ConditionCategory, List<ICondition>> grouped = new LinkedHashMap<>();
        for (ConditionCategory cat : ConditionCategory.values()) {
            List<ICondition> list = getByCategory(cat);
            if (!list.isEmpty()) grouped.put(cat, list);
        }
        return grouped;
    }

    private ConditionRegistry() {}

    private static void logOverwrite(String key, String oldName, String newName) {
        try {
            LittleMaidMoreAction.LOGGER.warn("[ConditionRegistry] key '{}' 覆盖: {} -> {}", key, oldName, newName);
        } catch (Exception | LinkageError ignored) {
            // LOGGER not available in test environments
        }
    }
}
