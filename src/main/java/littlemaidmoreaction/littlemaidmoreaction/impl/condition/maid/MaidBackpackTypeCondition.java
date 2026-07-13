package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;

/** 检查女仆当前背包类型 ID（如 small/medium/large/ender/crafting）。STR 类型，配合操作符精确匹配。 */
@RuleCondition
public final class MaidBackpackTypeCondition implements ICondition {
    @Override public String key() { return "maid_backpack_type"; }
    @Override public String displayName() { return "背包类型"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return MaidStateReader.getBackpackType(ctx.maid());
    }
}
