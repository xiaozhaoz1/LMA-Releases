package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
@RuleCondition
public final class IsCriticalAttackCondition implements ICondition {
    @Override public String key() { return "is_critical_attack"; }
    @Override public String displayName() { return "暴击"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.valueOf(ctx.maid().isSprinting()); }
}
