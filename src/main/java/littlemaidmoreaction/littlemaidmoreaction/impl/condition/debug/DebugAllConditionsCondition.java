package littlemaidmoreaction.littlemaidmoreaction.impl.condition.debug;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.*;

/**
 * DEBUG 条件 — 一次性评测所有已注册条件并输出日志。
 *
 * <p>始终返回 "true"，副作用是遍历 {@link ConditionRegistry#getAll()}
 * 对每个条件调用 {@code evaluate()}，输出 OK/FAIL/ERROR 日志。
 */
@RuleCondition
public final class DebugAllConditionsCondition implements ICondition {

    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.BoolParam("verbose", "详细模式", false)
    );

    @Override public String key() { return "debug_conditions"; }
    @Override public String displayName() { return "DEBUG: 测试全部条件"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        boolean verbose = "true".equalsIgnoreCase(rawParams.get("verbose"));
        Collection<ICondition> all = ConditionRegistry.getAll();
        int ok = 0, fail = 0, skip = 0;

        for (ICondition cond : all) {
            if (cond.key().equals("debug_conditions")) { skip++; continue; }
            try {
                String result = cond.evaluate(ctx, Map.of());
                if (verbose) {
                    LittleMaidMoreAction.LOGGER.debug("[LMA DEBUG] condition={} = {}",
                        cond.key(), result);
                }
                ok++;
            } catch (Exception e) {
                LittleMaidMoreAction.LOGGER.warn("[LMA DEBUG] condition={} FAIL: {}",
                    cond.key(), e.toString());
                fail++;
            }
        }

        LittleMaidMoreAction.LOGGER.info("[LMA DEBUG] conditions done: {} OK, {} FAIL, {} SKIP",
            ok, fail, skip);
        return "true";  // 始终满足
    }
}
