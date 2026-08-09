package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.WorldStateReader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * 环境扫描器 (v37→v63) — 纯工具类，无状态。
 *
 * <p>不管理调度/缓存/回调。由 {@link EnvSenseBroadcaster} 按需调用。
 * 每个 Pipeline 也可独立调用轻量方法（如 scanSnow/scanDark）。
 *
 * <p>v79.3: tempCategory/timeSegment 委托 {@link EnvRules} (纯 JVM 可测);
 * WorldInfo +biomeId/structuresAt 字段。
 */
public final class EnvScanner {

    private EnvScanner() {}

    // ── 实体分类常量 ──
    public static final String CAT_MONSTER  = "monster";
    public static final String CAT_FRIENDLY = "friendly";
    public static final String CAT_MAID     = "maid";

    // ── 综合扫描 (广播器用) ──

    /** 读取世界状态快照（轻量 — 温度/天气/时间直读，无方块遍历） */
    public static EnvSnapshot.WorldInfo readWorld(ServerLevel level, BlockPos center) {
        var biomeHolder = level.getBiome(center);
        var biome = biomeHolder.value();
        long dayTime = WorldStateReader.getTime(level);
        return new EnvSnapshot.WorldInfo(
                WorldStateReader.isDay(level),
                WorldStateReader.isRaining(level),
                WorldStateReader.isThundering(level),
                WorldStateReader.getMoonPhase(level),
                WorldStateReader.getLightLevel(level, center),
                WorldStateReader.getDimension(level),
                EnvRules.tempCategory(biome.getBaseTemperature()),
                biome.getBaseTemperature(),
                biome.getPrecipitationAt(center).name(),
                dayTime,
                EnvRules.timeSegment(dayTime),
                biomeId(biomeHolder),
                structuresAt(level, center));
    }

    /** biome Holder → registry id (未知 → "unknown") */
    private static String biomeId(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder) {
        return holder.unwrapKey()
                .map(k -> k.location().toString())
                .orElse("unknown");
    }

    /**
     * 站立点所在结构 (v79.3, 零成本 — getAllStructuresAt 瞬时查询, 与 24000t 最近结构通道互补)。
     * 返回排序逗号连接的 structure registry id; 空串 = 不在任何结构。
     * public — SenseApi.structuresAt 暴露。
     */
    public static String structuresAt(ServerLevel level, BlockPos center) {
        var structs = level.structureManager().getAllStructuresAt(center);
        if (structs.isEmpty()) return "";
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        return structs.keySet().stream()
                .map(s -> registry.getResourceKey(s)
                        .map(k -> k.location().toString())
                        .orElse("unknown"))
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    /** 扫描附近实体（分类为 monster/friendly/maid） */
    public static Map<String, List<LivingEntity>> scanEntities(ServerLevel level, EntityMaid maid,
                                                                int radius, int maxHits) {
        Map<String, List<LivingEntity>> result = new HashMap<>();
        BlockPos center = maid.blockPosition();
        int vert = VanillaConstants.SEARCH_VERTICAL;
        AABB aabb = new AABB(center).inflate(radius, vert, radius);

        List<LivingEntity> all = level.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e != maid && e.isAlive());
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

        if (!monsters.isEmpty()) result.put(CAT_MONSTER, List.copyOf(monsters));
        if (!friendlies.isEmpty()) result.put(CAT_FRIENDLY, List.copyOf(friendlies));
        if (!maids.isEmpty()) result.put(CAT_MAID, List.copyOf(maids));
        return Map.copyOf(result);
    }

    // ── 轻量工具 (Pipeline tick 内用) ──

    /** 扫描附近雪层 (SnowLayer / PowderSnow / TopSnow)，返回距离排序列表 */
    public static List<BlockPos> scanSnowBlocks(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        int vert = 4;
        for (int y = -vert; y <= vert; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    mPos.set(cx + x, cy + y, cz + z);
                    var state = level.getBlockState(mPos);
                    if (state.is(net.minecraft.tags.BlockTags.SNOW)) {
                        found.add(mPos.immutable());
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return found;
    }

    /** 扫描附近红石灯 */
    public static List<BlockPos> scanRedstoneLamps(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        for (int y = -4; y <= 4; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    mPos.set(cx + x, cy + y, cz + z);
                    if (level.getBlockState(mPos).is(net.minecraft.world.level.block.Blocks.REDSTONE_LAMP)) {
                        found.add(mPos.immutable());
                    }
                }
            }
        }
        return found;
    }
}
