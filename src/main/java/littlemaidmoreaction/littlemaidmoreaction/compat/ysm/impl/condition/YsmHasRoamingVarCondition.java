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
 * 检查女仆的 YSM 漫游变量是否存在。
 *
 * <h3>YSM 漫游变量是什么</h3>
 * <p>YSM 模型可以定义"漫游变量"（roaming variables）——绑定到动画参数的自定义浮点数。
 * 例如武器挥动速度、表情混合度、装备可见性等。变量名在 YSM 模型 JSON 中定义。</p>
 *
 * <h3>用法</h3>
 * <pre>
 * 条件: ysm_has_roaming_var = "swing_speed"     → 检查 swing_speed 变量是否存在
 * 条件: ysm_has_roaming_var = "emotion_anger"   → 检查 emotion_anger 是否存在
 * </pre>
 *
 * <h3>YSM 侧对应</h3>
 * <p>对应 {@code EntityMaid#roamingVars} 字段（{@code Object2FloatOpenHashMap<String>}），
 * 由 {@code SyncYsmMaidDataMessage} 从服务端同步到客户端。</p>
 *
 * <h3>典型规则</h3>
 * <pre>
 * 场景: 检测女仆是否正在使用特定 YSM 动画功能
 * 条件: ysm_has_roaming_var :=: "weapon_charge"
 * 动作: play_sound("modid:charge_complete")
 * </pre>
 *
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.condition.YsmRoamingVarCondition
 * @see littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action.SetYsmRoamingVarAction
 */
@RuleCondition
public final class YsmHasRoamingVarCondition implements ICondition {

    @Override public String key() { return "ysm_has_roaming_var"; }
    @Override public String displayName() { return "YSM漫游变量存在"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return String.valueOf(ctx.maid().roamingVars.containsKey(
            rawParams.getOrDefault("var_name", "")));
    }
}
