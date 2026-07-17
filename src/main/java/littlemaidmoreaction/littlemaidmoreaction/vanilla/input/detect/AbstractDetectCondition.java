package littlemaidmoreaction.littlemaidmoreaction.vanilla.input.detect;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.search.BlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.search.EntitySearch;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * 周围环境检测条件抽象基类 (v10 系统二)。
 *
 * <h3>设计目的</h3>
 * 将 TLM MaidMoveToBlockTask 的螺旋搜索模式提取为可复用的条件基类。
 * 子类只需覆写匹配方法，即可创建"检测附近XX"的条件。
 *
 * <h3>快速创建新检测条件</h3>
 * <pre>{@code
 * @RuleCondition
 * public class DetectChestCondition extends AbstractDetectCondition {
 *     @Override public String key() { return "chest_nearby"; }
 *     @Override public String displayName() { return "附近有箱子"; }
 *     @Override protected boolean matchAt(Level level, BlockPos pos, EntityMaid maid) {
 *         return level.getBlockState(pos).is(Blocks.CHEST);
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractDetectCondition implements ICondition {

    /** 默认搜索范围参数 */
    protected static final List<TypedParam<?>> DETECT_PARAMS = List.of(
        new TypedParam.IntParam("range", "搜索范围", 10),
        new TypedParam.IntParam("vertical", "垂直范围", 4)
    );

    @Override
    public final ConditionValueType valueType() { return ConditionValueType.BOOL; }

    @Override
    public List<TypedParam<?>> params() { return DETECT_PARAMS; }

    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return "false";

        int range = parseInt(rawParams.get("range"), 10);
        int vertical = parseInt(rawParams.get("vertical"), 4);
        BlockPos center = maid.blockPosition();

        if (searchBlocks()) {
            if (BlockSearch.exists(maid.level(), center, range, vertical,
                    (pos, state) -> matchAt(maid.level(), pos, state, maid))) {
                return "true";
            }
        }

        if (searchEntities()) {
            if (EntitySearch.exists(maid.level(), center, range, vertical,
                    e -> matchEntity(e, maid))) {
                return "true";
            }
        }

        return "false";
    }

    // ── 子类覆写点 ──

    protected boolean searchBlocks() { return true; }
    protected boolean searchEntities() { return false; }

    protected boolean matchAt(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, EntityMaid maid) {
        return matchAt(level, pos, maid);
    }

    protected boolean matchAt(Level level, BlockPos pos, EntityMaid maid) {
        return false;
    }

    protected boolean matchEntity(net.minecraft.world.entity.Entity entity, EntityMaid maid) {
        return false;
    }

    // ── 工具 ──

    protected static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
