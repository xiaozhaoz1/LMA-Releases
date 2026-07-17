package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.maid.MaidStateReader;
import java.util.Map;
/** 与主人的距离(格)。主人在线时返回距离，否则返回999。 */
@RuleCondition
public final class OwnerDistanceCondition implements ICondition {
    @Override public String key() { return "owner_distance"; }
    @Override public String displayName() { return "主人距离"; }
    @Override public ConditionCategory category() { return ConditionCategory.OWNER; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return String.format("%.1f", MaidStateReader.getOwnerDistance(ctx.maid()));
    }
}
