package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import java.util.Map;
@RuleCondition
public final class MaidMainhandCondition implements ICondition {
    @Override public String key() { return "maid_mainhand"; }
    @Override public String displayName() { return "主手物品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) { return MaidStateReader.getMainhandId(ctx.maid()); }
}

