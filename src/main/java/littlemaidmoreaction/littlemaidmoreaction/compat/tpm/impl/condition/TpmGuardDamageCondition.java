package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;

/**
 * TPM 格挡系统当前吸收的伤害值。
 *
 * <h3>数据来源</h3>
 * <p>读取 PersistentData 中的 {@code truePowerOfMaid.guardDamage} 浮点值。
 * TPM 格挡系统在女仆受击时累加到此值，达到阈值后触发闪避。</p>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: tpm_guard_damage :>:= 10                 → 格挡伤害累积 >= 10
 * 条件: tpm_guard_damage :<:= 5                  → 格挡伤害 <= 5 (即将触发闪避)
 * </pre>
 */
@RuleCondition
public final class TpmGuardDamageCondition implements ICondition {
    @Override public String key() { return "tpm_guard_damage"; }
    @Override public String displayName() { return "TPM格挡伤害"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        return String.valueOf(c.maid().getPersistentData().getFloat("truePowerOfMaid.guardDamage"));
    }
}
