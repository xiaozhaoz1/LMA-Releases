package littlemaidmoreaction.littlemaidmoreaction.impl.condition.target;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.target.TargetStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
@RuleCondition
public final class TargetIsOnGroundCondition implements ICondition {
    @Override public String key() { return "target_is_on_ground"; }
    @Override public String displayName() { return "目标在地面"; }
    @Override public ConditionCategory category() { return ConditionCategory.TARGET; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.valueOf(ctx.target()!=null&&TargetStateReader.isOnGround(ctx.target())); }
}
