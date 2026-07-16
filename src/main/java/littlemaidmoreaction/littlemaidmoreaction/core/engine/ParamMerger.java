package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.core.expression.ExpressionResolver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 参数合并器 — 三层合并：默认值 → 用户覆盖 → $expr 表达式求值。
 *
 * <p>优先级（低→高）：
 * <ol>
 *   <li>TypedParam.defaultValue() — 参数默认值</li>
 *   <li>ActionStep.params() — 用户在 JSON 中配置的覆盖值</li>
 *   <li>$keyName 表达式求值 — 运行时动态值</li>
 * </ol>
 */
public final class ParamMerger {

    /**
     * 合并参数。
     *
     * @param action 动作定义（含参数 schema）
     * @param step   当前动作步骤（含用户配置的参数）
     * @param ctx    规则上下文
     * @return 合并后的参数 Map（值已求完 $expr）
     */
    public static Map<String, String> merge(IAction action, ActionStep step, RuleContext ctx) {
        Map<String, String> merged = new LinkedHashMap<>();

        // Layer 1: 默认值
        for (TypedParam<?> param : action.params()) {
            merged.put(param.name(), String.valueOf(param.defaultValue()));
        }

        // Layer 2: 用户覆盖（JSON 中配置的值）
        if (!step.params().isEmpty()) {
            step.params().forEach(merged::put);
        }

        // ★ Bug #69 fix: v16 预设模板用 "target" 键，动作代码读 "item_id" / "music_name"
        // 自动映射 target → item_id / music_name，向后兼容新旧模板
        if (merged.containsKey("target")) {
            String target = merged.get("target");
            merged.putIfAbsent("item_id", target);
            merged.putIfAbsent("music_name", target);
        }

        // Layer 3: $expr 表达式求值
        merged.replaceAll((k, v) ->
            ExpressionResolver.resolve(v, ctx));

        return merged;
    }

    private ParamMerger() {}
}
