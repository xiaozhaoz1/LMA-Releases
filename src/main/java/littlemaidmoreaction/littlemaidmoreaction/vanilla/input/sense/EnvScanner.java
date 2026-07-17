package littlemaidmoreaction.littlemaidmoreaction.vanilla.input.sense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.VanillaConstants;
import littlemaidmoreaction.littlemaidmoreaction.api.envsense.EnvSenseRegistry.BlockSensor;
import littlemaidmoreaction.littlemaidmoreaction.api.envsense.EnvSenseRegistry.EntitySensor;
import littlemaidmoreaction.littlemaidmoreaction.api.envsense.EnvSnapshot;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.world.WorldStateReader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 环境扫描器 (v37, 输入层) — 单次合并扫描产出 {@link EnvSnapshot}。
 *
 * <p>核心低耗设计：N 个方块感知器共享同一次 {@code (2r+1)²×9} 方块遍历
 * （每个位置对所有 matcher 逐个测试），实体感知器共享同一次 AABB 查询。
 * 不做多次独立扫描。
 */
public final class EnvScanner {

    private EnvScanner() {}

    /**
     * 执行一次合并扫描。
     *
     * @param blockSensors  本次适用的方块感知器（已按 appliesTo 过滤）
     * @param entitySensors 本次适用的实体感知器（已按 appliesTo 过滤）
     * @param radius        水平半径（工作范围或 config 默认）
     * @param maxHits       每感知器命中上限
     */
    public static EnvSnapshot scan(ServerLevel level, EntityMaid maid,
                                   List<BlockSensor> blockSensors,
                                   List<EntitySensor> entitySensors,
                                   int radius, int maxHits) {
        BlockPos center = maid.blockPosition();
        int vert = VanillaConstants.SEARCH_VERTICAL;

        // ── 方块: 共享单次遍历 ──
        Map<String, List<BlockPos>> blockHits = new HashMap<>();
        if (!blockSensors.isEmpty()) {
            BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
            int cx = center.getX(), cy = center.getY(), cz = center.getZ();
            for (int y = -vert; y <= vert; y++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        mPos.set(cx + x, cy + y, cz + z);
                        BlockState state = level.getBlockState(mPos);
                        if (state.isAir()) continue;
                        for (BlockSensor sensor : blockSensors) {
                            List<BlockPos> list = blockHits.get(sensor.id());
                            if (list != null && list.size() >= maxHits) continue;
                            if (sensor.matcher().test(mPos, state)) {
                                blockHits.computeIfAbsent(sensor.id(), k -> new ArrayList<>())
                                        .add(mPos.immutable());
                            }
                        }
                    }
                }
            }
            // 遍历序 → 距离序
            blockHits.replaceAll((k, v) -> {
                v.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
                return List.copyOf(v);
            });
        }

        // ── 实体: 共享单次 AABB 查询 ──
        Map<String, List<LivingEntity>> entityHits = new HashMap<>();
        if (!entitySensors.isEmpty()) {
            AABB aabb = new AABB(center).inflate(radius, vert, radius);
            List<LivingEntity> all = level.getEntitiesOfClass(LivingEntity.class, aabb,
                    e -> e != maid && e.isAlive());
            all.sort(Comparator.comparingDouble(e -> e.blockPosition().distSqr(center)));
            for (EntitySensor sensor : entitySensors) {
                List<LivingEntity> matched = new ArrayList<>();
                for (LivingEntity e : all) {
                    if (matched.size() >= maxHits) break;
                    if (sensor.matcher().test(e)) matched.add(e);
                }
                if (!matched.isEmpty()) {
                    entityHits.put(sensor.id(), List.copyOf(matched));
                }
            }
        }

        // ── 世界状态: 直读快照（温度/降水判定对齐 TLM IMaid.getAtBiomeTemp / SoundUtil） ──
        var biome = level.getBiome(center).value();
        long dayTime = WorldStateReader.getTime(level);
        EnvSnapshot.WorldInfo world = new EnvSnapshot.WorldInfo(
                WorldStateReader.isDay(level),
                WorldStateReader.isRaining(level),
                WorldStateReader.isThundering(level),
                WorldStateReader.getMoonPhase(level),
                WorldStateReader.getLightLevel(level, center),
                WorldStateReader.getDimension(level),
                tempCategory(biome.getBaseTemperature()),
                biome.getBaseTemperature(),
                biome.getPrecipitationAt(center).name(),
                dayTime,
                timeSegment(dayTime));

        // worldTriggers 由调度器基于 prev/now 对比填充
        return new EnvSnapshot(level.getGameTime(),
                Map.copyOf(blockHits), Map.copyOf(entityHits), world, List.of());
    }

    /** TLM IMaid.getAtBiomeTemp 四档阈值 */
    private static String tempCategory(float baseTemp) {
        if (baseTemp < 0.15f) return "COLD";
        if (baseTemp < 0.55f) return "OCEAN";
        if (baseTemp < 0.95f) return "MEDIUM";
        return "WARM";
    }

    /** v37.2 时间段划分（MC 无官方常量，自定义边界） */
    private static String timeSegment(long dayTime) {
        if (dayTime < 12000) return "DAY";
        if (dayTime < 13800) return "DUSK";
        if (dayTime < 22200) return "NIGHT";
        return "DAWN";
    }
}
