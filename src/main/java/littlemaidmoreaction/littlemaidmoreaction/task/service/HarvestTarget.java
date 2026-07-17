package littlemaidmoreaction.littlemaidmoreaction.task.service;

import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.item.ToolStateReader;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.search.ConnectedBlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import java.util.function.BiPredicate;

/**
 * 采集目标抽象 (v36.6) — 矿物/树木检测原子化（用户要求）。
 *
 * <p>把"是不是目标 / 工具能不能采 / 现场是否有效 / 连通谓词 / 耗久 / 速度"
 * 六个判定收拢为一个抽象，执行器（ChainHarvestExecute）只做编排零内联判断。
 * 未来新增采集类型（石头/沙砾/庄稼…）= 新增一个实现类。
 *
 * <p>判定本体全部委托输入层 {@link ToolStateReader}/{@link ConnectedBlockSearch}
 * 与计算层 {@link ToolJudge} — 本类只是组合。
 */
public abstract class HarvestTarget {

    /** 砍树: 所有原木；无斧慢砍；天然树校验；仅持可用斧耗久 */
    public static final HarvestTarget WOOD = new WoodTarget();
    /** 挖矿: Forge ores 标签；镐等级硬门槛；恒耗久 */
    public static final HarvestTarget ORE = new OreTarget();

    private static final int NATURE_CHECK_MAX_LOGS = 100;
    private static final int TOOL_RESERVE_DURABILITY = 1;

    /** 方块是否为本类型采集目标 */
    public abstract boolean matches(BlockState state);

    /** 当前工具能否采集此方块（ORE=挖掘等级判断，WOOD=恒真/无斧慢砍） */
    public abstract boolean canHarvest(ItemStack tool, BlockState state);

    /** 现场有效性校验（WOOD=天然树防拆建筑，ORE=恒真） */
    public abstract boolean validAt(ServerLevel world, BlockPos pos);

    /** BFS 连通谓词（WOOD=所有原木整树，ORE=与起点同种矿单一矿脉） */
    public abstract BiPredicate<BlockPos, BlockState> veinPredicate(BlockState start);

    /** 本次采集是否消耗工具耐久 */
    public abstract boolean consumesDurability(ItemStack tool);

    /** 每块破坏时长 (tick) — 委托 ToolJudge 等级表 */
    public abstract int intervalTicks(ItemStack tool);

    /** 气泡/日志显示名 */
    public abstract String label();

    // ── 实现 ──

    private static final class WoodTarget extends HarvestTarget {
        @Override public boolean matches(BlockState state) {
            return state.is(BlockTags.LOGS);
        }
        @Override public boolean canHarvest(ItemStack tool, BlockState state) {
            return true; // 无斧慢砍语义 — 斧只影响速度与耗久
        }
        @Override public boolean validAt(ServerLevel world, BlockPos pos) {
            return !MoreActionConfig.CHAIN_WOOD_NATURE_CHECK.get()
                    || ConnectedBlockSearch.isNaturalTree(world, pos, NATURE_CHECK_MAX_LOGS);
        }
        @Override public BiPredicate<BlockPos, BlockState> veinPredicate(BlockState start) {
            return (p, s) -> s.is(BlockTags.LOGS);
        }
        @Override public boolean consumesDurability(ItemStack tool) {
            return ToolStateReader.isAxe(tool)
                    && ToolJudge.isToolUsable(tool, TOOL_RESERVE_DURABILITY);
        }
        @Override public int intervalTicks(ItemStack tool) {
            return ToolJudge.harvestIntervalTicks(tool, true);
        }
        @Override public String label() {
            return "伐木";
        }
    }

    private static final class OreTarget extends HarvestTarget {
        @Override public boolean matches(BlockState state) {
            return state.is(Tags.Blocks.ORES);
        }
        @Override public boolean canHarvest(ItemStack tool, BlockState state) {
            return ToolJudge.canPickaxeMine(tool, state);
        }
        @Override public boolean validAt(ServerLevel world, BlockPos pos) {
            return true;
        }
        @Override public BiPredicate<BlockPos, BlockState> veinPredicate(BlockState start) {
            Block startBlock = start.getBlock();
            return (p, s) -> s.getBlock() == startBlock;
        }
        @Override public boolean consumesDurability(ItemStack tool) {
            return true;
        }
        @Override public int intervalTicks(ItemStack tool) {
            return ToolJudge.harvestIntervalTicks(tool, false);
        }
        @Override public String label() {
            return "采矿";
        }
    }
}
