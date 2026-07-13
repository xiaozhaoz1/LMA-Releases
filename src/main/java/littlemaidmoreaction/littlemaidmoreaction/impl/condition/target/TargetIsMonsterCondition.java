package littlemaidmoreaction.littlemaidmoreaction.impl.condition.target;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
import net.minecraft.world.entity.monster.Monster;
@RuleCondition
public final class TargetIsMonsterCondition implements ICondition {
    @Override public String key() { return "target_is_monster"; }
    @Override public String displayName() { return "目标怪物"; }
    @Override public ConditionCategory category() { return ConditionCategory.TARGET; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { return String.valueOf(ctx.target() instanceof Monster); }
}
