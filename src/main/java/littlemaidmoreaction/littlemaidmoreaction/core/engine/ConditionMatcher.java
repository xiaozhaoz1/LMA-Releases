package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import java.util.Map;

import littlemaidmoreaction.littlemaidmoreaction.core.cache.ConditionCache;
import littlemaidmoreaction.littlemaidmoreaction.core.debug.RuleTracer;
import littlemaidmoreaction.littlemaidmoreaction.core.model.*;

/**
 * 条件匹配器 — 统一的条件评估，支持扁平列表匹配。
 *
 * <p>使用 conditions + matchMode (ALL/ANY) 匹配。</p>
 */
public final class ConditionMatcher {

    /**
     * 检查规则的所有条件是否满足。
     *
     * @param rule  规则定义
     * @param cache 条件缓存（同一事件中复用）
     * @return true 表示条件满足
     */
    public static boolean matches(RuleDef rule, ConditionCache cache) {
        return evaluateFlat(rule.conditions(), rule.minMatch(), cache);
    }

    /** 扁平列表匹配（matchMode: ALL/ANY） */
    private static boolean evaluateFlat(java.util.List<ConditionDef> conditions,
                                         int required, ConditionCache cache) {
        int matched = 0;
        for (ConditionDef cond : conditions) {
            boolean ok = evaluate(cond, cache);
            if (ok) {
                matched++;
                if (matched >= required) return true;
            }
        }
        return matched >= required;
    }

    /** 单条件评估 */
    static boolean evaluate(ConditionDef cond, ConditionCache cache) {
        // 1. 获取条件值（从缓存，有参数时绕过缓存）
        Map<String, String> params = cond.params();
        String rawValue = (params != null && !params.isEmpty())
                ? cache.get(cond.key(), params)
                : cache.get(cond.key());

        // 2. keyMath 运算
        String processed = applyMath(rawValue, cond.keyMath(), cond.keyMathVal());

        // 3. 布尔条件（无操作符）
        if (cond.op() == null) {
            boolean result = Boolean.parseBoolean(processed);
            RuleTracer.addCondition(cond.key(), result, processed, "true", null);
            return result;
        }

        // 4. $引用解析
        String expected = cond.val();
        if (expected != null && expected.startsWith("$")) {
            String refKey = expected.substring(1);
            expected = cache.get(refKey);
        }

        // 5. valMath 运算
        expected = applyMath(expected, cond.valMath(), cond.valMathVal());

        // 6. 操作符比较
        boolean result = ConditionOperator.fromToken(cond.op()).test(processed, expected);
        RuleTracer.addCondition(cond.key(), result, processed, expected != null ? expected : "true", cond.op());
        return result;
    }

    /** math 运算 (v10: package-private — ConditionEvaluator 也使用) */
    static String applyMath(String value, String op, String operand) {
        if (op == null || operand == null || value == null) return value;
        try {
            double v = Double.parseDouble(value);
            double o = Double.parseDouble(operand);
            return String.valueOf(switch (op) {
                case "+" -> v + o; case "-" -> v - o;
                case "*" -> v * o;
                case "/" -> o != 0 ? v / o : v;
                default -> v;
            });
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private ConditionMatcher() {}
}
