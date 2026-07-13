package littlemaidmoreaction.littlemaidmoreaction.impl.condition.target;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.target.TargetStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
/** 女仆能否看到目标 (canSee)。无目标返回false。 */
@RuleCondition
public final class CanSeeTargetCondition implements ICondition {
    @Override public String key() { return "can_see_target"; }
    @Override public String displayName() { return "视线可达"; }
    @Override public ConditionCategory category() { return ConditionCategory.TARGET; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return String.valueOf(ctx.target() != null && TargetStateReader.canSee(ctx.maid(), ctx.target()));
    }
}
