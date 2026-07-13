package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import net.mrqx.slashblade.maidpower.item.SlashBladeMaidBauble;
import java.util.Map;

/**
 * 女仆是否装备"真之力"(True Power) — TPM 核心饰品。
 *
 * <h3>效果</h3>
 * <p>真之力是 TPM 的战斗核心：免疫击晕(Stun)、SA 连段上限从 3 提升到 5、
 * 解锁完整 SA 连段。无此饰品时 SA 仅 1 次后冷却。</p>
 *
 * <h3>用法</h3>
 * <pre>条件: tpm_has_true_power</pre>
 *
 * <h3>典型规则</h3>
 * <pre>
 * 真之力反击: maid_attack + tpm_has_true_power + is_holding_katana
 *   → cancel_event → slashblade_sa(knockback=toss, damage_mult=1.5)
 * </pre>
 */
@RuleCondition
public final class TpmHasTruePowerCondition implements ICondition {
    @Override public String key() { return "tpm_has_true_power"; }
    @Override public String displayName() { return "真之力饰品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) { return String.valueOf(SlashBladeMaidBauble.TruePower.checkBauble(c.maid())); }
}
