package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.util.Map;

/**
 * 获取女仆当前 YSM 模型的唯一标识符。
 *
 * <h3>格式</h3>
 * <p>格式为 {@code "包名/模型目录"}，如 {@code "wine_fox/01_taisho_maid"}。
 * 由 YSM 资源包的 {@code pack.json} 中 {@code model_id} 字段决定。</p>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: ysm_model_id :=: wine_fox/01_taisho_maid      → 检查是否为特定模型
 * 条件: ysm_model_id :!=: wine_fox/02_new_year        → 检查是否不是某个模型
 * 条件: ysm_model_id :contains: wine_fox              → 检查是否来自酒狐包
 * </pre>
 *
 * <h3>典型规则</h3>
 * <pre>
 * 场景: 特定模型触发专属攻击动画
 * 事件: maid_attack
 * 条件: ysm_model_id :=: wine_fox/04_kongfu           // 功夫酒狐
 * 动作: play_anim(anim=kongfu_attack)
 * </pre>
 *
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action.SetYsmModelAction
 */
@RuleCondition
public final class YsmModelIdCondition implements ICondition {
    @Override public String key() { return "ysm_model_id"; }
    @Override public String displayName() { return "YSM模型ID"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return ctx.maid().getYsmModelId();
    }
}
