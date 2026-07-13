package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.*;
import java.util.Map;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
@RuleCondition
public final class MaidHasCurseCondition implements ICondition {
    @Override public String key() { return "maid_has_curse"; }
    @Override public String displayName() { return "女仆诅咒"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    private static boolean curse(net.minecraft.world.item.ItemStack s){return EnchantmentHelper.hasBindingCurse(s)||EnchantmentHelper.hasVanishingCurse(s);}
    @Override public String evaluate(RuleContext ctx, Map<String, String> r) { for(var s:ctx.maid().getArmorSlots()) if(curse(s)) return "true"; return String.valueOf(curse(ctx.maid().getMainHandItem())); }
}
