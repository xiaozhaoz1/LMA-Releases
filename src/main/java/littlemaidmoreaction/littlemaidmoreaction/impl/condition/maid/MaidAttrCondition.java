package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.attribute.MaidAttrRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/**
 * 女仆属性条件 — 读取 TLM 内置 + 原版属性值。
 *
 * <p>NUM 类型，支持全部比较操作符。示例：{@code maid_attr(attribute="attack_damage") :>: 5}</p>
 */
@RuleCondition
public final class MaidAttrCondition implements ICondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
            new TypedParam.StringParam("attribute", "属性", "attack_damage")
    );

    @Override public String key() { return "maid_attr"; }
    @Override public String displayName() { return "女仆属性"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        String key = rawParams.getOrDefault("attribute", "attack_damage");
        MaidAttrRegistry.Entry entry = MaidAttrRegistry.getDef(key);
        if (entry == null) return "0";

        return switch (entry.valueType()) {
            case "num"  -> String.format("%.2f", MaidAttrRegistry.get(ctx.maid(), key));
            case "bool" -> MaidAttrRegistry.get(ctx.maid(), key) != 0.0 ? "true" : "false";
            case "str"  -> String.valueOf(MaidAttrRegistry.get(ctx.maid(), key)); // 预留
            default     -> "0";
        };
    }
}
