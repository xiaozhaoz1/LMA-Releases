package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.util.Map;

/**
 * 检测 YSM 轮盘动画是否发生了变更（脏标记）。
 *
 * <h3>什么是 dirty 标记</h3>
 * <p>当调用 {@code set_ysm_roaming_var} 或通过其他方式修改漫游变量时，
 * {@code EntityMaid#rouletteAnimDirty} 会被设为 {@code true}，
 * 触发 {@code SyncYsmMaidDataMessage} 向客户端同步。</p>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: ysm_roulette_dirty              → 检查是否有未同步的变更
 * </pre>
 *
 * <h3>典型规则</h3>
 * <pre>
 * 场景: 漫游变量变更后立即同步特效
 * 事件: maid_tick
 * 条件: ysm_roulette_dirty
 * 动作: spawn_particle(...)              // 在同步前播放一个瞬间特效
 * </pre>
 *
 * <p><b>注意</b>: 此条件触发频率较高（任何漫游变量变更都会触发），
 * 建议搭配冷却时间使用。</p>
 */
@RuleCondition
public final class YsmRouletteDirtyCondition implements ICondition {

    @Override public String key() { return "ysm_roulette_dirty"; }
    @Override public String displayName() { return "YSM漫游变量变更中"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return String.valueOf(ctx.maid().rouletteAnimDirty);
    }
}
