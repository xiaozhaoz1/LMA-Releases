package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import java.util.Map;

/** 是否蓄力完成 — isCharged()。需附魔刀且未折断/封印。 */
@RuleCondition
public final class SlashbladeIsChargedCondition implements ICondition {
    @Override public String key() { return "slashblade_is_charged"; }
    @Override public String displayName() { return "拔刀剑蓄力完成"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        if (!(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return "false";
        return c.maid().getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .map(s -> String.valueOf(s.isCharged(c.maid()))).orElse("false");
    }
}
