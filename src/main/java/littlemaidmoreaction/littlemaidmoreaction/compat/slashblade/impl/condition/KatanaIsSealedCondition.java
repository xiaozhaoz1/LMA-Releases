package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import java.util.Map;

/** 拔刀剑是否封印 — ISlashBladeState#isSealed() */
@RuleCondition
public final class KatanaIsSealedCondition implements ICondition {
    @Override public String key() { return "katana_is_sealed"; }
    @Override public String displayName() { return "拔刀剑封印"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        if (!(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return "false";
        return String.valueOf(c.maid().getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .map(s -> s.isSealed()).orElse(false));
    }
}
