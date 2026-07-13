package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
import java.util.stream.Collectors;
@RuleCondition
public final class MaidMainhandTagCondition implements ICondition {
    @Override public String key() { return "maid_mainhand_tag"; }
    @Override public String displayName() { return "主手标签"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return ctx.maid().getMainHandItem().getTags().map(net.minecraft.tags.TagKey::location).map(Object::toString).collect(Collectors.joining(",")); }
}
