package littlemaidmoreaction.littlemaidmoreaction.core.cache;

import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.storage.MaidRuleStorage;

import java.util.*;

/**
 * v8.7 每女仆规则事件索引 — O(1) 按女仆 UUID + 事件 ID 查找候选规则。
 *
 * <p>结构与 {@link RuleIndex} 相同，增加外层 {@code UUID} 键。
 * 每次该女仆的规则保存后调用 {@link #rebuild(UUID)} 重建对应条目。</p>
 */
public final class MaidRuleIndex {
    private static volatile Map<UUID, Map<String, List<RuleDef>>> INDEX = Map.of();

    /**
     * 重建指定女仆的事件索引。
     */
    public static void rebuild(UUID uuid) {
        List<RuleDef> rules = MaidRuleStorage.getRules(uuid);
        Map<UUID, Map<String, List<RuleDef>>> index = new HashMap<>(INDEX);

        if (rules.isEmpty()) {
            index.remove(uuid);
        } else {
            Map<String, List<RuleDef>> byEvent = new HashMap<>();
            for (RuleDef rule : rules) {
                byEvent.computeIfAbsent(rule.eventId(), k -> new ArrayList<>()).add(rule);
            }
            for (List<RuleDef> list : byEvent.values()) {
                list.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
            }
            index.put(uuid, Collections.unmodifiableMap(byEvent));
        }

        INDEX = Collections.unmodifiableMap(index);
    }

    /**
     * O(1) 查找指定女仆的指定事件候选规则列表（已按优先级降序）。
     * 首次访问时从 {@link MaidRuleStorage} 懒加载。
     */
    public static List<RuleDef> getByEvent(UUID uuid, String eventId) {
        Map<String, List<RuleDef>> maidIndex = INDEX.get(uuid);
        if (maidIndex == null) {
            // 懒加载：首次访问该女仆时从磁盘读取规则文件
            rebuild(uuid);
            maidIndex = INDEX.get(uuid);
            if (maidIndex == null) return List.of();
        }
        return maidIndex.getOrDefault(eventId, List.of());
    }

    private MaidRuleIndex() {}
}
