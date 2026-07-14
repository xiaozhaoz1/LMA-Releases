package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search;

import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.VanillaConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.function.BiPredicate;

public final class BlockSearch {
    private BlockSearch() {}

    public record Match(BlockPos pos, BlockState state, double distSqr) {}

    /** 便捷重载: 自动 double→int + 默认垂直范围 */
    public static List<Match> findBlocksInRange(
            Level level, BlockPos center, double horizRange,
            BiPredicate<BlockPos, BlockState> matcher) {
        return findBlocks(level, center, (int) horizRange, VanillaConstants.SEARCH_VERTICAL, matcher);
    }

    public static List<Match> findBlocks(
            Level level, BlockPos center, int horiz, int vert,
            BiPredicate<BlockPos, BlockState> matcher) {
        List<Match> results = new ArrayList<>();
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();

        for (int y = -vert; y <= vert; y++) {
            for (int x = -horiz; x <= horiz; x++) {
                for (int z = -horiz; z <= horiz; z++) {
                    mPos.set(cx + x, cy + y, cz + z);
                    BlockState state = level.getBlockState(mPos);
                    if (state.isAir()) continue;
                    if (!matcher.test(mPos, state)) continue;
                    results.add(new Match(mPos.immutable(), state, mPos.distSqr(center)));
                }
            }
        }

        results.sort(Comparator.comparingDouble(Match::distSqr));
        return results;
    }

    public static boolean exists(Level level, BlockPos center, int horiz, int vert,
                                 BiPredicate<BlockPos, BlockState> matcher) {
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();

        for (int y = -vert; y <= vert; y++) {
            for (int x = -horiz; x <= horiz; x++) {
                for (int z = -horiz; z <= horiz; z++) {
                    mPos.set(cx + x, cy + y, cz + z);
                    BlockState state = level.getBlockState(mPos);
                    if (state.isAir()) continue;
                    if (matcher.test(mPos, state)) return true;
                }
            }
        }
        return false;
    }
}
