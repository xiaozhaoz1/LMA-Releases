package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * 连通方块搜索 — BFS 26 邻域泛洪 (v36)。
 *
 * <p>用于连锁砍树/连锁挖矿：从起始方块出发查找所有相连的同类方块。
 * 参考 maid_useful_task 的 Queue+HashSet+距离截断模式，
 * 额外增加数量上限截断（防止巨型矿脉/丛林树全图遍历）。
 */
public final class ConnectedBlockSearch {

    private ConnectedBlockSearch() {}

    /**
     * BFS 查找与 start 连通（26 邻域）且满足 match 的方块。
     *
     * @param level      世界
     * @param start      起始方块（本身必须匹配，否则返回空列表）
     * @param match      匹配条件
     * @param maxBlocks  结果数量上限
     * @param anchor     距离锚点（通常为女仆站立位置或工作中心）
     * @param maxDistSqr 与锚点的最大距离平方，超出不入队
     * @return BFS 层序列表（近→远），含 start
     */
    public static List<BlockPos> findConnected(Level level, BlockPos start,
                                               BiPredicate<BlockPos, BlockState> match,
                                               int maxBlocks, BlockPos anchor, double maxDistSqr) {
        List<BlockPos> result = new ArrayList<>();
        if (maxBlocks <= 0 || !match.test(start, level.getBlockState(start))) {
            return result;
        }

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            BlockPos pos = queue.poll();
            result.add(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        if (next.distSqr(anchor) > maxDistSqr) continue;
                        visited.add(next);
                        if (!match.test(next, level.getBlockState(next))) continue;
                        queue.add(next);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 判定天然树：沿原木 DFS，找到任意非 PERSISTENT 树叶即为天然树。
     * 玩家建筑用原木通常无树叶或树叶为 PERSISTENT（手放置）— 防止女仆拆房。
     *
     * @param maxLogs DFS 遍历原木数量上限（防巨型结构全图遍历）
     */
    public static boolean isNaturalTree(Level level, BlockPos startLog, int maxLogs) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> stack = new ArrayDeque<>();
        stack.push(startLog);
        visited.add(startLog);

        while (!stack.isEmpty() && visited.size() <= maxLogs) {
            BlockPos pos = stack.pop();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (visited.contains(next)) continue;
                        BlockState state = level.getBlockState(next);
                        if (state.is(BlockTags.LEAVES)
                                && state.hasProperty(LeavesBlock.PERSISTENT)
                                && !state.getValue(LeavesBlock.PERSISTENT)) {
                            return true;
                        }
                        if (state.is(BlockTags.LOGS)) {
                            visited.add(next);
                            stack.push(next);
                        }
                    }
                }
            }
        }
        return false;
    }
}
