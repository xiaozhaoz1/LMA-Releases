package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.util.Map;

/**
 * 获取女仆当前 YSM 轮盘动画的名称。
 *
 * <h3>数据来源</h3>
 * <p>读取 {@code EntityMaid#rouletteAnim} 公共 String 字段。
 * 轮盘动画通过 {@code playRouletteAnim(name)} 设置名称并启动。
 * 未播放时返回 {@code "empty"}。</p>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: roulette_anim :=: dance                    → 检查是否在播放 dance 轮盘
 * 条件: roulette_anim :!=: empty                   → 检查是否有轮盘动画在播放
 * </pre>
 *
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition.IsRoulettePlayingCondition
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action.PlayYsmRouletteAction
 */
@RuleCondition
public final class RouletteAnimCondition implements ICondition {
    @Override public String key() { return "roulette_anim"; }
    @Override public String displayName() { return "YSM轮盘动画名"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return ctx.maid().rouletteAnim;
    }
}
