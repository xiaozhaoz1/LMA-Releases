package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
/** 女仆模型ID (getModelId)。 */
@RuleCondition
public final class MaidModelIdCondition implements ICondition {
    @Override public String key() { return "maid_model_id"; }
    @Override public String displayName() { return "女仆模型"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return MaidStateReader.getModelId(ctx.maid());
    }
}
