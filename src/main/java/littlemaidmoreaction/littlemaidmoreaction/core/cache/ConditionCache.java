package littlemaidmoreaction.littlemaidmoreaction.core.cache;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.util.HashMap;
import java.util.Map;

/**
 * 条件求值缓存 — 同一事件处理周期内相同条件 key 只计算一次。
 *
 * <p>使用场景：多条规则可能引用相同条件 key（如 health_ratio）。
 * 在 findFirst() 遍历规则时，缓存避免对同一 maid 重复求值。
 *
 * <p>非线程安全，生命周期限定在单次 handleEvent() 调用内。
 */
public class ConditionCache {
    private final Map<String, String> cache = new HashMap<>();
    private final RuleContext ctx;

    public ConditionCache(RuleContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 获取条件值（带缓存）。
     *
     * @param key 条件 key，支持 "data:" 前缀读取 PersistentData
     * @return 条件值的字符串表示，未找到时返回 "0"
     */
    public String get(String key) {
        return cache.computeIfAbsent(key, k -> {
            // 1. 先查静态预计算缓存
            String staticVal = StaticPreEvaluator.get(k);
            if (staticVal != null) return staticVal;

            // 2. data: 前缀 → PersistentData
            if (k.startsWith("data:")) {
                String dataKey = k.substring(5);
                var pd = ctx.maid().getPersistentData();
                return pd.contains(dataKey) ? pd.get(dataKey).getAsString() : "0";
            }
            // 3. 注册表查询
            ICondition cond = ConditionRegistry.get(k);
            return cond != null ? cond.evaluate(ctx, Map.of()) : "0";
        });
    }

    /**
     * 获取条件值（带参数，不走缓存）。
     * 同key不同params结果不同，无法安全缓存，故直接调用 evaluate。
     */
    public String get(String key, Map<String, String> params) {
        if (params == null || params.isEmpty()) return get(key);
        String staticVal = StaticPreEvaluator.get(key);
        if (staticVal != null) return staticVal;
        if (key.startsWith("data:")) {
            var pd = ctx.maid().getPersistentData();
            return pd.contains(key.substring(5)) ? pd.get(key.substring(5)).getAsString() : "0";
        }
        ICondition cond = ConditionRegistry.get(key);
        return cond != null ? cond.evaluate(ctx, params) : "0";
    }

    /**
     * 清空缓存（事件处理完成后调用）。
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 当前缓存的条目数。
     *
     * @return 已缓存的条件 key 数量
     */
    public int size() {
        return cache.size();
    }
}
