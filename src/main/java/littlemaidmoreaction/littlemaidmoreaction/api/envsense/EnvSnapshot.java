package littlemaidmoreaction.littlemaidmoreaction.api.envsense;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;

/**
 * 环境感知快照 (v37) — 一次合并扫描的不可变结果。
 *
 * <p>由 {@link EnvSenseScheduler} 每个扫描周期（默认 200 tick）生成并缓存，
 * 任意代码可经 {@code EnvSenseScheduler.getSnapshot(maid)} O(1) 读取。
 *
 * <p><b>实体引用警告</b>：{@code entityHits} 持有的 {@link LivingEntity}
 * 可能在两次扫描间死亡/卸载，消费方使用前必须 {@code isAlive()} 复核。
 *
 * @param gameTime      扫描时的 gameTime
 * @param blockHits     感知器 id → 命中方块（按距离排序，截断至 config max_hits）
 * @param entityHits    感知器 id → 命中实体（按距离排序，截断至 config max_hits）
 * @param world         世界状态快照
 * @param worldTriggers v37.1: 本轮触发（边沿命中）的世界感知器 id
 */
public record EnvSnapshot(long gameTime,
                          Map<String, List<BlockPos>> blockHits,
                          Map<String, List<LivingEntity>> entityHits,
                          WorldInfo world,
                          List<String> worldTriggers) {

    /**
     * 世界状态快照 — 温度/降水判定与 TLM 对齐
     * (IMaid.getAtBiomeTemp 四档 / SoundUtil.getPrecipitationAt)。
     *
     * @param tempCategory  温度档: COLD(<0.15) / OCEAN(<0.55) / MEDIUM(<0.95) / WARM — 基于 biome 基础温度
     * @param temperature   biome 基础温度 {@code getBaseTemperature()}（TLM IMaid 同源；>1.0F 为炎热。
     *                      注: 位置温度 getTemperature(pos) 在本映射为 private，TLM 靠 AT 开放，LMA 不引入 AT）
     * @param precipitation 女仆位置降水类型: NONE / RAIN / SNOW
     * @param dayTime       当日时间 0-23999
     * @param timeSegment   v37.2 时间段: DAY(0-11999) / DUSK(12000-13799) / NIGHT(13800-22199) / DAWN(22200-23999)
     *                      — MC 无官方边界常量，此为自定义划分
     */
    public record WorldInfo(boolean day, boolean raining, boolean thundering,
                            int moonPhase, int lightAtMaid, String dimension,
                            String tempCategory, float temperature,
                            String precipitation, long dayTime, String timeSegment) {}

    /** 指定感知器的命中方块（无命中返回空列表） */
    public List<BlockPos> blocks(String sensorId) {
        return blockHits.getOrDefault(sensorId, List.of());
    }

    /** 指定感知器的命中实体（无命中返回空列表） */
    public List<LivingEntity> entities(String sensorId) {
        return entityHits.getOrDefault(sensorId, List.of());
    }

    /** 指定感知器本轮是否命中（方块/实体/世界触发任一） */
    public boolean hit(String sensorId) {
        return !blocks(sensorId).isEmpty()
                || !entities(sensorId).isEmpty()
                || worldTriggers.contains(sensorId);
    }
}
