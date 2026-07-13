package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/**
 * 检测主人(玩家)背包是否包含指定物品 (v11 P4)。
 *
 * <p>用于组合技能: "主人有钻石剑时女仆释放特殊连击"。
 * 条件值类型 BOOL。
 */
@RuleCondition
public final class PlayerHasItemCondition implements ICondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("item_id", "物品ID", "minecraft:diamond_sword"),
        new TypedParam.IntParam("min_count", "最少数量", 1)
    );

    @Override public String key() { return "player_has_item"; }
    @Override public String displayName() { return "主人有物品"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        if (ctx.maid().level().isClientSide()) return "false";

        String itemId = rawParams.getOrDefault("item_id", "minecraft:diamond_sword");
        int minCount = parseInt(rawParams.get("min_count"), 1);

        Item target = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
        if (target == null) return "false";

        var owner = ctx.maid().getOwner();
        if (!(owner instanceof Player player)) return "false";

        int found = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(target)) {
                found += inv.getItem(i).getCount();
            }
        }
        return found >= minCount ? "true" : "false";
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
