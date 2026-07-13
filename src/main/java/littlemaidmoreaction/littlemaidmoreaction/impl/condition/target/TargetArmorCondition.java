package littlemaidmoreaction.littlemaidmoreaction.impl.condition.target;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.target.TargetStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
@RuleCondition
public final class TargetArmorCondition implements ICondition {
    @Override public String key() { return "target_armor"; }
    @Override public String displayName() { return "目标护甲"; }
    @Override public ConditionCategory category() { return ConditionCategory.TARGET; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return ctx.target()!=null?String.valueOf(TargetStateReader.getArmor(ctx.target())):"0"; }
}
