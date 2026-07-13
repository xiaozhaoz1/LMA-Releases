package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import net.minecraft.tags.DamageTypeTags;
@RuleCondition
public final class DamageTypeCondition implements ICondition {
    @Override public String key() { return "damage_type"; }
    @Override public String displayName() { return "伤害类型"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) { return ctx.source() != null && ctx.source().is(DamageTypeTags.IS_PROJECTILE) ? "ranged" : "melee"; }
}

