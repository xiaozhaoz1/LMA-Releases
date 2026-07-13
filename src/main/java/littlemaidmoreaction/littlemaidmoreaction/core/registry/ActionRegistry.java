package littlemaidmoreaction.littlemaidmoreaction.core.registry;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动作注册中心 v5 — 冲突矩阵 + 分类索引 + 运行时卸载。
 *
 * <p>接受 v5 {@code core.spi.action.IAction} SPI 实现。</p>
 */
public final class ActionRegistry {
    private static final Map<String, IAction> REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> CONFLICT_MATRIX = new ConcurrentHashMap<>();

    /**
     * v5 SPI 入口 — 注册新的 {@link IAction}。
     */
    public static void register(IAction action) {
        IAction old = REGISTRY.put(action.id(), action);
        if (old != null) logOverwrite(action.id(), old.getClass().getSimpleName(), action.getClass().getSimpleName());
        if (!action.conflicts().isEmpty())
            CONFLICT_MATRIX.put(action.id(), Set.copyOf(action.conflicts()));
    }

    public static void unregister(String id) { REGISTRY.remove(id); CONFLICT_MATRIX.remove(id); }

    public static IAction get(String id) { return REGISTRY.get(id); }
    public static boolean has(String id) { return REGISTRY.containsKey(id); }
    public static int size() { return REGISTRY.size(); }
    public static Collection<IAction> getAll() { return List.copyOf(REGISTRY.values()); }
    public static Set<String> getAllIds() { return Set.copyOf(REGISTRY.keySet()); }

    public static boolean areConflicting(String id1, String id2) {
        Set<String> c = CONFLICT_MATRIX.getOrDefault(id1, Set.of());
        return c.contains(id2);
    }

    public static List<IAction> getByCategory(ActionCategory category) {
        return REGISTRY.values().stream().filter(a -> a.category() == category).toList();
    }

    public static Map<ActionCategory, List<IAction>> getGroupedByCategory() {
        Map<ActionCategory, List<IAction>> grouped = new LinkedHashMap<>();
        for (ActionCategory cat : ActionCategory.values()) {
            List<IAction> list = getByCategory(cat);
            if (!list.isEmpty()) grouped.put(cat, list);
        }
        return grouped;
    }

    private ActionRegistry() {}

    private static void logOverwrite(String id, String oldName, String newName) {
        try {
            LittleMaidMoreAction.LOGGER.warn("[ActionRegistry] ID '{}' 覆盖: {} -> {}", id, oldName, newName);
        } catch (Exception | LinkageError ignored) {
            // LOGGER not available in test environments
        }
    }
}
