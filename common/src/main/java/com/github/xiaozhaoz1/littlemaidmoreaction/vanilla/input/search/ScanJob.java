package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 可恢复预算扫描任务 (v77.5 移植自 Numen ScanBlocksJob) — 游标切片, 每 tick 按预算推进。
 *
 * <p>同步女仆场景: 由 ChainHarvestExecute 每 tick 驱动 {@link #tick()}; 扫描不阻塞主线程长时
 * (每 tick 预算内做若干 section, 预算耗尽挂起, 下 tick 续扫)。螺旋游标 (ring/perimIdx) +
 * chunk 内 section 游标, 可跨 tick 恢复。
 *
 * <p>生命周期: {@link #start} → {@link #tick()} (每 tick) → done 回调。DEADLINE 超时截断返回部分结果。
 *
 * <p>v79.3: implements {@link Tickable} (可入 ScanScheduler 集中调度); +ownerId (女仆归属,
 * 卸载 cancelFor 清理闭环); +{@link #cancel} (置 done 清回调); matches 防御拷贝。
 */
public final class ScanJob implements Tickable {

    /** 硬超时 (30s 截断 → 部分结果) */
    public static final int DEADLINE_TICKS = 600;
    /** 收集上限 */
    public static final int MAX_COLLECT = 8_192;

    private ServerLevel level;
    private BlockPos center;
    private Predicate<BlockState> filter;
    private int maxHits;
    private Runnable onDone;
    private int startTick;
    /** 归属 owner (女仆 entityId; -1 = 无归属) — 卸载 cancelFor 用 */
    private int ownerId = -1;

    // 游标
    private int ring = 0;
    private int perimIdx = 0;
    private int ringMax;
    private int sectionCursor = 0;   // 当前 chunk 内 section 续扫游标 (预算中断续扫, 审计 H1)
    private int currentCX, currentCZ;
    private final List<BlockScanner.Match> matches = new ArrayList<>();
    private boolean done;
    private boolean finished;

    private ScanJob() {}

    /** 启动扫描 — 返回任务 (调用方每 tick 驱动 tick(); 无归属 ownerId=-1) */
    public static ScanJob start(ServerLevel level, BlockPos center, int maxRing,
                                Predicate<BlockState> filter, int maxHits, int serverTick,
                                Runnable onDone) {
        return start(level, center, maxRing, filter, maxHits, serverTick, onDone, -1);
    }

    /** 启动扫描 — 带归属 ownerId (ScanScheduler 集中调度 + 卸载清理) */
    public static ScanJob start(ServerLevel level, BlockPos center, int maxRing,
                                Predicate<BlockState> filter, int maxHits, int serverTick,
                                Runnable onDone, int ownerId) {
        ScanJob job = new ScanJob();
        job.level = level;
        job.center = center;
        job.filter = filter;
        job.maxHits = maxHits;
        job.onDone = onDone;
        job.startTick = serverTick;
        job.ownerId = ownerId;
        job.ringMax = Math.min(maxRing, BlockScanner.MAX_RING_RADIUS_CHUNKS);
        return job;
    }

    /** 归属 owner (女仆 entityId) */
    public int ownerId() { return ownerId; }

    public boolean isDone() { return done; }

    /** 防御拷贝 (外部修改不影响任务内部收集) */
    public List<BlockScanner.Match> matches() { return List.copyOf(matches); }

    /** 取消 — 置 done + 清回调 + 释放引用 (onDone 不再触发) */
    public void cancel() {
        done = true;
        onDone = null;
        level = null;
        matches.clear();
    }

    /** 每 tick 推进 — 预算内扫描若干 section; 完成后触发 onDone (仅一次) */
    public void tick(int serverTick) {
        if (done || level == null) return;
        ScanBudget budget = ScanBudget.GLOBAL;
        budget.refresh(serverTick);
        if (serverTick - startTick > DEADLINE_TICKS) {
            finish();
            return;
        }
        // 第一段: 预计算中心 chunk 偏移
        int centerCX = SectionPos.blockToSectionCoord(center.getX());
        int centerCZ = SectionPos.blockToSectionCoord(center.getZ());
        int minSectionY = level.getSectionIndexFromSectionY(level.getMinSection());
        int maxSectionY = level.getSectionIndexFromSectionY(level.getMaxSection() - 1);
        int centerSectionY = level.getSectionIndexFromSectionY(center.getY() >> 4);
        double maxDistSqr = (double) (ringMax * 16) * (ringMax * 16);

        while (ring <= ringMax && matches.size() < maxHits) {
            int perim = RingSpiral.perimeter(ring);
            while (perimIdx < perim && matches.size() < maxHits) {
                int[] off = RingSpiral.offset(ring, perimIdx);
                int cx = centerCX + off[0];
                int cz = centerCZ + off[1];
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) { perimIdx++; continue; }   // 未加载跳过
                currentCX = cx;
                currentCZ = cz;
                // 该 chunk 内 section 扫描 (预算切片; sectionCursor 续扫被打断的 chunk)
                int spread = Math.max(Math.abs(centerSectionY - minSectionY),
                        Math.abs(centerSectionY - maxSectionY));
                boolean budgetDry = false;
                for (int d = sectionCursor; d <= spread && matches.size() < maxHits; d++) {
                    for (int y0 : new int[]{centerSectionY - d, centerSectionY + d}) {
                        if (y0 < minSectionY || y0 > maxSectionY) continue;
                        int idx = y0; // 同 BlockScanner — y0 已是索引, 原偏移 64 格
                        var sections = chunk.getSections();
                        if (idx < 0 || idx >= sections.length) continue;
                        var section = sections[idx];
                        if (section == null || section.hasOnlyAir()) continue;
                        if (!section.maybeHas(filter)) continue;
                        // ★ 预算 permit — 每个 section 扫描消耗 1
                        if (!budget.trySectionScan()) {
                            budgetDry = true;
                            sectionCursor = d;   // 记中断层, 下 tick 续扫 (perimIdx 不推进)
                            break;
                        }
                        scanSection(section, currentCX, (y0 + level.getMinSection()) << 4,
                                currentCZ, center, maxDistSqr, matches);
                        if (matches.size() >= MAX_COLLECT) break;
                    }
                    if (budgetDry) break;
                }
                if (budgetDry) return;   // 预算耗尽 — 下 tick 同 chunk 从 sectionCursor 续扫
                sectionCursor = 0;       // chunk 完成 → 重置续扫游标并推进周界
                perimIdx++;
                if (matches.size() >= MAX_COLLECT) break;
            }
            perimIdx = 0;
            ring++;
            if (ring > ringMax) break;
        }
        finish();
    }

    private void scanSection(net.minecraft.world.level.chunk.LevelChunkSection section,
                             int chunkX, int yReal, int chunkZ, BlockPos center,
                             double maxDistSqr, List<BlockScanner.Match> results) {
        var states = section.getStates();
        for (int yy = 0; yy < 16; yy++) {
            int wy = yReal + yy;
            int dy = wy - center.getY();
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
                    results.add(new BlockScanner.Match(new BlockPos(wx, wy, wz), state, distSqr));
                    if (results.size() >= MAX_COLLECT) return;
                }
            }
        }
    }

    private void finish() {
        done = true;
        matches.sort(java.util.Comparator.comparingDouble(BlockScanner.Match::distSqr));
        if (matches.size() > maxHits) {
            matches.subList(maxHits, matches.size()).clear();
        }
        if (onDone != null && !finished) {
            finished = true;
            onDone.run();
        }
        level = null;
    }
}
