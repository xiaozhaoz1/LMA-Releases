package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.MaidUnloadRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单区块方块模式缓存 (v79.61x 缓存体系规划 L3, 用户裁定落位 execute 层) —
 * 热源/水源/容器 的「区块级匹配方块列表」共享缓存。
 *
 * <p><b>动机 (源码实证)</b>: HeatSourceQuery 每 100t / WaterQuery 每 100t
 * 各自三重循环扫半径 — 同一批方块数据被多被动多女仆重复扫描。
 * (v79.62: SNOW 缓存已删 — snow_shovel 被动移除, 用户裁定 TLM 原版清雪覆盖)
 *
 * <p><b>键</b>: (维度, chunkX, chunkZ, patternType) — 位置无关, 跨女仆共享。
 * <b>值</b>: Entry(scanTick, 地表高度窗内匹配方块列表)。
 * <b>TTL</b>: per-type (热源 100t / 水源 100t / 容器 200t) — 时间戳自愈,
 * 时钟回绕守卫 (isFresh 既有模式)。
 *
 * <p><b>失效策略 (lithium VicinityCache 源码调研结论)</b>: 纯 TTL 自愈 — LMA 无 mixin 基建,
 * 且自然方块变化 (天气) 走 Level.setBlock 不触发任何事件 → 事件失效对缓存场景无效;
 * lithium 的 SectionedBlockChangeTracker (mixin 版本戳) 记为未来升级路径。
 *
 * <p><b>扫描窗</b>: 每列 WORLD_SURFACE 高度 ±{@link #VERT} — 热源/水源均在近地表,
 * 避免全高 (16×16×384) 扫描; 查询方仍做类型过滤与距离排序 (缓存存粗匹配, 不写死)。
 * <b>容器</b>: 原版 {@link Container} BE 粗判 (capability 平台差异的细判留查询方)。
 */
public final class BlockPatternCache {

    /** 缓存类型 — 与查询服务一一对应 */
    public enum PatternType {
        HEAT(100), WATER(100), CONTAINER(200),
        /** v79.62 作物区域: 成熟作物 (CropRegistry 判定) / 可种耕地 (FARMLAND) — 区域扫描缓存 */
        CROP(200), FARMLAND(200);
        public final long ttl;
        PatternType(long ttl) { this.ttl = ttl; }
    }

    /** 地表高度窗 (±格) — 对齐既有垂直窗语义 (SEARCH_VERTICAL/4) 的上限 */
    private static final int VERT = 8;

    /** 时间戳新鲜度判定 (逐字镜像 EntityScanCache.isFresh — 时钟回绕守卫) */
    static boolean isFresh(long scanTick, long nowTick, long ttl) {
        return nowTick >= scanTick && nowTick - scanTick < ttl;
    }

    /** 缓存条目 */
    private record Entry(long scanTick, List<BlockPos> matches) {}

    /** 维度 → (chunkKey → type → entry); chunkKey = ChunkPos.asLong */
    private static final Map<String, Map<Long, Map<PatternType, Entry>>> CACHE = new HashMap<>();

    static {
        // 无 per-maid 状态 (区块级) — 卸载清理无注册项; 维度清理走 clearDimension
        MaidUnloadRegistry.register(maid -> { /* 占位: 区块缓存无 maidId 键 */ });
    }

    private BlockPatternCache() {}

    /** 查询: 覆盖区块内匹配方块 (按类型) — 新鲜命中零扫描, 未命中填块后返回 */
    public static List<BlockPos> scanBlocks(ServerLevel level, BlockPos center, int radius, PatternType type) {
        String dim = level.dimension().location().toString();
        Map<Long, Map<PatternType, Entry>> dimCache = CACHE.computeIfAbsent(dim, k -> new HashMap<>());
        long now = level.getGameTime();
        List<BlockPos> out = new ArrayList<>();
        int baseCX = center.getX() >> 4, baseCZ = center.getZ() >> 4;
        int chunkRadius = radius >> 4;
        for (int cx = baseCX - chunkRadius; cx <= baseCX + chunkRadius; cx++) {
            for (int cz = baseCZ - chunkRadius; cz <= baseCZ + chunkRadius; cz++) {
                long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                Map<PatternType, Entry> byType = dimCache.computeIfAbsent(key, k -> new HashMap<>());
                Entry entry = byType.get(type);
                if (entry == null || !isFresh(entry.scanTick(), now, type.ttl)) {
                    entry = new Entry(now, scanChunk(level, cx, cz, type));
                    byType.put(type, entry);
                }
                out.addAll(entry.matches());
            }
        }
        return out;
    }

    /** 当前覆盖区块 (调试叠加 — 画缓存区块边界) */
    public static List<long[]> coveredChunks(ServerLevel level) {
        String dim = level.dimension().location().toString();
        Map<Long, Map<PatternType, Entry>> dimCache = CACHE.get(dim);
        if (dimCache == null) return List.of();
        List<long[]> keys = new ArrayList<>();
        for (long k : dimCache.keySet()) {
            keys.add(new long[]{net.minecraft.world.level.ChunkPos.getX(k), net.minecraft.world.level.ChunkPos.getZ(k)});
        }
        return keys;
    }

    /** 维度清理 (世界关闭/维度卸载 — ServerStopping 调用) */
    public static void clearDimension(String dim) {
        CACHE.remove(dim);
    }

    /** 按类型清理指定维度缓存 (v79.62.2 测试用: 放热源后清 HEAT, 不碰 FARMLAND/CROP 等并发测试缓存).
     *  <p>修 {@code lmaTempAdaptSafeTarget} 偶发失败: setBlock 不失效缓存 (#238), 新放热源被
     *  旧「无热源」HEAT 缓存遮蔽 → 查询 null → 断言失败. 测试放热源后必须先清 HEAT 缓存. */
    public static void clearType(String dim, PatternType type) {
        synchronized (CACHE) {
            var dimCache = CACHE.get(dim);
            if (dimCache == null) return;
            for (var byType : dimCache.values()) {
                byType.remove(type);
            }
        }
    }

    /**
     * 执行成功 (收获/播种等) 后更新缓存: 从该位置所在 chunk 的所有类型条目移除该方块.
     * <p>缓存是「粗匹配提示」(TTL 自愈, setBlock 不失效 — #238), 执行层在动作成功后必须
     * 把该位置从缓存剔掉, 否则过期粗匹配会让下一 tick 重复瞄准同一块 (种→收→种循环).
     */
    public static void invalidateBlock(ServerLevel level, BlockPos pos) {
        Map<Long, Map<PatternType, Entry>> dimCache = CACHE.get(level.dimension().location().toString());
        if (dimCache == null) return;
        Map<PatternType, Entry> byType = dimCache.get(net.minecraft.world.level.ChunkPos.asLong(
                pos.getX() >> 4, pos.getZ() >> 4));
        if (byType == null) return;
        for (Entry e : byType.values()) {
            e.matches().removeIf(p -> p.equals(pos));
        }
    }

    /** 区块扫描 — 每列高度窗内匹配方块 (类型判定与查询服务同源).
     *  <p>v79.62 修悬浮/地下农场: CROP/FARMLAND (玩家自定义区域, 可任意高度) 走全列扫描 —
     *  原 WORLD_SURFACE ±VERT 窗对悬浮平台/gametest 结构 miss (错题 #238 surfaceSample=63 实证,
     *  2026-08-19 lmaFarmPlant/lmaFarmJob 双挂); HEAT/WATER/CONTAINER 保持近地表窗 (合理限定语义). */
    private static List<BlockPos> scanChunk(ServerLevel level, int chunkX, int chunkZ, PatternType type) {
        // v79.62.1 修卡顿: 区块未加载 → 跳过 (getHeight/getBlockState 在未生成区块会同步生成 → 主线程卡)
        if (!level.hasChunk(chunkX, chunkZ)) return List.of();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        boolean fullColumn = type == PatternType.CROP || type == PatternType.FARMLAND;
        List<BlockPos> matches = new ArrayList<>();
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = chunkX * 16 + x, wz = chunkZ * 16 + z;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                // 高度图兜底: 异常区块 surface 可能 ≤ minY (高度图未就绪) — 落到矿底起窗
                if (surface <= minY) {
                    surface = minY + 1;
                }
                int yStart = fullColumn ? minY : Math.max(minY, surface - VERT);
                int yEnd = fullColumn ? maxY : Math.min(maxY, surface + VERT);
                for (int y = yStart; y <= yEnd; y++) {
                    mp.set(wx, y, wz);
                    if (matches(level, mp, type)) {
                        matches.add(mp.immutable());
                    }
                }
            }
        }
        return matches;
    }

    /** 类型判定 — 与查询服务判定同源 (热源单一来源 isHeatSource / 水源源方块 / 原版容器 BE / 成熟作物 / 耕地) */
    private static boolean matches(ServerLevel level, BlockPos pos, PatternType type) {
        BlockState state = level.getBlockState(pos);
        return switch (type) {
            case HEAT -> com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.HeatSourceQuery
                    .isHeatSource(state);
            case WATER -> state.is(Blocks.WATER) && state.getFluidState().isSource();
            case CONTAINER -> level.getBlockEntity(pos) instanceof Container;
            case CROP -> com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.CropRegistry
                    .isMatureCrop(level, pos, state);
            case FARMLAND -> state.is(net.minecraft.world.level.block.Blocks.FARMLAND);
        };
    }
}
