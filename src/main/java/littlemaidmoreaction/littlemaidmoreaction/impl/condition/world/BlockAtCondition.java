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

import java.util.List;
import java.util.Map;

/**
 * 检测女仆指定偏移处方块 (v11 P1)。
 * 返回方块 ID 供条件匹配，如 block_at := minecraft:chest。
 */
@RuleCondition
public final class BlockAtCondition implements ICondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("offset_x", "X偏移", 0),
        new TypedParam.IntParam("offset_y", "Y偏移", -1),
        new TypedParam.IntParam("offset_z", "Z偏移", 0)
    );

    @Override public String key() { return "block_at"; }
    @Override public String displayName() { return "指定位置方块"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return "air";

        int ox = parseInt(rawParams.get("offset_x"), 0);
        int oy = parseInt(rawParams.get("offset_y"), -1);
        int oz = parseInt(rawParams.get("offset_z"), 0);

        BlockPos pos = maid.blockPosition().offset(ox, oy, oz);
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
