package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
@RuleCondition
public final class OwnerArmorCondition implements ICondition {
    @Override public String key() { return "owner_armor"; }
    @Override public String displayName() { return "主人护甲"; }
    @Override public ConditionCategory category() { return ConditionCategory.OWNER; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.valueOf(MaidStateReader.getOwnerArmor(ctx.maid())); }
}
