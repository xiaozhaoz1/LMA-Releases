package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 附近实体清单 (v77.6 移植自 Numen ScanNearbyEntitiesTool) — 按距离排序, 20 上限, 截断标记。
 *
 * <p>EnvSense 只有布尔信号 (monster_nearby); 本读取器补完整清单 (id/类型/距离/hp/分类) —
 * LLM 决策攻击/逃跑目标。分类: hostile (Enemy)/passive (Animal)/player/all。
 */
public final class EntityScan {

    public static final int MAX_ENTITIES = 20;

    private EntityScan() {}

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
}
