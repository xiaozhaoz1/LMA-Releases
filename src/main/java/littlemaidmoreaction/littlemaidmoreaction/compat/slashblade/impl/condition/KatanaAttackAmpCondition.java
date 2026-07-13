package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import java.util.Map;

/**
 * 拔刀剑攻击倍率 — ISlashBladeState#getAttackAmplifier()。
 *
 * <h3>说明</h3>
 * <p>受炼狱等级、附魔、SA 状态等影响。基础值为 1.0。</p>
 *
 * <h3>用法</h3>
 * <pre>条件: katana_attack_amp :>:= 1.5      → 倍率 >= 1.5</pre>
 */
@RuleCondition
public final class KatanaAttackAmpCondition implements ICondition {
    @Override public String key() { return "katana_attack_amp"; }
    @Override public String displayName() { return "拔刀剑攻击倍率"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        if (!(c.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return "0";
        return c.maid().getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
            .map(s -> String.format("%.2f", s.getAttackAmplifier())).orElse("0");
    }
}
