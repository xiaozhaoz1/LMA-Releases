package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Map;

/**
 * 检查女仆饰品栏中指定物品的数量。
 *
 * <p>通过 params 传入 item_id 参数，遍历30个饰品槽统计匹配数量。
 * NUM类型，支持数值比较。</p>
 *
 * <h3>用法</h3>
 * <pre>条件: maid_bauble_count :>:= 2   params: item_id=touhou_little_maid:ultramarine_orb_elixir  → 至少2个</pre>
 */
@RuleCondition
public final class MaidBaubleCountCondition implements ICondition {
    @Override public String key() { return "maid_bauble_count"; }
    @Override public String displayName() { return "饰品数量"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.NUM; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        String targetId = rawParams.getOrDefault("item_id", "");
        var bauble = ctx.maid().getMaidBauble();
        int count = 0;
        for (int i = 0; i < bauble.getSlots(); i++) {
            ItemStack stack = bauble.getStackInSlot(i);
            if (!stack.isEmpty()) {
                var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (key != null && key.toString().equals(targetId)) count++;
            }
        }
        return String.valueOf(count);
    }
}
