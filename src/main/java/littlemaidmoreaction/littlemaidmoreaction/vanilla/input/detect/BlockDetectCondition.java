package littlemaidmoreaction.littlemaidmoreaction.vanilla.input.detect;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

@RuleCondition
public final class BlockDetectCondition extends AbstractDetectCondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("block_id", "方块ID", "minecraft:chest"),
        new TypedParam.IntParam("range", "搜索范围", 10),
        new TypedParam.IntParam("vertical", "垂直范围", 4)
    );

    private Block targetBlock = null;

    @Override public String key() { return "block_nearby"; }
    @Override public String displayName() { return "附近有方块"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        String blockId = rawParams.getOrDefault("block_id", "minecraft:chest");
        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        targetBlock = rl != null ? ForgeRegistries.BLOCKS.getValue(rl) : null;
        if (targetBlock == null) return "false";
        return super.evaluate(ctx, rawParams);
    }

    @Override
    protected boolean matchAt(Level level, BlockPos pos, EntityMaid maid) {
        return targetBlock != null && level.getBlockState(pos).is(targetBlock);
    }
}
