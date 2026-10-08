package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 附近实体扫描器 (v79.6x 收编合并 — B 批原语抽离) — 两个历史来源:
 * <ul>
 *   <li>{@link #scanNearby} — 原 task/sense/EntityScan (v77.6 移植自 Numen ScanNearbyEntitiesTool):
 *       AI 可读完整清单 (id/类型/坐标/距离/hp/分类), 20 上限 + 截断标记, 分类 hostile/passive/player。</li>
 *   <li>{@link #scanEntities} + CAT_* — 原 EnvScanner.scanEntities (v37):
 *       EnvSense 快照分类 (monster/friendly/maid), maxHits 截断, 距离排序。</li>
 * </ul>
 * 防御点 (逐行保留): e != maid 自排除 / isAlive / isRemoved / maxHits 截断 /
 * 空表不入 map / List.copyOf + Map.copyOf 防外部修改 / "unknown" 兜底。
 */
public final class EntityScanner {

    /** EnvSense 实体分类常量 (自 EnvScanner 迁入; 广播器消费 friendly/maid, monster 分类原样保留) */
    public static final String CAT_MONSTER  = "monster";
    public static final String CAT_FRIENDLY = "friendly";
    public static final String CAT_MAID     = "maid";

    /** AI 清单上限 (超出截断标记) */
    public static final int MAX_ENTITIES = 20;

    private EntityScanner() {}

    // ── AI 可读清单 (原 EntityScan) ──

    /** 实体条目 */
    public record EntityInfo(String id, String type, int x, int y, int z, double distance,
                             float hp, String category) {}

    /** 扫描结果 (entities 按距离升序; truncated = 超过 20 上限) */
    public record Result(List<EntityInfo> entities, boolean truncated) {}

    /** 扫描附近实体 — typeFilter: hostile/passive/player/all */
    public static Result scanNearby(ServerLevel level, double cx, double cy, double cz,
                                    double radius, String typeFilter) {
        List<EntityInfo> list = new ArrayList<>();
        boolean truncated = false;
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                new AABB(cx - radius, cy - radius, cz - radius,
                        cx + radius, cy + radius, cz + radius), x -> true)) {
            if (e == null || e.isRemoved()) continue;
            String cat = categoryOf(e);
            if (!"all".equals(typeFilter) && !cat.equals(typeFilter)) continue;
            double dist = e.distanceToSqr(cx, cy, cz);
            if (list.size() >= MAX_ENTITIES) { truncated = true; break; }
            String type = e.getType().getDescriptionId().replace("entity.", "");
            list.add(new EntityInfo(e.getStringUUID(), type,
                    e.getBlockX(), e.getBlockY(), e.getBlockZ(),
                    Math.sqrt(dist), e instanceof LivingEntity le ? le.getHealth() : -1, cat));
        }
        list.sort(Comparator.comparingDouble(EntityInfo::distance));
        return new Result(list, truncated);
    }

    private static String categoryOf(net.minecraft.world.entity.Entity e) {
        if (e instanceof Player) return "player";
        if (e instanceof Enemy) return "hostile";
        if (e instanceof Animal) return "passive";
        return "passive";
    }

    // ── EnvSense 分类快照 (原 EnvScanner.scanEntities) ──

    /** 扫描附近实体（分类为 monster/friendly/maid; 空分类不入 map; 距离升序截断 maxHits） */
    public static Map<String, List<LivingEntity>> scanEntities(ServerLevel level, EntityMaid maid,
                                                               int radius, int maxHits) {
        Map<String, List<LivingEntity>> result = new HashMap<>();
        BlockPos center = maid.blockPosition();
        int vert = com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants.SEARCH_VERTICAL;
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
}
