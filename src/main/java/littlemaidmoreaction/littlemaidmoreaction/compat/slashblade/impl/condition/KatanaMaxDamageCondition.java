package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import java.util.Map;

/**
 * 拔刀剑最大伤害值 — ISlashBladeState#getMaxDamage()。
 *
 * <h3>用法</h3>
 * <pre>条件: katana_max_damage :>:= 200      → 最大伤害 >= 200</pre>
 */
@RuleCondition
public final class KatanaMaxDamageCondition implements ICondition {
    @Override public String key() { return "katana_max_damage"; }
    @Override public String displayName() { return "拔刀剑最大伤害"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        if (!(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return "0";
        return c.maid().getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .map(s -> String.valueOf(s.getMaxDamage())).orElse("0");
    }
}
