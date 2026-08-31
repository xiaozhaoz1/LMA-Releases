package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 热源查询 (v79.6x 自 TempAdaptPipeline.findHeatSource 迁入 — B7 原语抽离)。
 * 防御点 (逐行保留): 垂直窗 ±4 / 距离排序取最近 / 空 → null;
 * LIT-only 判定 (2026-08-11c 全景 #13: 灭的篝火不热)。
 *
 * <p>v79.61x 用户裁定 (A+B 双保险):
 * <ul>
 *   <li>A — 热源清单剔除 FIRE/SOUL_FIRE (瞬态危险方块: 寻路不避、火无碰撞箱,
 *       女仆会走进火里被点燃 — 历史缺陷 v63 起)</li>
 *   <li>B — 新增 {@link #safeStand} 安全落点: 导航不再直接指向热源本体,
 *       改指热源旁可行走安全格 (岩浆块可站立烫脚 / 岩浆贴边 一并解决)</li>
 * </ul>
 */
public final class HeatSourceQuery {

    private HeatSourceQuery() {}

    /**
     * 最近热源 (点燃的营火/灵魂营火/岩浆/岩浆块); 无 → null。
     * 火方块 (FIRE/SOUL_FIRE) 已剔除 (v79.61x 用户裁定 A — 瞬态且危险)。
     * v79.61x L3 缓存接入: 走 BlockPatternCache 粗匹配 + 本类细筛 (TTL 窗内状态变化防脏读)。
     */
    public static BlockPos nearestHeatSource(ServerLevel world, BlockPos center, int radius) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos p : com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache
                .scanBlocks(world, center, radius,
                        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.BlockPatternCache.PatternType.HEAT)) {
            if (isHeatSource(world.getBlockState(p))) {
                candidates.add(p);
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** 热源判定单一来源 (v79.61x) — 缓存/查询共用; 火方块已剔除 */
    public static boolean isHeatSource(BlockState s) {
        return (s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT))
                || (s.is(Blocks.SOUL_CAMPFIRE) && s.getValue(CampfireBlock.LIT))
                || s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK);
    }

    /**
     * 热源旁最近安全站立落点 (v79.61x 用户裁定 B) — 水平 ±2 / 垂直 -1..+1 扫描:
     * 非危险方块 + 非流体 + 无碰撞 + 下方有支撑; 热源本体格跳过。
     * 找不到 → null (调用方兜底导航到热源本身)。
     */
    public static BlockPos safeStand(ServerLevel world, BlockPos heat) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int y = -1; y <= 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (x == 0 && z == 0 && y == 0) continue;  // 热源本体格
                    mp.set(heat.getX() + x, heat.getY() + y, heat.getZ() + z);
                    BlockState state = world.getBlockState(mp);
                    if (isDangerous(state)) continue;
                    if (!world.getFluidState(mp).isEmpty()) continue;               // 流体 (水/岩浆) 不可站
                    if (!state.getCollisionShape(world, mp).isEmpty()) continue;    // 有碰撞不可站
                    if (world.getBlockState(mp.below()).getCollisionShape(world, mp.below()).isEmpty()) {
                        continue;                                                   // 下方无支撑 (悬空)
                    }
                    double d = mp.distSqr(heat);
                    if (d < bestDist) {
                        bestDist = d;
                        best = mp.immutable();
                    }
                }
            }
        }
        return best;
    }

    /** 危险方块判定 — 火/岩浆/岩浆块/点燃营火 (落点排除; 与热源清单同源, 防走入伤害格) */
    private static boolean isDangerous(BlockState s) {
        return s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.LAVA)
                || s.is(Blocks.MAGMA_BLOCK)
                || (s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT))
                || (s.is(Blocks.SOUL_CAMPFIRE) && s.getValue(CampfireBlock.LIT));
    }
}
