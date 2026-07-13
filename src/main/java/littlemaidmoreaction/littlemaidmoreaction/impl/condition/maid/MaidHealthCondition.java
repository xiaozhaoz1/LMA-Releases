package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import java.util.Map;
@RuleCondition
public final class MaidHealthCondition implements ICondition {
    @Override public String key() { return "maid_health"; }
    @Override public String displayName() { return "女仆生命"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.format("%.1f", MaidStateReader.getHealth(ctx.maid())); }
}
