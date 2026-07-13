package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;

import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;

/**
 * 检查女仆当前是否在执行攻击类任务。
 *
 * <p>通过 {@link IAttackTask} 接口检测，覆盖所有 TLM 内置攻击任务
 * (attack / ranged_attack / crossbow_attack / danmaku_attack / trident_attack)
 * 以及实现了该接口的 mod 攻击任务（拔刀剑/魔法枪械等）。
 * 配合 preset 避免将远程/魔法任务强制切换为近战 attack。</p>
 */
@RuleCondition
public final class IsCombatTaskCondition implements ICondition {
    @Override public String key() { return "is_combat_task"; }
    @Override public String displayName() { return "是否攻击任务"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return String.valueOf(ctx.maid().getTask() instanceof IAttackTask);
    }
}
