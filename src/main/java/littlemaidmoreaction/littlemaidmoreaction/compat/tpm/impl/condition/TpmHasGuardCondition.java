package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import net.mrqx.slashblade.maidpower.item.SlashBladeMaidBauble;
import java.util.Map;

/** 格挡饰品 — 受伤累积自动格挡反制。 */
@RuleCondition
public final class TpmHasGuardCondition implements ICondition {
    @Override public String key() { return "tpm_has_guard"; }
    @Override public String displayName() { return "TPM格挡饰品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) { return String.valueOf(SlashBladeMaidBauble.Guard.checkBauble(c.maid())); }
}
