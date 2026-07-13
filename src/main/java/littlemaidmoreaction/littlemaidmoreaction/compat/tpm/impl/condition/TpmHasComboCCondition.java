package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import net.mrqx.slashblade.maidpower.item.SlashBladeMaidBauble;
import java.util.Map;

/** C终结饰品 — A3后接C终结技(低血量)。 */
@RuleCondition
public final class TpmHasComboCCondition implements ICondition {
    @Override public String key() { return "tpm_has_combo_c"; }
    @Override public String displayName() { return "TPM连段C饰品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) { return String.valueOf(SlashBladeMaidBauble.ComboC.checkBauble(c.maid())); }
}
