package littlemaidmoreaction.littlemaidmoreaction.compat.tpm.impl.condition;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import net.mrqx.slashblade.maidpower.item.SlashBladeMaidBauble;
import java.util.Map;

/**
 * 女仆是否装备"次元斩"(Judgement Cut) 饰品。
 *
 * <h3>效果</h3>
 * <p>允许女仆使用拔刀剑的次元斩 SA。未装备时 JC 会被取消。
 * 搭配真之力时连段上限从 3 提升到 5。</p>
 *
 * <h3>用法</h3>
 * <pre>条件: tpm_has_judgement_cut</pre>
 */
@RuleCondition
public final class TpmHasJudgementCutCondition implements ICondition {
    @Override public String key() { return "tpm_has_judgement_cut"; }
    @Override public String displayName() { return "JC饰品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext c, Map<String,String> r) { return String.valueOf(SlashBladeMaidBauble.JudgementCut.checkBauble(c.maid())); }
}
