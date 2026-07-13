package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
import net.minecraft.world.item.Items;
@RuleCondition
public final class MaidHasShieldCondition implements ICondition {
    @Override public String key() { return "maid_has_shield"; }
    @Override public String displayName() { return "女仆持盾"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) { return String.valueOf(ctx.maid().getMainHandItem().is(Items.SHIELD) || ctx.maid().getOffhandItem().is(Items.SHIELD)); }
}

