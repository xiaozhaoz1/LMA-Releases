package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;

/**
 * 距上次拔刀剑 SA 的 tick 数。
 *
 * <h3>数据来源</h3>
 * <p>读取 PersistentData 中的 {@code truePowerOfMaid.lastDoSlashTime}，
 * 与当前游戏时间相减。TPM 的 DoSlashHandler 在每次攻击时更新此值。</p>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: tpm_last_slash :>:= 200                  → 距上次 SA >= 10 秒
 * 条件: tpm_last_slash :<:= 40                   → 距上次 SA <= 2 秒（连段中）
 * </pre>
 */
@RuleCondition
public final class TpmLastSlashCondition implements ICondition {
    @Override public String key() { return "tpm_last_slash"; }
    @Override public String displayName() { return "TPM距上次SA"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) {
        long last = c.maid().getPersistentData().getLong("truePowerOfMaid.lastDoSlashTime");
        return String.valueOf(c.maid().level().getGameTime() - last);
    }
}
