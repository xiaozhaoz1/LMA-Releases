package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.detect;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Map;

@RuleCondition
public final class TileEntityDetectCondition extends AbstractDetectCondition {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("te_class", "TileEntity类名", "TileEntityAltar"),
        new TypedParam.IntParam("range", "搜索范围", 10),
        new TypedParam.IntParam("vertical", "垂直范围", 4)
    );

    private String targetClass = null;

    @Override public String key() { return "tile_entity_nearby"; }
    @Override public String displayName() { return "附近有TileEntity"; }
    @Override public ConditionCategory category() { return ConditionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        targetClass = rawParams.getOrDefault("te_class", "TileEntityAltar");
        return super.evaluate(ctx, rawParams);
    }

    @Override
    protected boolean matchAt(Level level, BlockPos pos, EntityMaid maid) {
        if (targetClass == null) return false;
        BlockEntity te = level.getBlockEntity(pos);
        if (te == null) return false;
        String className = te.getClass().getName();
        String simpleName = te.getClass().getSimpleName();
        return className.equals(targetClass) || simpleName.equalsIgnoreCase(targetClass);
    }
}
