package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import java.util.Map;

/** 拔刀剑锁定目标 entityId — getTargetEntityId()。 */
@RuleCondition
public final class SlashbladeTargetIdCondition implements ICondition {
    @Override public String key() { return "slashblade_target_id"; }
    @Override public String displayName() { return "拔刀剑锁定目标ID"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        if (!(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return "-1";
        return c.maid().getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .map(s -> String.valueOf(s.getTargetEntityId())).orElse("-1");
    }
}
