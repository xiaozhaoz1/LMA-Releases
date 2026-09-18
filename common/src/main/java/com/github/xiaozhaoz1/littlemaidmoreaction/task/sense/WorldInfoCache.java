package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * 世界信息快照缓存 (v79.61x 广播粒度分析 — 用户裁定数据分层) —
 * 消除广播中「同区块 N 女仆重复 readWorld」的温度/亮度/天气重复读。
 *
 * <p><b>分层</b>:
 * <ul>
 *   <li><b>per-dimension</b> (全维度相同: 天气/昼夜/时间): 每轮 1 次查询,
 *       所有区块共享 — 消灭 N×重复读时间/天气</li>
 *   <li><b>per-chunk</b> (位置相关: 亮度/温度/biome/降水/站立结构): 每区块每轮 1 次,
 *       同区块女仆共享 — 温度/亮度/biome 从 N× 降为 1×</li>
 * </ul>
 *
 * <p><b>TTL</b>: {@link #CACHE_TTL_TICKS} (200t = 广播间隔) — 与 EnvSenseBroadcaster 对齐;
 * 时间戳自愈 + 时钟回绕守卫 (isFresh 逐字镜像)。清理: 懒清理 (超规模清陈旧) + 维度生命周期
 * (clearDimension, 世界关闭)。
 */
public final class WorldInfoCache {

    /** 缓存 TTL (tick) — 对齐环境广播间隔 200t */
    private static final long CACHE_TTL_TICKS = 200L;
    /** 懒清理: 超此规模清陈旧条目 */
    private static final int MAX_ENTRIES = 2048;
    /** 懒清理: 超此年龄视为陈旧 (10 分钟) */
    private static final long HORIZON_TICKS = 12_000L;
    /** 区块缓存: 维度 → (chunkLong → entry) */
    private static final Map<String, Map<Long, ChunkEntry>> CHUNK = new HashMap<>();
    /** 维度缓存: 维度 → entry (天气/时间, 每维度 1 条) */
    private static final Map<String, DimEntry> DIM = new HashMap<>();

    // v79.63 (命名评审 D-6): 原此处有一个 no-op 清理占位 (空 lambda 登记 MaidUnloadRegistry) — 已删。
    // 规则澄清: **有 per-maid 状态才登记**; 本缓存是区块/维度键, 卸载清理无对象, 维度收口走
    // clearDimension (ServerStopping)。(占位式登记会稀释"登记 = 有清理义务"的信号强度。)

    private WorldInfoCache() {}

    private record DimEntry(long tick, boolean day, boolean raining, boolean thundering,
                            int moonPhase, String dimension, long dayTime, String timeSegment) {}
    // v79.62.5: tempCategory/temperature 字段删 — AI 上下文温度档改调 TLM maid.getAtBiomeTemp()
    private record ChunkEntry(long tick, int lightAtMaid,
                              String precipitation, String biomeId, String structuresAt) {}

    /** 时间戳新鲜度判定 (逐字镜像 StructureScanCache.isFresh — 时钟回绕守卫) */
    static boolean isFresh(long scanTick, long nowTick, long ttl) {
        return nowTick >= scanTick && nowTick - scanTick < ttl;
    }

    /** 组装世界快照 — per-dim 天气/时间 (每轮 1 次) + per-chunk 位置数据 (同区块共享) */
    public static EnvSnapshot.WorldInfo get(ServerLevel level, BlockPos center) {
        String dim = level.dimension().location().toString();
        long now = level.getGameTime();
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        long chunkKey = ChunkPos.asLong(cx, cz);
        Map<Long, ChunkEntry> dimChunk = CHUNK.computeIfAbsent(dim, k -> new HashMap<>());
        lazyClean(dimChunk, now);

        // ── per-chunk 位置数据 (温度/亮度/biome/降水/站立结构) ──
        ChunkEntry c = dimChunk.get(chunkKey);
        if (c == null || !isFresh(c.tick(), now, CACHE_TTL_TICKS)) {
            var biome = level.getBiome(center).value();
            c = new ChunkEntry(now,
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader.getLightLevel(level, center),
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader.getPrecipitation(level, center),
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader.getBiome(level, center),
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.cache.StructureScanCache.structuresAt(level, center));
            dimChunk.put(chunkKey, c);
        }

        // ── per-dimension 天气/时间 (每轮 1 次, 全维度共享; 组装按始终刷 — 便宜) ──
        DimEntry d = DIM.get(dim);
        if (d == null || !isFresh(d.tick(), now, CACHE_TTL_TICKS)) {
            long dayTime = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader.getTime(level);
            d = new DimEntry(now,
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader.isDay(level),
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader.isRaining(level),
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader.isThundering(level),
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader.getMoonPhase(level),
                    dim, dayTime, EnvRules.timeSegment(dayTime));
            DIM.put(dim, d);
        }

        return new EnvSnapshot.WorldInfo(d.day(), d.raining(), d.thundering(), d.moonPhase(),
                c.lightAtMaid(), d.dimension(),
                c.precipitation(), d.dayTime(), d.timeSegment(), c.biomeId(), c.structuresAt());
    }

    /** 懒清理 — 超规模清陈旧区块条目; 维度缓存条目数 = 维度数恒小 */
    private static void lazyClean(Map<Long, ChunkEntry> dimChunk, long nowTick) {
        if (dimChunk.size() < MAX_ENTRIES) return;
        dimChunk.entrySet().removeIf(e -> !isFresh(e.getValue().tick(), nowTick, HORIZON_TICKS));
    }

    /** 维度清理 (世界关闭/维度卸载) */
    public static void clearDimension(String dim) {
        CHUNK.remove(dim);
        DIM.remove(dim);
    }
}
