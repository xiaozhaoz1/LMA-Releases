package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;

/** 格挡冷却剩余 tick 数。0=可用，>0=冷却中。 */
@RuleCondition
public final class TpmGuardCooldownCondition implements ICondition {
    @Override public String key() { return "tpm_guard_cooldown"; }
    @Override public String displayName() { return "TPM格挡冷却"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        return String.valueOf(c.maid().getPersistentData().getInt("truePowerOfMaid.guardCooldown"));
    }
}
