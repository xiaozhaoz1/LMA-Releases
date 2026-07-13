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

/** 检测女仆头顶方块 (v11)。默认头顶+1格。 */
@RuleCondition
public final class BlockAboveCondition implements ICondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("offset_y", "Y偏移", 2)  // 默认头顶+2格
    );

    @Override public String key() { return "block_above"; }
    @Override public String displayName() { return "头顶方块"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public ConditionValueType valueType() { return ConditionValueType.STR; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return "air";

        int offsetY = parseInt(rawParams.get("offset_y"), 2);
        BlockPos pos = maid.blockPosition().offset(0, offsetY, 0);
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
