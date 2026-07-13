package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.util.Map;

/**
 * 获取女仆当前 YSM 模型的显示名称。
 *
 * <h3>来源</h3>
 * <p>显示名称在设置模型时通过 {@code setYsmModel(modelId, texture, name)} 的
 * 第三个参数传入。LMA 的 {@code set_ysm_model} 动作在随机模式下自动设置为中文名
 * （如"大正女仆酒狐"），手动模式下可自定义。</p>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: ysm_model_name :=: 大正女仆酒狐             → 检查是否为特定名称
 * 条件: ysm_model_name :contains: 酒狐               → 检查是否来自酒狐系列
 * </pre>
 *
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action.SetYsmModelAction
 */
@RuleCondition
public final class YsmModelNameCondition implements ICondition {
    @Override public String key() { return "ysm_model_name"; }
    @Override public String displayName() { return "YSM模型名称"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return ctx.maid().getYsmModelName().getString();
    }
}
