package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/**
 * 读取 YSM 漫游变量的数值。
 *
 * <h3>参数</h3>
 * <ul>
 *   <li><b>var_name</b> (String) — 变量名，必填。对应 YSM 模型 JSON 中定义的漫游变量名。</li>
 * </ul>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: ysm_roaming_var :>:= 0.5         → swing_speed 是否 > 0.5
 * 条件: ysm_roaming_var :=: 1.0         → emotion_anger 是否 = 1.0 (满愤怒)
 * 条件: ysm_roaming_var :<=: 0.1        → blend_idle 是否 <= 0.1 (几乎不混合)
 * </pre>
 *
 * <h3>典型规则</h3>
 * <pre>
 * 场景: 当 YSM 模型的武器充能 > 0.8 时触发特效
 * 事件: maid_tick
 * 条件: ysm_has_roaming_var :=: "weapon_charge"   // 先确认变量存在
 *       ysm_roaming_var :>:= 0.8    (var_name=weapon_charge)
 * 动作: spawn_particle(effect_id=flame)
 * </pre>
 *
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition.YsmHasRoamingVarCondition
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action.SetYsmRoamingVarAction
 */
@RuleCondition
public final class YsmRoamingVarCondition implements ICondition {

    @Override public String key() { return "ysm_roaming_var"; }
    @Override public String displayName() { return "YSM漫游变量值"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }

    @Override
    public List<TypedParam<?>> params() {
        return List.of(new TypedParam.StringParam("var_name", "变量名", ""));
    }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        String name = rawParams.getOrDefault("var_name", "");
        if (name.isEmpty()) return "0";
        return String.valueOf(ctx.maid().roamingVars.getFloat(name));
    }
}
