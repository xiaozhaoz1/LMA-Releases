package littlemaidmoreaction.littlemaidmoreaction.core.expression;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;

/**
 * MVEL 表达式求值器接口 — SPI 契约。
 *
 * <p>当 MVEL 库在 classpath 时，MvelEvaluatorImpl 实现此接口。
 * 通过 ServiceLoader 加载，核心引擎无 MVEL 传递依赖。
 */
public interface MvelEvaluator {
    /** 求值 MVEL 表达式 */
    String eval(String expression, RuleContext ctx);

    /** 引擎是否可用 */
    boolean isAvailable();
}
