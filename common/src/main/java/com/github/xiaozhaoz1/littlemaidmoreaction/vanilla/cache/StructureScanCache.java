package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache;

import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StructBox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
//? if 1.20.1 {
import net.minecraft.world.level.chunk.ChunkStatus;
//?} else {
import net.minecraft.world.level.chunk.status.ChunkStatus;
//?}
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search.RingSpiral;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结构扫描区块缓存 (2026-08-16 预算方案 P — 用户裁定) — 原版世界读工具 API:
 * 区块级共享缓存 + 逐轮摊薄螺旋 + 墙钟预算, 供 StructureSense 等任意消费方复用。
 *
 * <ul>
 *   <li>基本单位 = 区块: 单区块扫描 getAllStarts → registry id → {@link StructBox} (原子原语)</li>
 *   <li>共享缓存: 维度 + chunkPos 键, 跨玩家共享 — 同区块玩家零重扫
 *       <b>静态层 (v79.61x L1 增强, 用户裁定): 结构位置世界生成后不可变 → 条目存在即有效,
 *       无 TTL 过期 (lithium 哲学: 不可变数据零失效成本); 新结构 = 新区块首次查询增量填充;
 *       失效 = 维度生命周期 (clearDimension)</b></li>
 *   <li>摊薄: 螺旋由近及远逐区块扫描, 预算耗尽即停 — 近处永远先扫到 (enter/leave 判定优先),
 *       远处结构发现延迟到后续轮次 (预算压力下的优雅降级)</li>
 *   <li>预算: {@link #SCAN_BUDGET_NANOS} 每广播轮结构通道总墙钟上限 (先到先得, 跨玩家共享)</li>
 * </ul>
 *
 * <p>维度键: 不同维度的同 chunkPos 独立缓存 (overworld/nether 隔离)。
 * 缓存懒清理: 超 {@link #MAX_ENTRIES} 时清除超 {@link #HORIZON_TICKS} 的陈旧条目 (容量兜底,
 * 静态数据清掉后重扫成本 = 单区块 getAllStarts, 预算内可恢复)。
 */
public final class StructureScanCache {

    /** 每广播轮结构通道总墙钟预算 (2ms) */
    public static final long SCAN_BUDGET_NANOS = 2_000_000L;
    /** 懒清理: 超此规模清陈旧条目 */
    private static final int MAX_ENTRIES = 4096;
    /** 懒清理: 超此年龄视为陈旧 (10 分钟 — 静态层容量兜底) */
    private static final long HORIZON_TICKS = 12_000L;
    /** 全局实例 (与 ScanBudget.GLOBAL 同约定 — 服务器生命周期单例) */
    public static final StructureScanCache GLOBAL = new StructureScanCache();

    /** 维度 location → (chunkPosLong → 扫描结果) */
    private final Map<String, Map<Long, Entry>> cache = new HashMap<>();

    private record Entry(long scanTick, Map<String, StructBox> structures) {}

    /** 新鲜判定纯函数 (JVM 可测) — 时钟回退视为过期 */
    public static boolean isFresh(long scanTick, long nowTick, long ttl) {
        return nowTick >= scanTick && nowTick - scanTick < ttl;
    }

    /**
     * 单区块扫描原子原语 — 已加载 chunk (FULL) 的全部结构 start → registry id → 边界盒。
     * 未加载 chunk → 空表 (接近才加载, 附近感知语义)。
     */
    public static Map<String, StructBox> scanChunk(ServerLevel level, int chunkX, int chunkZ) {
        Map<String, StructBox> out = new LinkedHashMap<>();
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) return out;
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (var e : chunk.getAllStarts().entrySet()) {
            var start = e.getValue();
            if (start == null || !start.isValid()) continue;
            String id = registry.getResourceKey(e.getKey())
                    .map(k -> k.location().toString())
                    .orElse("unknown");
            var bb = start.getBoundingBox();
            out.putIfAbsent(id, new StructBox(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ()));
        }
        return out;
    }

    /**
     * 以 center 为中心 radiusChunks 半径的摊薄扫描 — 螺旋由近及远;
     * 静态层: 条目存在即有效 (结构位置不可变), 未缓存区块在预算内扫描填块; 预算耗尽跳过。
     *
     * @param deadlineNanos 本轮结构通道预算截止 (System.nanoTime() + {@link #SCAN_BUDGET_NANOS})
     * @return 本轮可见的结构 id → 边界盒并集 (预算压力下可能不含远处结构 — 优雅降级)
     */
    public Map<String, StructBox> scanAround(ServerLevel level, BlockPos center, int radiusChunks,
                                              long nowTick, long deadlineNanos) {
        Map<String, StructBox> out = new LinkedHashMap<>();
        String dim = level.dimension().location().toString();
        Map<Long, Entry> dimCache = cache.computeIfAbsent(dim, k -> new HashMap<>());
        lazyClean(dimCache, nowTick);
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        for (int ring = 0; ring <= radiusChunks; ring++) {
            int perimeter = RingSpiral.perimeter(ring);
            for (int i = 0; i < perimeter; i++) {
                int[] off = RingSpiral.offset(ring, i);
                if (Math.max(Math.abs(off[0]), Math.abs(off[1])) > radiusChunks) continue;
                long key = ChunkPos.asLong(cx + off[0], cz + off[1]);
                Entry entry = dimCache.get(key);
                if (entry != null) {
                    // 静态层: 存在即有效 — 结构位置世界生成后不可变 (v79.61x L1 增强)
                    out.putAll(entry.structures());
                    continue;
                }
                if (System.nanoTime() >= deadlineNanos) continue;   // 预算耗尽: 本轮跳过
                Map<String, StructBox> one = scanChunk(level, cx + off[0], cz + off[1]);
                dimCache.put(key, new Entry(nowTick, one));
                out.putAll(one);
            }
        }
        return out;
    }

    /** 懒清理 — 超规模清陈旧条目 (HORIZON_TICKS 外); 新近条目保留 (活跃玩家缓存) */
    private static void lazyClean(Map<Long, Entry> dimCache, long nowTick) {
        if (dimCache.size() < MAX_ENTRIES) return;
        dimCache.entrySet().removeIf(e -> !isFresh(e.getValue().scanTick(), nowTick, HORIZON_TICKS));
    }

    /**
     * 站立点所在结构 (零成本 — getAllStructuresAt 瞬时查询, 与 1200t 最近结构通道互补)。
     * 返回排序逗号连接的 structure registry id; 空串 = 不在任何结构。
     * v79.6x 自 EnvScanner.structuresAt 迁入 (B3 — 结构查询同域收口; SenseApi 暴露)。
     */
    public static String structuresAt(ServerLevel level, BlockPos center) {
        var structs = level.structureManager().getAllStructuresAt(center);
        if (structs.isEmpty()) return "";
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        return structs.keySet().stream()
                .map(st -> registry.getResourceKey(st)
                        .map(k -> k.location().toString())
                        .orElse("unknown"))
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    /** 测试钩子: 指定维度缓存条目数 */
    public int cacheSize(String dimensionLocation) {
        Map<Long, Entry> m = cache.get(dimensionLocation);
        return m == null ? 0 : m.size();
    }
}
