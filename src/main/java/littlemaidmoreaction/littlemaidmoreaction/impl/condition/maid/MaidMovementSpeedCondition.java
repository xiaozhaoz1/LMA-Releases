package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
@RuleCondition
public final class MaidMovementSpeedCondition implements ICondition {
    @Override public String key() { return "maid_movement_speed"; }
    @Override public String displayName() { return "女仆移动速度"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.format("%.3f", MaidStateReader.getMovementSpeed(ctx.maid())); }
}
