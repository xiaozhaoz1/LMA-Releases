package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import net.mrqx.slashblade.maidpower.item.SlashBladeMaidBauble;
import java.util.Map;

/**
 * 女仆是否装备"无限剑制"(Unlimited Blade Works) 饰品。
 *
 * <h3>效果</h3>
 * <p>每次 SA 命中时在周围随机位置生成幻影剑攻击，
 * 同时提升 ProudSoul 掉落倍率。</p>
 *
 * <h3>用法</h3>
 * <pre>条件: tpm_has_ubw</pre>
 *
 * <h3>典型规则</h3>
 * <pre>
 * UBW 反击: maid_attack + tpm_has_ubw + is_holding_katana
 *   → cancel_event → slashblade_sa(knockback=smash, damage_mult=2.0)
 *   → play_sound(minecraft:entity.wither.shoot)
 * </pre>
 */
@RuleCondition
public final class TpmHasUBWCondition implements ICondition {
    @Override public String key() { return "tpm_has_ubw"; }
    @Override public String displayName() { return "UBW饰品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) { return String.valueOf(SlashBladeMaidBauble.UnlimitedBladeWorks.checkBauble(c.maid())); }
}
