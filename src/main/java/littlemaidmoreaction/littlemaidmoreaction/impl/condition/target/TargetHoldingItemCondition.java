package littlemaidmoreaction.littlemaidmoreaction.impl.condition.target;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.target.TargetStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;

import java.util.Map;

/**
 * 检查 target（如右键玩家）手持物品的注册名。
 *
 * <p>用于 {@code maid_interact} 事件：ctx.target() = 右键女仆的玩家，
 * 返回玩家主手物品 ID，如 {@code "minecraft:glass_bottle"}。
 */
@RuleCondition
public final class TargetHoldingItemCondition implements ICondition {
    @Override public String key() { return "target_holding_item"; }
    @Override public String displayName() { return "目标手持"; }
    @Override public ConditionCategory category() { return ConditionCategory.TARGET; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        return ctx.target() != null ? TargetStateReader.getHoldingItemId(ctx.target()) : "none";
    }
}
