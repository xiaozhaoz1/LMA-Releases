package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 水源查询 (v79.6x 自 TempAdaptPipeline.findWaterSource 迁入 — B7 原语抽离)。
 * 防御点 (逐行保留): 垂直窗 ±4 / 只认流体源方块 / 距离排序取最近 / 空 → null。
 */
public final class WaterQuery {

    private WaterQuery() {}

    /**
     * 最近水源 (静止水方块); 无 → null。
     * v79.61x L3 缓存接入: BlockPatternCache 粗匹配 (TTL 100t 对齐温度节流) + 本类细筛
     * (TTL 窗内水被改 → 防脏读) + 距离排序保留。
     */
    public static BlockPos nearestWaterSource(ServerLevel world, BlockPos center, int radius) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos p : com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache
                .scanBlocks(world, center, radius,
                        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache.PatternType.WATER)) {
            var s = world.getBlockState(p);
            if (s.is(Blocks.WATER) && s.getFluidState().isSource()) {
                candidates.add(p);
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return candidates.isEmpty() ? null : candidates.get(0);
    }
}
