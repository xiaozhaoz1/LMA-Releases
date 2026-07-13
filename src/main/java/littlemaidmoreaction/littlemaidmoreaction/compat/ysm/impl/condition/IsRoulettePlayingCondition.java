package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.util.Map;

/**
 * 检查女仆当前是否正在播放 YSM 轮盘动画。
 *
 * <h3>轮盘动画机制</h3>
 * <p>YSM 轮盘动画是一种循环播放的预设动画（类似 idle 动画的增强版）。
 * 通过 {@code playRouletteAnim(name)} 启动、{@code stopRouletteAnim()} 停止。
 * 播放状态通过 {@code SyncYsmMaidDataMessage} 自动同步到所有客户端。</p>
 *
 * <h3>数据来源</h3>
 * <p>读取 {@code EntityMaid#rouletteAnimPlaying} 公共 boolean 字段，
 * 由 TLM 实体直接暴露，不经过方法调用。</p>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: is_roulette_playing                    → 检查是否正在播放轮盘动画
 * </pre>
 *
 * <h3>典型规则</h3>
 * <pre>
 * 场景: 轮盘动画中禁止切换任务
 * 事件: maid_task_enable
 * 条件: is_roulette_playing
 * 动作: cancel_event
 * </pre>
 *
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition.RouletteAnimCondition
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action.PlayYsmRouletteAction
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action.StopYsmRouletteAction
 */
@RuleCondition
public final class IsRoulettePlayingCondition implements ICondition {
    @Override public String key() { return "is_roulette_playing"; }
    @Override public String displayName() { return "YSM轮盘动画播放中"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return ctx.maid().rouletteAnimPlaying ? "true" : "false";
    }
}
