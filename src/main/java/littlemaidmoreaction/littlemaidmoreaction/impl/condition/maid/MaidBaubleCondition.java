package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 检查女仆饰品栏中的物品ID（逗号拼接）。
 *
 * <p>遍历30个饰品槽，将非空槽的物品registry name拼接为逗号分隔字符串。
 * STR类型，支持 :contains: / :in: / :=: 操作符。</p>
 *
 * <h3>用法</h3>
 * <pre>条件: maid_bauble :contains: touhou_little_maid:ultramarine_orb_elixir  → 有该饰品</pre>
 */
@RuleCondition
public final class MaidBaubleCondition implements ICondition {
    @Override public String key() { return "maid_bauble"; }
    @Override public String displayName() { return "饰品物品"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        var bauble = ctx.maid().getMaidBauble();
        StringJoiner sj = new StringJoiner(",");
        for (int i = 0; i < bauble.getSlots(); i++) {
            ItemStack stack = bauble.getStackInSlot(i);
            if (!stack.isEmpty()) {
                var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (key != null) sj.add(key.toString());
            }
        }
        return sj.length() > 0 ? sj.toString() : "";
    }
}
