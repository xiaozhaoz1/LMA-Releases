package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import java.util.Map;

/**
 * 拔刀剑是否处于附魔(妖化)状态 — ISlashBladeState#isDefaultBewitched()。
 *
 * <h3>用法</h3>
 * <pre>条件: katana_is_bewitched                → 是否妖化</pre>
 */
@RuleCondition
public final class KatanaIsBewitchedCondition implements ICondition {
    @Override public String key() { return "katana_is_bewitched"; }
    @Override public String displayName() { return "拔刀剑附魔"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        if (!(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return "false";
        return String.valueOf(c.maid().getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .map(s -> s.isDefaultBewitched()).orElse(false));
    }
}
