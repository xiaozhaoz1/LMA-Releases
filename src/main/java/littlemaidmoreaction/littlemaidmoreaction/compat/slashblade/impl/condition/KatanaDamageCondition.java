package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import java.util.Map;

/**
 * 拔刀剑当前伤害值 — ISlashBladeState#getDamage()。
 *
 * <h3>与 katana_max_damage 的区别</h3>
 * <p>当前伤害受耐久度和折断状态影响。折断时返回 0。
 * 最大伤害是满耐久时的上限值。</p>
 *
 * <h3>用法</h3>
 * <pre>条件: katana_damage :>:= 100         → 伤害 >= 100</pre>
 */
@RuleCondition
public final class KatanaDamageCondition implements ICondition {
    @Override public String key() { return "katana_damage"; }
    @Override public String displayName() { return "拔刀剑伤害值"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        if (!(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return "0";
        return c.maid().getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .map(s -> String.valueOf(s.getDamage())).orElse("0");
    }
}
