package littlemaidmoreaction.littlemaidmoreaction.impl.condition.target;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.target.TargetStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
@RuleCondition
public final class DistanceCondition implements ICondition {
    @Override public String key() { return "distance"; }
    @Override public String displayName() { return "距离"; }
    @Override public ConditionCategory category() { return ConditionCategory.TARGET; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) { return ctx.target() != null ? String.valueOf(TargetStateReader.getDistance(ctx.maid(), ctx.target())) : "999"; }
}

