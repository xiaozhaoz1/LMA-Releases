package littlemaidmoreaction.littlemaidmoreaction.core.expression;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.core.expression.ExpressionResolver;

import java.util.ServiceLoader;

/**
 * MVEL 模块引导器 — 条件性加载 MVEL 表达式引擎。
 *
 * <p>加载流程：
 * <ol>
 *   <li>模组初始化时调用 {@code tryLoad()}</li>
 *   <li>通过 ServiceLoader 查找 {@code MvelEvaluator} 的实现</li>
 *   <li>找到 → MVEL 库存在 → ExpressionResolver 启用 @{...} 语法</li>
 *   <li>未找到 → @{...} 表达式降级为占位符</li>
 * </ol>
 *
 * <p>此模式确保：
 * <ul>
 *   <li>核心引擎无 MVEL 传递依赖</li>
 *   <li>优雅降级，不影响规则引擎核心功能</li>
 * </ul>
 */
public final class MvelBootstrap {

    private static boolean initialized = false;
    private static boolean available = false;

    /**
     * 尝试加载 MVEL。幂等操作。
     * 在 FMLCommonSetupEvent 期间调用。
     */
    public static void tryLoad() {
        if (initialized) return;
        initialized = true;

        try {
            ServiceLoader<MvelEvaluator> loader = ServiceLoader.load(MvelEvaluator.class);
            MvelEvaluator evaluator = loader.findFirst().orElse(null);

            if (evaluator != null && evaluator.isAvailable()) {
                ExpressionResolver.setMvelAvailable(true);
                available = true;
                LittleMaidMoreAction.LOGGER.info(
                    "[MvelBootstrap] MVEL 表达式引擎已加载: {}",
                    evaluator.getClass().getName());
            } else {
                LittleMaidMoreAction.LOGGER.info(
                    "[MvelBootstrap] 未找到 MVEL 实现，"
                    + "@{...} 表达式将不可用（$var 语法正常）");
            }
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.warn(
                "[MvelBootstrap] MVEL 加载失败，降级为纯 $var 模式", e);
        }
    }

    public static boolean isAvailable() { return available; }
    private MvelBootstrap() {}
}
