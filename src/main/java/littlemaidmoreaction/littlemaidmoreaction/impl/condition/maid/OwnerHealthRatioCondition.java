package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
@RuleCondition
public final class OwnerHealthRatioCondition implements ICondition {
    @Override public String key() { return "owner_health_ratio"; }
    @Override public String displayName() { return "主人生命比例"; }
    @Override public ConditionCategory category() { return ConditionCategory.OWNER; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.format("%.2f", MaidStateReader.getOwnerHealthRatio(ctx.maid())); }
}
