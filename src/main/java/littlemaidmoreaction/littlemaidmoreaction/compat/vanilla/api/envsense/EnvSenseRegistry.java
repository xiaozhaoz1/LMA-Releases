package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.envsense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * 环境感知器注册中心 (v37) — 仿 MaidEditorRegistry 静态注册模式。
 *
 * <p>其他模块（任务/compat/规则动作）注册感知器声明"关心什么"；
 * {@link EnvSenseScheduler} 每 200 tick（可配置）对有适用感知器的女仆
 * 做一次合并扫描，命中时经回调 + {@code lma_env_scan} 规则事件双通道触发。
 *
 * <p><b>零注册 = 零开销</b>：无感知器时调度器每 tick 只做一次布尔判断。
 * v37.1 起内置世界感知器常驻（{@link BuiltinEnvSensors}）。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * EnvSenseRegistry.addBlockSensor("nearby_logs",
 *     (pos, state) -> state.is(BlockTags.LOGS),
 *     maid -> maid.getTask().getUid().getPath().contains("collect_wood"), // 或 null=所有女仆
 *     (maid, snap) -> { ... snap.blocks("nearby_logs") ... });
 * }</pre>
 */
public final class EnvSenseRegistry {

    /** 感知器命中回调 — 与规则事件共享同一次扫描结果 */
    @FunctionalInterface
    public interface SensorCallback {
        void onScan(EntityMaid maid, EnvSnapshot snapshot);
    }

    /** v37.1: 世界状态变化触发器 — prev 为 null 表示该女仆首轮扫描 */
    @FunctionalInterface
    public interface WorldTrigger {
        boolean test(@Nullable EnvSnapshot.WorldInfo prev, EnvSnapshot.WorldInfo now);
    }

    /** 方块感知器: appliesTo 为 null 表示适用所有女仆 */
    public record BlockSensor(String id, BiPredicate<BlockPos, BlockState> matcher,
                              @Nullable Predicate<EntityMaid> appliesTo,
                              @Nullable SensorCallback callback) {}

    /** 实体感知器 */
    public record EntitySensor(String id, Predicate<LivingEntity> matcher,
                               @Nullable Predicate<EntityMaid> appliesTo,
                               @Nullable SensorCallback callback) {}

    /** v37.1: 世界感知器 — 基于 prev/now 快照对比的边沿触发（太冷太热/昼夜/天气/维度变化） */
    public record WorldSensor(String id, WorldTrigger trigger,
                              @Nullable Predicate<EntityMaid> appliesTo,
                              @Nullable SensorCallback callback) {}

    /** v37.2: 结构感知器 — 低频 findNearestMapStructure 探测（村庄/前哨站/矿井），边沿触发 */
    public record StructureSensor(String id, TagKey<Structure> tag,
                                  @Nullable Predicate<EntityMaid> appliesTo,
                                  @Nullable SensorCallback callback) {}

    private static final Map<String, BlockSensor> BLOCK_SENSORS = new ConcurrentHashMap<>();
    private static final Map<String, EntitySensor> ENTITY_SENSORS = new ConcurrentHashMap<>();
    private static final Map<String, WorldSensor> WORLD_SENSORS = new ConcurrentHashMap<>();
    private static final Map<String, StructureSensor> STRUCTURE_SENSORS = new ConcurrentHashMap<>();

    private EnvSenseRegistry() {}

    /** 注册方块感知器。id 全局唯一，重复注册 WARN 并忽略（防静默覆盖） */
    public static void addBlockSensor(String id, BiPredicate<BlockPos, BlockState> matcher,
                                      @Nullable Predicate<EntityMaid> appliesTo,
                                      @Nullable SensorCallback callback) {
        if (isDuplicate(id)) return;
        BLOCK_SENSORS.put(id, new BlockSensor(id, matcher, appliesTo, callback));
    }

    /** 注册实体感知器。id 全局唯一，重复注册 WARN 并忽略 */
    public static void addEntitySensor(String id, Predicate<LivingEntity> matcher,
                                       @Nullable Predicate<EntityMaid> appliesTo,
                                       @Nullable SensorCallback callback) {
        if (isDuplicate(id)) return;
        ENTITY_SENSORS.put(id, new EntitySensor(id, matcher, appliesTo, callback));
    }

    /** v37.1: 注册世界感知器（prev/now 边沿触发）。id 全局唯一，重复注册 WARN 并忽略 */
    public static void addWorldSensor(String id, WorldTrigger trigger,
                                      @Nullable Predicate<EntityMaid> appliesTo,
                                      @Nullable SensorCallback callback) {
        if (isDuplicate(id)) return;
        WORLD_SENSORS.put(id, new WorldSensor(id, trigger, appliesTo, callback));
    }

    /** v37.2: 注册结构感知器（低频探测，间隔 config structure_interval_ticks）。id 全局唯一 */
    public static void addStructureSensor(String id, TagKey<Structure> tag,
                                          @Nullable Predicate<EntityMaid> appliesTo,
                                          @Nullable SensorCallback callback) {
        if (isDuplicate(id)) return;
        STRUCTURE_SENSORS.put(id, new StructureSensor(id, tag, appliesTo, callback));
    }

    /** 是否有任何感知器（调度器快速门控） */
    public static boolean hasSensors() {
        return !BLOCK_SENSORS.isEmpty() || !ENTITY_SENSORS.isEmpty()
                || !WORLD_SENSORS.isEmpty() || !STRUCTURE_SENSORS.isEmpty();
    }

    public static Collection<BlockSensor> blockSensors() {
        return BLOCK_SENSORS.values();
    }

    public static Collection<EntitySensor> entitySensors() {
        return ENTITY_SENSORS.values();
    }

    public static Collection<WorldSensor> worldSensors() {
        return WORLD_SENSORS.values();
    }

    public static Collection<StructureSensor> structureSensors() {
        return STRUCTURE_SENSORS.values();
    }

    /** 测试/热重载用：移除感知器 */
    public static void remove(String id) {
        BLOCK_SENSORS.remove(id);
        ENTITY_SENSORS.remove(id);
        WORLD_SENSORS.remove(id);
        STRUCTURE_SENSORS.remove(id);
    }

    private static boolean isDuplicate(String id) {
        if (BLOCK_SENSORS.containsKey(id) || ENTITY_SENSORS.containsKey(id)
                || WORLD_SENSORS.containsKey(id) || STRUCTURE_SENSORS.containsKey(id)) {
            LittleMaidMoreAction.LOGGER.warn("[EnvSense] 感知器 id 重复注册被忽略: {}", id);
            return true;
        }
        return false;
    }
}
