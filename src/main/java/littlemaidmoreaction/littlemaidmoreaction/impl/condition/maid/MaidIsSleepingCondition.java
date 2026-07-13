package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import java.util.Map;
@RuleCondition
public final class MaidIsSleepingCondition implements ICondition {
    @Override public String key() { return "maid_is_sleeping"; }
    @Override public String displayName() { return "女仆睡觉"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.valueOf(MaidStateReader.isSleeping(ctx.maid())); }
}
