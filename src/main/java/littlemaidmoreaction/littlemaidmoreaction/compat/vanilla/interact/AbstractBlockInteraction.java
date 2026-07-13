package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.BlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/**
 * 方块交互动作抽象基类 (v12 P2)。
 *
 * <p>自动处理方块搜索+排序，子类只需覆写 matchBlock + interact。
 * 使用 {@link BlockSearch} 搜索原语，与 condition 端检测共享同一套搜索逻辑。
 *
 * <h3>快速创建</h3>
 * <pre>{@code
 * @RuleAction
 * public class PlaceFurnaceItemAction extends AbstractBlockInteraction {
 *     @Override public String id() { return "place_furnace_item"; }
 *     @Override public String displayName() { return "放置熔炉物品"; }
 *     @Override public ActionCategory category() { return ActionCategory.WORLD; }
 *
 *     @Override
 *     protected void interact(BlockPos pos, BlockState state, EntityMaid maid, Map<String,String> params) {
 *         // 放置物品到熔炉
 *         BlockEntity be = maid.level().getBlockEntity(pos);
 *         // ...
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractBlockInteraction implements IAction {
    private static final List<TypedParam<?>> BASE_PARAMS = List.of(
        new TypedParam.StringParam("block_id", "目标方块ID", "minecraft:chest"),
        new TypedParam.IntParam("range", "搜索范围", 10),
        new TypedParam.IntParam("vertical", "垂直范围", 4),
        new TypedParam.IntParam("max", "最大交互数", 1)
    );

    @Override
    public List<TypedParam<?>> params() { return BASE_PARAMS; }

    @Override
    public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> rawParams) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return;

        String blockId = rawParams.getOrDefault("block_id", "minecraft:chest");
        int range = parseInt(rawParams.get("range"), 10);
        int vertical = parseInt(rawParams.get("vertical"), 4);
        int max = parseInt(rawParams.get("max"), 1);

        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        if (rl == null) return;
        Block targetBlock = ForgeRegistries.BLOCKS.getValue(rl);
        if (targetBlock == null) return;

        // ★ 使用 BlockSearch 原语 (v12 P1)
        List<BlockSearch.Match> matches = BlockSearch.findBlocks(
            maid.level(), maid.blockPosition(), range, vertical,
            (pos, state) -> state.is(targetBlock) && matchBlock(pos, state, maid)
        );

        int count = 0;
        for (BlockSearch.Match match : matches) {
            if (count >= max) break;
            interact(match.pos(), match.state(), maid, rawParams);
            count++;
        }
    }

    /**
     * 额外匹配条件 (block_id 之外)。
     * 如：检查 BlockEntity 类型、检查方块状态属性等。
     * 默认全部通过。
     */
    protected boolean matchBlock(BlockPos pos, BlockState state, EntityMaid maid) { return true; }

    /**
     * 对匹配的方块执行交互。
     * 每个 match 调用一次，按距离由近到远。
     */
    protected abstract void interact(BlockPos pos, BlockState state, EntityMaid maid,
                                     Map<String, String> params);

    protected static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
