package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
@RuleCondition
public final class OwnerHasAttackTargetCondition implements ICondition {
    @Override public String key() { return "owner_has_attack_target"; }
    @Override public String displayName() { return "主人在攻击"; }
    @Override public ConditionCategory category() { return ConditionCategory.OWNER; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.valueOf(MaidStateReader.ownerHasAttackTarget(ctx.maid())); }
}
