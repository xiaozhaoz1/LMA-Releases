package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.VanillaConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/**
 * 检测附近容器中是否包含指定物品 (v11 P4)。
 *
 * <p>搜索范围内容器(箱子/木桶/漏斗等)，检测是否有指定物品及数量。
 * 条件值类型 BOOL。
 */
@RuleCondition
public final class ContainerHasItemCondition implements ICondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("item_id", "物品ID", "minecraft:diamond"),
        new TypedParam.IntParam("min_count", "最少数量", 1),
        new TypedParam.IntParam("range", "搜索范围", 16)
    );

    @Override public String key() { return "container_has_item"; }
    @Override public String displayName() { return "容器有物品"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return "false";

        String itemId = rawParams.getOrDefault("item_id", "minecraft:diamond");
        int minCount = parseInt(rawParams.get("min_count"), 1);
        int range = parseInt(rawParams.get("range"), 16);

        Item target = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
        if (target == null) return "false";

        BlockPos center = maid.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-range, -VanillaConstants.SEARCH_VERTICAL, -range), center.offset(range, VanillaConstants.SEARCH_VERTICAL, range))) {
            BlockEntity te = maid.level().getBlockEntity(pos);
            if (te == null) continue;

            var handler = te.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (handler == null) continue;

            int found = 0;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (handler.getStackInSlot(slot).is(target)) {
                    found += handler.getStackInSlot(slot).getCount();
                }
            }
            if (found >= minCount) return "true";
        }
        return "false";
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
