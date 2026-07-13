package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import net.mrqx.slashblade.maidpower.item.SlashBladeMaidBauble;
import java.util.Map;

/**
 * 女仆是否装备"未觉醒灵魂"(Unawakened Soul) 饰品。
 *
 * <h3>效果</h3>
 * <p>提升击杀敌人时的 ProudSoul 获取量：无此饰品时倍率 0.8x，有时 1.0x。
 * 配合 ConcentrationRank 等级额外加成。</p>
 *
 * <h3>用法</h3>
 * <pre>条件: tpm_has_unawakened_soul</pre>
 */
@RuleCondition
public final class TpmHasUnawakenedSoulCondition implements ICondition {
    @Override public String key() { return "tpm_has_unawakened_soul"; }
    @Override public String displayName() { return "未觉醒灵魂饰品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) { return String.valueOf(SlashBladeMaidBauble.UnawakenedSoul.checkBauble(c.maid())); }
}
