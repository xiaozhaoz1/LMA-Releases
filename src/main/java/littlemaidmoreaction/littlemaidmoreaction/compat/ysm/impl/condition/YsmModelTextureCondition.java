package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.util.Map;

/**
 * 获取女仆当前 YSM 模型的纹理名称。
 *
 * <h3>格式</h3>
 * <p>纹理名不含 {@code .png} 后缀，如 {@code "skin"}、{@code "skin_white"}。
 * 每个 YSM 模型目录下可有多个纹理文件，此值对应当前选用的纹理名。</p>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: ysm_model_texture :=: skin                   → 检查是否为默认皮肤
 * 条件: ysm_model_texture :=: skin_white             → 检查是否为白色皮肤
 * </pre>
 *
 * <h3>典型规则</h3>
 * <pre>
 * 场景: 不同纹理触发不同特效颜色
 * 事件: maid_tick
 * 条件: ysm_model_texture :=: skin_white
 * 动作: spawn_particle(color=white)
 * </pre>
 *
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action.SetYsmModelAction
 */
@RuleCondition
public final class YsmModelTextureCondition implements ICondition {
    @Override public String key() { return "ysm_model_texture"; }
    @Override public String displayName() { return "YSM模型纹理"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return ctx.maid().getYsmModelTexture();
    }
}
