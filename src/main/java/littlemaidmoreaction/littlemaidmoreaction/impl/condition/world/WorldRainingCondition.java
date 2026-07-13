package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.world.WorldStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
@RuleCondition
public final class WorldRainingCondition implements ICondition {
    @Override public String key() { return "world_is_raining"; }
    @Override public String displayName() { return "世界下雨"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) { return String.valueOf(WorldStateReader.isRaining(ctx.maid().level())); }
}

