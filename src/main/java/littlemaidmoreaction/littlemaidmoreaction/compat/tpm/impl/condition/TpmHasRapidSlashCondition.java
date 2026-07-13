package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import net.mrqx.slashblade.maidpower.item.SlashBladeMaidBauble;
import java.util.Map;

/** 疾走居合饰品 — 解锁远程冲刺攻击。 */
@RuleCondition
public final class TpmHasRapidSlashCondition implements ICondition {
    @Override public String key() { return "tpm_has_rapid_slash"; }
    @Override public String displayName() { return "TPM疾走居合饰品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) { return String.valueOf(SlashBladeMaidBauble.RapidSlash.checkBauble(c.maid())); }
}
