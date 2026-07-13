package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import java.util.Map;

/** 检查女仆是否手持拔刀剑 — 直接 instanceof ItemSlashBlade。 */
@RuleCondition
public final class IsHoldingKatanaCondition implements ICondition {
    @Override public String key() { return "is_holding_katana"; }
    @Override public String displayName() { return "手持拔刀剑"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        return String.valueOf(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade);
    }
}
