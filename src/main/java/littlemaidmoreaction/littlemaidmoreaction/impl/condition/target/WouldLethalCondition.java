package littlemaidmoreaction.littlemaidmoreaction.impl.condition.target;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import net.minecraft.world.entity.ai.attributes.Attributes;
@RuleCondition
public final class WouldLethalCondition implements ICondition {
    @Override public String key() { return "would_lethal"; }
    @Override public String displayName() { return "可致死"; }
    @Override public ConditionCategory category() { return ConditionCategory.TARGET; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) { return ctx.target() != null ? String.valueOf(ctx.target().getHealth() <= ctx.maid().getAttributeValue(Attributes.ATTACK_DAMAGE)) : "false"; }
}

