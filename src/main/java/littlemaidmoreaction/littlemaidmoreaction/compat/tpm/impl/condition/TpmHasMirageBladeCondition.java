package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import net.mrqx.slashblade.maidpower.item.SlashBladeMaidBauble;
import java.util.Map;

/** 空中瞬移饰品 — 解锁空中 trick + 幻影剑。 */
@RuleCondition
public final class TpmHasMirageBladeCondition implements ICondition {
    @Override public String key() { return "tpm_has_mirage_blade"; }
    @Override public String displayName() { return "TPM幻影剑饰品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) { return String.valueOf(SlashBladeMaidBauble.MirageBlade.checkBauble(c.maid())); }
}
