package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
@RuleCondition
public final class MaidIsHoldingProjectileCondition implements ICondition {
    @Override public String key() { return "maid_is_holding_projectile"; }
    @Override public String displayName() { return "持远程武器"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { var i=ctx.maid().getMainHandItem().getItem(); return String.valueOf(i instanceof BowItem||i instanceof CrossbowItem||i instanceof TridentItem); }
}
