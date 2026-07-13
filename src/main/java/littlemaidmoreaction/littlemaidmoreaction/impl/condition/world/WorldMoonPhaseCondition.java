package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.world.WorldStateReader;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
@RuleCondition
public final class WorldMoonPhaseCondition implements ICondition {
    @Override public String key() { return "world_moon_phase"; }
    @Override public String displayName() { return "月相"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.valueOf(WorldStateReader.getMoonPhase(ctx.maid().level())); }
}
