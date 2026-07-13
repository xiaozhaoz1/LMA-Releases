package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
/** 主人当前生命值。主人在线时返回生命值，否则返回0。 */
@RuleCondition
public final class OwnerHealthCondition implements ICondition {
    @Override public String key() { return "owner_health"; }
    @Override public String displayName() { return "主人生命"; }
    @Override public ConditionCategory category() { return ConditionCategory.OWNER; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return String.format("%.1f", MaidStateReader.getOwnerHealth(ctx.maid()));
    }
}
