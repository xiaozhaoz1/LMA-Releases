package littlemaidmoreaction.littlemaidmoreaction.impl.condition.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * 检测女仆脚下方块 (v11)。
 *
 * <p>支持 block_id (如 "minecraft:stone") 或 block_tag (如 "#minecraft:dirt")。
 * 条件值类型 STR — 返回方块 ID 供条件匹配使用。
 */
@RuleCondition
public final class BlockBelowCondition implements ICondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("offset_y", "Y偏移", 0)  // 0=脚下, -1=脚下一格
    );

    @Override public String key() { return "block_below"; }
    @Override public String displayName() { return "脚下方块"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return "air";

        int offsetY = parseInt(rawParams.get("offset_y"), 0);
        BlockPos pos = maid.blockPosition().offset(0, offsetY - 1, 0);
        Level level = maid.level();
        var state = level.getBlockState(pos);
        if (state.isAir()) return "air";

        ResourceLocation rl = level.registryAccess()
            .registryOrThrow(Registries.BLOCK)
            .getKey(state.getBlock());
        return rl != null ? rl.toString() : "unknown";
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
