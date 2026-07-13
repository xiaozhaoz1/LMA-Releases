package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;

import java.util.Map;

/**
 * 检查女仆当前是否使用 YSM 模型渲染。
 *
 * <h3>技术细节</h3>
 * <p>调用 {@code EntityMaid#isYsmModel()} —— TLM 自身 API，不直接引用 YSM 类。
 * 返回值来自实体数据 {@code DATA_IS_YSM_MODEL}，跨 session 持久化。
 * YSM 模组未安装时永远返回 {@code false}。</p>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: is_ysm_model                          → 女仆是否正在使用 YSM 模型
 * 条件: is_ysm_model :=: true                 → 同上（显式写法）
 * 条件: is_ysm_model :=: false                → 女仆使用 TLM 原生模型
 * </pre>
 *
 * <h3>典型规则</h3>
 * <pre>
 * 场景: 仅对 YSM 模型的女仆触发变身
 * 事件: maid_interact
 * 条件: is_ysm_model, owner_holding_item :=: minecraft:nether_star
 * 动作: set_ysm_model(mode=ysm女仆模型)       // 随机切换 YSM 模型
 * </pre>
 *
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action.SetYsmModelAction
 */
@RuleCondition
public final class IsYsmModelCondition implements ICondition {
    @Override public String key() { return "is_ysm_model"; }
    @Override public String displayName() { return "YSM模型"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return ctx.maid().isYsmModel() ? "true" : "false";
    }
}
