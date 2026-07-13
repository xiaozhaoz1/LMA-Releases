package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import net.minecraft.world.entity.LivingEntity;
import java.util.Map;

/** 检查女仆能否寻路到目标实体。target 为 null 时返回 false。 */
@RuleCondition
public final class MaidCanPathToCondition implements ICondition {
    @Override public String key() { return "maid_can_path_to"; }
    @Override public String displayName() { return "可寻路到目标"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        LivingEntity target = ctx.target();
        if (target == null) return "false";
        return String.valueOf(ctx.maid().canPathReach(target));
    }
}
