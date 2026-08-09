package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;

/**
 * 环境感知快照 (v37→v63) — 一次环境扫描的不可变结果。
 *
 * <p>由 {@link EnvSenseBroadcaster} 每 200 tick 生成并缓存，
 * 任意代码可经 {@code EnvSenseBroadcaster.getSnapshot(maid)} O(1) 读取。
 *
 * <p><b>实体引用警告</b>：{@code entityHits} 持有的 {@link LivingEntity}
 * 可能在两次扫描间死亡/卸载，消费方使用前必须 {@code isAlive()} 复核。
 *
 * @param gameTime      扫描时的 gameTime
 * @param blockHits     分类id → 命中方块（按距离排序）
 * @param entityHits    分类id → 命中实体（按距离排序）
 * @param world         世界状态快照
 * @param worldSignals  本轮触发的边沿信号 (v72: String 信号 id)
 */
public record EnvSnapshot(long gameTime,
                          Map<String, List<BlockPos>> blockHits,
                          Map<String, List<LivingEntity>> entityHits,
                          WorldInfo world,
                          List<String> worldSignals) {

    /**
     * 世界状态快照 — 温度/降水判定与 TLM 对齐。
     *
     * @param tempCategory  温度档: COLD(<0.15) / OCEAN(<0.55) / MEDIUM(<0.95) / WARM
     * @param temperature   biome 基础温度 {@code getBaseTemperature()}
     * @param precipitation 女仆位置降水类型: NONE / RAIN / SNOW
     * @param dayTime       当日时间 0-23999
     * @param timeSegment   时间段: DAY(0-11999) / DUSK(12000-13799) / NIGHT(13800-22199) / DAWN(22200-23999)
     * @param biomeId       生物群系 registry id (namespace:path; 未知 "unknown")
     * @param structuresAt  站立点所在结构 registry id (排序逗号连接; 空串 = 不在任何结构)
     */
    public record WorldInfo(boolean day, boolean raining, boolean thundering,
                            int moonPhase, int lightAtMaid, String dimension,
                            String tempCategory, float temperature,
                            String precipitation, long dayTime, String timeSegment,
                            String biomeId, String structuresAt) {}

    /** 指定分类的命中方块（无命中返回空列表） */
    public List<BlockPos> blocks(String category) {
        return blockHits != null ? blockHits.getOrDefault(category, List.of()) : List.of();
    }

    /** 指定分类的命中实体（无命中返回空列表） */
    public List<LivingEntity> entities(String category) {
        return entityHits != null ? entityHits.getOrDefault(category, List.of()) : List.of();
    }

    /** 指定分类本轮是否命中（方块/实体/世界信号任一） */
    public boolean hit(String category) {
        return !blocks(category).isEmpty() || !entities(category).isEmpty();
    }
}
