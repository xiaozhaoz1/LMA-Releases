package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import net.mrqx.slashblade.maidpower.event.MaidGuardHandler;
import java.util.Map;

/** 女仆当前是否正在格挡中。 */
@RuleCondition
public final class TpmIsGuardingCondition implements ICondition {
    @Override public String key() { return "tpm_is_guarding"; }
    @Override public String displayName() { return "TPM正在格挡"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) { return String.valueOf(MaidGuardHandler.isGuarding(c.maid())); }
}
