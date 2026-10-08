package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 附近实体区块缓存 (2026-08-16 用户裁定: 环境感知扫描缓存 — 与结构通道同款缓存模式)。
 * 三层结构: L1 per-查询漂移缓存 → L2 区块共享缓存 → L0 MC 原生分区索引。
 *
 * <ul>
 *   <li><b>L1 per-查询漂移缓存</b> (Retold 模式 ①): entityId → 上次结果 + 中心坐标 —
 *       中心漂移 ≤{@link #MAX_CENTER_DRIFT_SQ} 开方 (3.5 格) 且结果未过期 → 直接复用
 *       (静止/小幅走动女仆零重扫零合并; 移动超门限落到 L2); 卸载经 {@link #onEntityRemoved} 闭环</li>
 *   <li><b>L2 区块共享缓存</b>: 维度+chunkPos → 区块全高存活 LivingEntity 清单
 *       (无分类无自排除 — 分类/排除/垂直窗是查询方语义, 进缓存就无法共享);
 *       TTL {@link #CACHE_TTL_TICKS} = 200t = 环境扫描间隔: 同轮女仆+主人共享一次扫描,
 *       下轮重扫 — 实体动态, 不做结构式长 TTL; 新鲜度与改造前一致 (纯省重复扫描)</li>
 *   <li><b>grace 宽限</b> (Retold 模式 ②): 过期条目在 {@link #GRACE_TICKS} 40t 宽限内仍可读 —
 *       预算耗尽时不漏报, TTL 边界抖动不重扫; 更旧才跳过 (迟发现不误报)</li>
 *   <li>{@link #warm} 预热: 每广播轮先扫在线玩家区块入缓存 — 女仆在主人附近全命中零重扫</li>
 *   <li>区块枚举走 {@link RingSpiral} 螺旋 (对齐结构通道): 预算耗尽时优先保中心区块, 角落延后</li>
 *   <li><b>L0 MC 原生</b>: 16³ 分区空间哈希 + 按类分桶 (getEntitiesOfClass 只遍历相交非空分区
 *       内的 LivingEntity) — 本缓存是第三层, 不替代 MC 索引</li>
 * </ul>
 *
 * <p>防御点清单: 缓存内容 List.copyOf 防外部改 / 合并后 isAlive 再过滤 (缓存期内实体可能死亡) /
 * 垂直窗 SEARCH_VERTICAL 保持原 scanEntities 语义 (区块全高存储, 查询方按 ±4 窗过滤) /
 * 空分类不入 map / Map.copyOf / 越界区块枚举由螺旋 ring 与 Chebyshev 截断保证 /
 * queryCache 卸载清理 (onEntityRemoved) + 懒清理 (MAX_ENTRIES 阈值)。
 */
public final class EntityScanCache {

    /** 缓存新鲜期 (tick) — 200t 与环境扫描间隔对齐: 同轮共享, 轮间重扫 */
    public static final long CACHE_TTL_TICKS = 200L;
    /** 过期宽限 (Retold STALE_SHARED_SCAN_GRACE_TICKS 模式): 过期条目在宽限内仍可读 — 预算耗尽时不漏报, 边界抖动不重扫 */
    public static final long GRACE_TICKS = 40L;
    /** per-查询漂移门限 (Retold MAX_CENTER_DRIFT_SQUARED 模式): 查询中心漂移 ≤3.5 格且结果未过期 → 直接复用上次结果 (跨区块边缘走动的女仆不用重扫) */
    public static final double MAX_CENTER_DRIFT_SQ = 3.5D * 3.5D;
    /** 预热段独立墙钟预算 (不挤占广播 pass 8ms — 防预热吃光预算饿死边沿检测) */
    public static final long SCAN_BUDGET_NANOS = 2_000_000L;
    /** 懒清理: 超此规模清陈旧条目 */
    private static final int MAX_ENTRIES = 4096;
    /** 懒清理: 超此年龄视为陈旧 (10 分钟) */
    private static final long HORIZON_TICKS = 12_000L;
    /** 全局实例 (与 StructureScanCache.GLOBAL 同约定) */
    public static final EntityScanCache GLOBAL = new EntityScanCache();

    /** 维度 location → (chunkPosLong → 扫描结果) */
    private final Map<String, Map<Long, Entry>> cache = new HashMap<>();
    /** per-查询结果缓存 (entityId → 上次查询) — 漂移门限复用层 */
    private final Map<Integer, QueryEntry> queryCache = new HashMap<>();

    private record Entry(long scanTick, List<LivingEntity> living) {}

    private record QueryEntry(int cx, int cy, int cz, long scanTick, Map<String, List<LivingEntity>> result) {}

    private EntityScanCache() {}

    /** 当前覆盖区块 (v79.61x 调试叠加 — 画实体缓存区块边界; 与 BlockPatternCache 同款签名) */
    public List<long[]> coveredChunks(ServerLevel level) {
        String dim = level.dimension().location().toString();
        Map<Long, Entry> dimCache = cache.get(dim);
        if (dimCache == null) return List.of();
        List<long[]> keys = new java.util.ArrayList<>();
        for (long k : dimCache.keySet()) {
            keys.add(new long[]{net.minecraft.world.level.ChunkPos.getX(k), net.minecraft.world.level.ChunkPos.getZ(k)});
        }
        return keys;
    }

    /** 查询方卸载清理 (广播器 onMaidUnload 调) — per-查询缓存闭环 */
    public void onEntityRemoved(int entityId) {        queryCache.remove(entityId);
    }

    /** 新鲜判定纯函数 (JVM 可测) — 时钟回退视为过期 */
    public static boolean isFresh(long scanTick, long nowTick, long ttl) {
        return nowTick >= scanTick && nowTick - scanTick < ttl;
    }

    /** 查询中心漂移平方 (纯函数) — 供 per-查询漂移门限 */
    static double driftSq(BlockPos center, int cx, int cy, int cz) {
        double dx = center.getX() - cx, dy = center.getY() - cy, dz = center.getZ() - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** 半径 (格) → 区块半径 (纯函数): (radius+15)>>4, 下限 1 — 16 格 = 1 区块 = 3×3 */
    static int chunkRadiusOf(int radius) {
        return Math.max(1, (radius + 15) >> 4);
    }

    /** L1 per-查询漂移命中判定 (纯函数): 结果未过期 (时钟回退视为过期) 且中心漂移 ≤ 门限 */
    static boolean shouldReuseQuery(long scanTick, long nowTick, double driftSq, double maxDriftSq) {
        return nowTick >= scanTick && nowTick - scanTick < CACHE_TTL_TICKS && driftSq <= maxDriftSq;
    }

    /** 并入合并集 (私有原语): 过滤 null/自排除/死实体/垂直窗外 — 三段调用 (新鲜/grace/新扫) 共用, 语义单一 */
    private static void mergeInto(List<LivingEntity> from, EntityMaid exclude, AABB window, List<LivingEntity> all) {
        for (LivingEntity e : from) {
            if (e != null && e != exclude && e.isAlive() && window.contains(e.position())) {
                all.add(e);
            }
        }
    }

    /** 单区块实体扫描原子原语 — 区块全高 AABB 内全部存活 LivingEntity (无分类无自排除) */
    static List<LivingEntity> scanChunk(ServerLevel level, int chunkX, int chunkZ) {
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight();
        AABB box = new AABB(chunkX * 16, minY, chunkZ * 16,
                chunkX * 16 + 16, maxY, chunkZ * 16 + 16);
        return List.copyOf(level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive));
    }

    /**
     * 预热 — 只把覆盖区块扫入缓存, 不合并结果 (每广播轮先扫在线玩家区块, 女仆随后全命中)。
     *
     * @param deadlineNanos 预热段独立预算截止 (System.nanoTime() + {@link #SCAN_BUDGET_NANOS})
     */
    public void warm(ServerLevel level, BlockPos center, int radius, long nowTick, long deadlineNanos) {
        String dim = level.dimension().location().toString();
        Map<Long, Entry> dimCache = cache.computeIfAbsent(dim, k -> new HashMap<>());
        lazyClean(dimCache, nowTick);
        int baseCX = center.getX() >> 4, baseCZ = center.getZ() >> 4;
        int chunkRadius = chunkRadiusOf(radius);   // 16 格 = 1 区块; 默认 16 格 → 3×3 = RingSpiral ring 0..1
        // v79.6x 螺旋枚举 (对齐结构通道): 预算耗尽时优先保中心区块 (女仆脚下最重要), 角落延后
        for (int ring = 0; ring <= chunkRadius; ring++) {
            int perimeter = RingSpiral.perimeter(ring);
            for (int i = 0; i < perimeter; i++) {
                int[] off = RingSpiral.offset(ring, i);
                if (Math.max(Math.abs(off[0]), Math.abs(off[1])) > chunkRadius) continue;
                int cx = baseCX + off[0], cz = baseCZ + off[1];
                long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                Entry entry = dimCache.get(key);
                if (entry != null && isFresh(entry.scanTick(), nowTick, CACHE_TTL_TICKS + GRACE_TICKS)) continue;   // grace: 宽限内视为已覆盖, 下一轮再刷
                if (System.nanoTime() >= deadlineNanos) return;   // 预算耗尽: 本轮预热到此为止
                dimCache.put(key, new Entry(nowTick, scanChunk(level, cx, cz)));
            }
        }
    }

    /**
     * 区块级扫描合并 — 输出与 {@link EntityScanner#scanEntities} 同构
     * (分类 monster/friendly/maid + 距离升序 + maxHits 截断 + 空分类不入 + Map.copyOf)。
     * 新鲜区块零成本命中; 过期区块在预算内重扫; 预算耗尽跳过 (迟发现不误报)。
     *
     * @param exclude 排除实体 (女仆自身 — 缓存共享不含排除, 查询方自行排除; 预热传 null)
     */
    public Map<String, List<LivingEntity>> scanAround(ServerLevel level, BlockPos center, int radius,
                                                      EntityMaid exclude, int maxHits,
                                                      long nowTick, long deadlineNanos) {
        String dim = level.dimension().location().toString();
        Map<Long, Entry> dimCache = cache.computeIfAbsent(dim, k -> new HashMap<>());
        lazyClean(dimCache, nowTick);
        // Retold 模式 ①: per-查询漂移门限 — 中心漂移 ≤3.5 格且结果未过期 → 直接复用上次结果
        // (静止/小幅走动的女仆零重扫零合并; 移动超门限自动落到下面的区块共享层)
        if (exclude != null) {
            QueryEntry q = queryCache.get(exclude.getId());
            if (q != null && shouldReuseQuery(q.scanTick(), nowTick,
                    driftSq(center, q.cx(), q.cy(), q.cz()), MAX_CENTER_DRIFT_SQ)) {
                // P1 修复: 快路径也过滤死实体 (与 L2 合并路径一致 — 缓存期内实体可能死亡)
                Map<String, List<LivingEntity>> alive = new HashMap<>();
                for (var cat : q.result().entrySet()) {
                    List<LivingEntity> living = cat.getValue().stream().filter(LivingEntity::isAlive).toList();
                    if (!living.isEmpty()) alive.put(cat.getKey(), List.copyOf(living));
                }
                return Map.copyOf(alive);
            }
        }

        // 垂直窗 ±SEARCH_VERTICAL — 与改造前 scanEntities 的 inflate(radius, vert, radius) 语义一致
        int vert = com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants.SEARCH_VERTICAL;
        AABB window = new AABB(center).inflate(radius, vert, radius);
        List<LivingEntity> all = new ArrayList<>();
        boolean incomplete = false;   // P2: 预算耗尽跳过 grace 外区块 → 结果不完整, 不写 L1 缓存

        // 覆盖区块枚举 (半径 16 格 → 恒 3×3 = 9 区块)
        int baseCX = center.getX() >> 4, baseCZ = center.getZ() >> 4;
        int chunkRadius = chunkRadiusOf(radius);   // 16 格 = 1 区块; 默认 16 格 → 3×3 = RingSpiral ring 0..1
        // v79.6x 螺旋枚举 (对齐结构通道): 预算耗尽时优先保中心区块, 角落延后
        for (int ring = 0; ring <= chunkRadius; ring++) {
            int perimeter = RingSpiral.perimeter(ring);
            for (int i = 0; i < perimeter; i++) {
                int[] off = RingSpiral.offset(ring, i);
                if (Math.max(Math.abs(off[0]), Math.abs(off[1])) > chunkRadius) continue;
                long key = net.minecraft.world.level.ChunkPos.asLong(baseCX + off[0], baseCZ + off[1]);
                Entry entry = dimCache.get(key);
                if (entry != null && isFresh(entry.scanTick(), nowTick, CACHE_TTL_TICKS)) {
                    mergeInto(entry.living(), exclude, window, all);
                    continue;
                }
                // 预算耗尽: grace 宽限内仍并入旧数据 (Retold 模式 ② — 不漏报); 更旧才跳过 (迟发现不误报)
                if (entry != null && isFresh(entry.scanTick(), nowTick, CACHE_TTL_TICKS + GRACE_TICKS)) {
                    mergeInto(entry.living(), exclude, window, all);
                    continue;
                }
                if (System.nanoTime() >= deadlineNanos) { incomplete = true; continue; }
                List<LivingEntity> one = scanChunk(level, baseCX + off[0], baseCZ + off[1]);
                dimCache.put(key, new Entry(nowTick, one));
                mergeInto(one, exclude, window, all);
            }
        }

        all.sort(Comparator.comparingDouble(e -> e.blockPosition().distSqr(center)));
        List<LivingEntity> monsters = new ArrayList<>();
        List<LivingEntity> friendlies = new ArrayList<>();
        List<LivingEntity> maids = new ArrayList<>();
        for (LivingEntity e : all) {
            if (e instanceof EntityMaid) {
                if (maids.size() < maxHits) maids.add(e);
            } else if (e.getType().getCategory() == MobCategory.MONSTER) {
                if (monsters.size() < maxHits) monsters.add(e);
            } else if (e instanceof Mob
                    && e.getType().getCategory() != MobCategory.MISC) {
                if (friendlies.size() < maxHits) friendlies.add(e);
            }
        }
        Map<String, List<LivingEntity>> result = new HashMap<>();
        if (!monsters.isEmpty()) result.put(EntityScanner.CAT_MONSTER, List.copyOf(monsters));
        if (!friendlies.isEmpty()) result.put(EntityScanner.CAT_FRIENDLY, List.copyOf(friendlies));
        if (!maids.isEmpty()) result.put(EntityScanner.CAT_MAID, List.copyOf(maids));
        if (exclude != null && !incomplete) {   // P2: 不完整结果不进 L1 (防漏报放大到多轮)
            queryCache.put(exclude.getId(), new QueryEntry(center.getX(), center.getY(), center.getZ(), nowTick, Map.copyOf(result)));
        }
        return Map.copyOf(result);
    }

    /** 懒清理 — 超规模清陈旧条目 (HORIZON_TICKS 外) */
    private void lazyClean(Map<Long, Entry> dimCache, long nowTick) {
        if (dimCache.size() >= MAX_ENTRIES) {
            dimCache.entrySet().removeIf(e -> !isFresh(e.getValue().scanTick(), nowTick, HORIZON_TICKS));
        }
        if (queryCache.size() >= MAX_ENTRIES) {
            queryCache.entrySet().removeIf(e -> !isFresh(e.getValue().scanTick(), nowTick, HORIZON_TICKS));
        }
    }

    /** 测试钩子: 指定维度缓存条目数 */
    public int cacheSize(String dimensionLocation) {
        Map<Long, Entry> m = cache.get(dimensionLocation);
        return m == null ? 0 : m.size();
    }
}
