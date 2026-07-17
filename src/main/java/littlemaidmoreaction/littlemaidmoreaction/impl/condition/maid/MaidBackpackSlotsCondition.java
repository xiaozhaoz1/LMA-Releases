package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;

/** 检查女仆背包的可用槽位数。NUM 类型，支持数值比较（如 {@code :>=: 27} 表示大背包以上）。 */
@RuleCondition
public final class MaidBackpackSlotsCondition implements ICondition {
    @Override public String key() { return "maid_backpack_slots"; }
    @Override public String displayName() { return "背包槽位数"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return String.valueOf(MaidStateReader.getBackpackSlots(ctx.maid()));
    }
}
