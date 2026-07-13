package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import java.util.Map;
/** 女仆正在骑乘实体。 */
@RuleCondition
public final class MaidIsRidingCondition implements ICondition {
    @Override public String key() { return "maid_is_riding"; }
    @Override public String displayName() { return "女仆骑乘"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return String.valueOf(MaidStateReader.isRiding(ctx.maid()));
    }
}
