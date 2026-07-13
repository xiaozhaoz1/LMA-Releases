package littlemaidmoreaction.littlemaidmoreaction.core.cache;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态条件预计算器 — 启动时预计算所有 isStatic=true 的条件值。
 *
 * <p>静态条件：值在加载时已知，运行时不变（如 mod_loaded, server_difficulty）。
 * 预计算后存入永久缓存，ConditionCache 查询时优先读取此缓存，
 * 避免每次事件触发时重新求值。
 */
public final class StaticPreEvaluator {

    /** 静态条件值缓存（加载时填充，运行时只读） */
    private static final Map<String, String> STATIC_VALUES = new ConcurrentHashMap<>();
    private static volatile boolean evaluated = false;

    /**
     * 预计算所有静态条件值。
     * 在模组初始化时、ConditionRegistry 填充后调用。
     * 幂等操作。
     */
    public static void evaluate() {
        if (evaluated) return;
        synchronized (StaticPreEvaluator.class) {
            if (evaluated) return;
            evaluated = true;

            int count = 0;
            for (ICondition cond : ConditionRegistry.getStaticConditions()) {
                try {
                    String value = cond.evaluate(null, Map.of());
                    STATIC_VALUES.put(cond.key(), value);
                    count++;
                    if (MoreActionConfig.DEBUG_MODE.get()) {
                        LittleMaidMoreAction.LOGGER.debug(
                            "[StaticPreEval] {} = {}", cond.key(), value);
                    }
                } catch (Exception e) {
                    LittleMaidMoreAction.LOGGER.warn(
                        "[StaticPreEval] 条件预计算失败: {}", cond.key(), e);
                    STATIC_VALUES.put(cond.key(), "0");
                }
            }

            LittleMaidMoreAction.LOGGER.info(
                "[StaticPreEval] 预计算完成: {} 个静态条件", count);
        }
    }

    /**
     * 查询静态条件值。
     * @return 预计算的值，若 key 非静态条件返回 null
     */
    public static String get(String key) {
        return STATIC_VALUES.get(key);
    }

    /** 是否已预计算 */
    public static boolean isEvaluated() { return evaluated; }

    /** 静态条件总数 */
    public static int count() { return STATIC_VALUES.size(); }

    private StaticPreEvaluator() {}
}
