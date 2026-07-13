package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import net.mrqx.slashblade.maidpower.item.SlashBladeMaidBauble;
import java.util.Map;

/**
 * 女仆是否装备"空中连段"(Air Combo) 饰品。
 *
 * <h3>效果</h3>
 * <p>允许女仆执行上挑斩(Upper Slash Jump)，
 * 将空中敌人拉回地面继续连段。</p>
 *
 * <h3>用法</h3>
 * <pre>条件: tpm_has_air_combo</pre>
 */
@RuleCondition
public final class TpmHasAirComboCondition implements ICondition {
    @Override public String key() { return "tpm_has_air_combo"; }
    @Override public String displayName() { return "空中连段"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) { return String.valueOf(SlashBladeMaidBauble.AirCombo.checkBauble(c.maid())); }
}
