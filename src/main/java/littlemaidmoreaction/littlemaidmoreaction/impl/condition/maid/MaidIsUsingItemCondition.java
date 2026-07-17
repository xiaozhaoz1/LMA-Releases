package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.maid.MaidStateReader;
import java.util.Map;
@RuleCondition
public final class MaidIsUsingItemCondition implements ICondition {
    @Override public String key() { return "maid_is_using_item"; }
    @Override public String displayName() { return "女仆使用物品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.valueOf(MaidStateReader.isUsingItem(ctx.maid())); }
}
