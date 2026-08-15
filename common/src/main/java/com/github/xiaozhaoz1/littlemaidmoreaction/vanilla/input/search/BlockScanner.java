package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * 高性能方块扫描 (v77.5 移植自 Numen BlockScanner) — chunk-section palette 短路 + 环形螺旋。
 *
 * <p>核心优化: 朴素扫描 (2r+1)³ 次 getBlockState (~15k for r=12); 本扫描先遍历 chunk section,
 * 用 {@link LevelChunkSection#maybeHas} 查 section 调色板 (2-10 条目) — 无目标块整节跳过
 * (稀疏目标 50-200× 提速)。只读缓存 {@code getChunkNow} — 绝不强制加载区块 (同步扫描不阻塞服务器)。
 *
 * <p>匹配 record 兼容 {@link BlockSearch.Match} 语义 (pos/state/distSqr)。
 * 纯 JVM 不可测 (需 Level) — 环形数学在 {@link RingSpiral} (可单测)。
 */
public final class BlockScanner {

    /** 收集上限 — 超丰富目标 (水/岩浆海) 截断防内存爆炸; 螺旋序近优先, 截断即"附近足够多" */
    public static final int MAX_COLLECT = 8_192;

    /** 最大环半径 (chunk 单位) */
    public static final int MAX_RING_RADIUS_CHUNKS = 64;

    /** 扫描命中 */
    public record Match(BlockPos pos, BlockState state, double distSqr) {}

    private BlockScanner() {}

    /** 同步扫描 — 环形 chunk 螺旋 (近优先) + palette 短路; 返回近优先排序后的命中列表 (截断 maxHits)。
     *  @param vRange 垂直范围 (±Y, 0/负 = 不限) — 档位扫描 (AGGRESSIVE 高 5 地下 5) */
    public static List<Match> scan(ServerLevel level, BlockPos center, int maxRing, int vRange,
                                   Predicate<BlockState> filter, int maxHits) {
        List<Match> results = new ArrayList<>();
        int centerCX = SectionPos.blockToSectionCoord(center.getX());
        int centerCZ = SectionPos.blockToSectionCoord(center.getZ());
        int minSectionY = level.getSectionIndexFromSectionY(level.getMinSection());
        int maxSectionY = level.getSectionIndexFromSectionY(level.getMaxSection() - 1);
        int centerSectionY = level.getSectionIndexFromSectionY(center.getY() >> 4);
        int maxRingClamped = Math.min(maxRing, MAX_RING_RADIUS_CHUNKS);
        double maxDistSqr = (double) (maxRingClamped * 16) * (maxRingClamped * 16);

        for (int ring = 0; ring <= maxRingClamped && results.size() < maxHits; ring++) {
            int perim = RingSpiral.perimeter(ring);
            for (int idx = 0; idx < perim && results.size() < maxHits; idx++) {
                int[] off = RingSpiral.offset(ring, idx);
                int cx = centerCX + off[0];
                int cz = centerCZ + off[1];
                ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;   // 未加载 — 跳过 (读区缓存, 不强制加载)
                scanChunk(level, chunk, center, maxDistSqr, minSectionY, maxSectionY,
                        centerSectionY, vRange, filter, maxHits, results);
            }
        }
        // 精确距离排序 + 截断 (螺旋是 Chebyshev 近优先, 排序后取最精确的 maxHits)
        results.sort(Comparator.comparingDouble(Match::distSqr));
        if (results.size() > maxHits) {
            return new ArrayList<>(results.subList(0, maxHits));
        }
        return results;
    }

    /** 单 chunk 扫描 — section 按 Y 距中心近优先, palette 短路 */
    private static void scanChunk(ServerLevel level, ChunkAccess chunk, BlockPos center,
                                  double maxDistSqr, int minSectionY, int maxSectionY,
                                  int centerSectionY, int vRange, Predicate<BlockState> filter,
                                  int maxHits, List<Match> results) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        LevelChunkSection[] sections = chunk.getSections();
        // section Y 近优先: 从中心 section 螺旋向外
        int spread = Math.max(Math.abs(centerSectionY - minSectionY),
                Math.abs(centerSectionY - maxSectionY));
        for (int d = 0; d <= spread && results.size() < maxHits; d++) {
            for (int y0 : new int[]{centerSectionY - d, centerSectionY + d}) {
                if (y0 < minSectionY || y0 > maxSectionY) continue;
                int idx = y0; // y0 已是 0-based 节索引 — 原再减 getMinSection()(-4) 导致偏移 64 格 (对照 Numen 原版 sections[y0])
                if (idx < 0 || idx >= sections.length) continue;
                LevelChunkSection section = sections[idx];
                if (section == null || section.hasOnlyAir()) continue;
                // ★ palette 短路: section 调色板无目标 → 整节跳过
                if (!section.maybeHas(filter)) continue;
                int yReal = (y0 + level.getMinSection()) << 4;
                scanSection(section, chunkX, yReal, chunkZ, center, maxDistSqr,
                        vRange, filter, results);
            }
        }
    }

    /** 16³ section 内扫描 — 逐格 filter + 球形裁剪 (超出 maxDistSqr 跳过) + 垂直范围裁剪 (±vRange) */
    private static void scanSection(LevelChunkSection section, int chunkX, int yReal, int chunkZ,
                                    BlockPos center, double maxDistSqr, int vRange,
                                    Predicate<BlockState> filter, List<Match> results) {
        var states = section.getStates();
        int minY = yReal;   // 1.20.1/1.21.1 section Y 均从 minBuildHeight 起, yReal 已含 minSection 偏移
        for (int yy = 0; yy < 16; yy++) {
            int wy = minY + yy;
            int dy = wy - center.getY();
            if (vRange > 0 && Math.abs(dy) > vRange) continue;
            for (int z = 0; z < 16; z++) {
                int wz = chunkZ * 16 + z;
                int dz = wz - center.getZ();
                for (int x = 0; x < 16; x++) {
                    int wx = chunkX * 16 + x;
                    int dx = wx - center.getX();
                    double distSqr = dx * dx + dy * dy + dz * dz;
                    if (distSqr > maxDistSqr) continue;
                    BlockState state = states.get(x, yy, z);
                    if (!filter.test(state)) continue;
                    results.add(new Match(new BlockPos(wx, wy, wz), state, distSqr));
                    if (results.size() >= MAX_COLLECT) return;
                }
            }
        }
    }
}
