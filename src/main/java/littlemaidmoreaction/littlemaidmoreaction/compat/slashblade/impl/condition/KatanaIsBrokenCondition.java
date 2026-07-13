package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import java.util.Map;

/** 拔刀剑是否折断 — ISlashBladeState#isBroken() */
@RuleCondition
public final class KatanaIsBrokenCondition implements ICondition {
    @Override public String key() { return "katana_is_broken"; }
    @Override public String displayName() { return "拔刀剑折断"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        if (!(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return "false";
        return String.valueOf(c.maid().getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .map(s -> s.isBroken()).orElse(false));
    }
}
