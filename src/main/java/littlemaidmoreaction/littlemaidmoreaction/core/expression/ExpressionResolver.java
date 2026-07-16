package littlemaidmoreaction.littlemaidmoreaction.core.expression;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.ConditionEvaluator;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一 {@code $keyName op number} 表达式解析 (v35.1: 零 MC 依赖)。
 *
 * <p>消除 {@code RuleEngine.resolveExpr()} 与 {@code ConditionEvaluator.resolveKeyRef()}
 * 之间的重复 Pattern 和算术逻辑。
 * <p>v35.1: MC 便利重载已移至 engine/EngineUtils.resolveExpression()。
 */
public final class ExpressionResolver {

    private static final Pattern KEY_REF = Pattern.compile(
            "\\$([a-z_]+)(?:\\s*([+\\-*/])\\s*(\\d+\\.?\\d*))?");

    /** MVEL 表达式引擎可用开关（Phase 4 启用）。 */
    private static volatile boolean mvelAvailable = false;

    /** 使用 {@link RuleContext} 解析表达式 (v35.1: 通过 Function 委托)。 */
    public static String resolve(String expr, RuleContext ctx) {
        return resolve(expr, key -> ConditionEvaluator.resolveKey(key, ctx, Map.of()));
    }

    /**
     * ★ 无 Minecraft 依赖的解析重载。
     *
     * <p>将 key 解析逻辑通过 {@link Function} 注入，打破 core ↔ engine 循环依赖。
     *
     * @param expr        可能包含 {@code $health_ratio * 2} 的文本
     * @param keyResolver key → 运行时值的解析函数
     * @return 解析后的字符串，若不匹配模式则返回原始输入
     */
    public static String resolve(String expr, Function<String, String> keyResolver) {
        if (expr == null) return null;

        // MVEL @{...} 表达式检测
        if (mvelAvailable && expr.contains("@{")) {
            expr = expr.replaceAll("@\\{(.+?)\\}", "[MVEL:$1]");
        }

        Matcher m = KEY_REF.matcher(expr);
        if (!m.matches()) return expr;

        String resolved = keyResolver.apply(m.group(1));
        String mathOp = m.group(2);
        if (mathOp == null) return resolved;

        try {
            double d = Double.parseDouble(resolved);
            double n = Double.parseDouble(m.group(3));
            resolved = String.valueOf(switch (mathOp) {
                case "+" -> d + n;
                case "-" -> d - n;
                case "*" -> d * n;
                case "/" -> n != 0 ? d / n : d;
                default  -> d;
            });
        } catch (NumberFormatException ignored) { /* 非数字值不参与算术 */ }
        return resolved;
    }

    public static boolean isMvelAvailable() { return mvelAvailable; }
    public static void setMvelAvailable(boolean available) { mvelAvailable = available; }

    private ExpressionResolver() {} // 工具类
}
