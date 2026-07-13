package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import java.util.Map;

/** 当前SA类型 — getSlashArtsKey()，如 slashblade:judgement_cut。 */
@RuleCondition
public final class SlashbladeSlashArtsCondition implements ICondition {
    @Override public String key() { return "slashblade_slash_arts"; }
    @Override public String displayName() { return "拔刀剑SA类型"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        if (!(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return "";
        return c.maid().getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .map(s -> { var k = s.getSlashArtsKey(); return k != null ? k.toString() : ""; }).orElse("");
    }
}
