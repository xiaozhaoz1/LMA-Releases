package littlemaidmoreaction.littlemaidmoreaction.core.cache;

import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;

import java.util.*;

/**
 * 规则事件索引 — O(1) 按事件 ID 查找候选规则。
 *
 * <p>替代旧 RuleMatcher 中 O(n) 遍历全部规则的模式。
 * 每次规则重载后调用 rebuild() 重建索引。
 */
public final class RuleIndex {

    private static volatile Map<String, List<RuleDef>> INDEX = Map.of();

    /** 重建索引（在规则加载/重载后调用） */
    public static void rebuild() {
        rebuild(RuleActionStorage.getRules());
    }

    /**
     * 从指定规则集合重建索引（包级可见，测试用）。
     *
     * <p>分离此方法使单元测试不依赖 {@link RuleActionStorage}（后者需要 Forge 运行时）。</p>
     */
    static void rebuild(Collection<RuleDef> rules) {
        Map<String, List<RuleDef>> index = new HashMap<>();
        for (RuleDef rule : rules) {
            index.computeIfAbsent(rule.eventId(), k -> new ArrayList<>()).add(rule);
        }
        // 每个事件列表按优先级降序排序（不变式，查找时无需再排序）
        for (List<RuleDef> list : index.values()) {
            list.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        }
        INDEX = Collections.unmodifiableMap(index);
    }

    /** O(1) 查找候选规则列表（已按优先级降序） */
    public static List<RuleDef> getByEvent(String eventId) {
        return INDEX.getOrDefault(eventId, List.of());
    }

    /** 所有已索引的事件 ID */
    public static Set<String> indexedEvents() { return INDEX.keySet(); }

    /** 索引中的规则总数 */
    public static int totalRules() {
        return INDEX.values().stream().mapToInt(List::size).sum();
    }

    private RuleIndex() {}
}
