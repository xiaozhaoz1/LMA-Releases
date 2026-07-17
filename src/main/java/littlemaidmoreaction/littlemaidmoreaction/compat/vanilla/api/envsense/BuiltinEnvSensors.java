package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.envsense;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.envsense.EnvSnapshot.WorldInfo;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * 内置环境感知器 (v37.1/v37.2) — 常驻注册，判定对齐 TLM。
 *
 * <p>appliesTo=null（所有女仆常驻）、callback=null（纯 lma_env_scan 规则事件通道，
 * 代码消费方经 {@code EnvSenseScheduler.getSnapshot} 自取）。
 * 玩家用法：规则事件 {@code lma_env_scan} + 条件 {@code env_sensor_hit(sensor_id=...)}。
 *
 * <h3>触发语义</h3>
 * <ul>
 *   <li>世界/结构感知器 = <b>边沿触发</b>：进入状态那一轮触发一次，不重复</li>
 *   <li>实体感知器 = <b>存在即命中</b>：目标在场每轮均命中（规则侧用 cooldown 控频，
 *       快照 {@code entities(id)} 可拿实体列表）</li>
 * </ul>
 */
public final class BuiltinEnvSensors {

    private static volatile boolean registered = false;

    private BuiltinEnvSensors() {}

    /** 幂等注册（防多入口双重初始化 — 错题集 #19） */
    public static void init() {
        if (registered) return;
        registered = true;

        registerWorldSensors();
        registerEntitySensors();
        registerStructureSensors();

        LittleMaidMoreAction.LOGGER.info("[EnvSense] 内置感知器已注册 (世界6 + 实体3 + 结构3)");
    }

    // ── v37.1 世界感知器（边沿触发） ──

    private static void registerWorldSensors() {
        // 太冷: TLM IMaid.getAtBiomeTemp COLD 档（基础温度 < 冷阈值, 默认 0.15）
        EnvSenseRegistry.addWorldSensor("env_too_cold",
                (prev, now) -> isCold(now) && (prev == null || !isCold(prev)),
                null, null);

        // 太热: TLM SoundUtil.shouldSnowGolemBurn（温度 > 热阈值, 默认 1.0）
        EnvSenseRegistry.addWorldSensor("env_too_hot",
                (prev, now) -> isHot(now) && (prev == null || !isHot(prev)),
                null, null);

        // 正在下雪: 世界下雨中 且 女仆位置降水类型为 SNOW（TLM isSnowyBiome）
        EnvSenseRegistry.addWorldSensor("env_snowing",
                (prev, now) -> isSnowing(now) && (prev == null || !isSnowing(prev)),
                null, null);

        // 昼夜切换
        EnvSenseRegistry.addWorldSensor("env_day_night_change",
                (prev, now) -> prev != null && prev.day() != now.day(),
                null, null);

        // 天气变化 (雨/雷任一翻转)
        EnvSenseRegistry.addWorldSensor("env_weather_change",
                (prev, now) -> prev != null
                        && (prev.raining() != now.raining() || prev.thundering() != now.thundering()),
                null, null);

        // 维度变化
        EnvSenseRegistry.addWorldSensor("env_dimension_change",
                (prev, now) -> prev != null && !prev.dimension().equals(now.dimension()),
                null, null);

        // v37.2: 时间段切换（DAY/DUSK/NIGHT/DAWN 四段，比昼夜更细）
        EnvSenseRegistry.addWorldSensor("env_time_segment_change",
                (prev, now) -> prev != null && !prev.timeSegment().equals(now.timeSegment()),
                null, null);

        // v37.2: 进入黑暗（亮度低于怪物生成阈值, 默认 <7）
        EnvSenseRegistry.addWorldSensor("env_darkness",
                (prev, now) -> isDark(now) && (prev == null || !isDark(prev)),
                null, null);

        // v37.2: 雷暴开始（比 weather_change 更精确的单边沿）
        EnvSenseRegistry.addWorldSensor("env_thunder_start",
                (prev, now) -> prev != null && !prev.thundering() && now.thundering(),
                null, null);
    }

    // ── v37.2 实体感知器（存在即命中，扫描器已排除女仆自身+死亡实体） ──

    private static void registerEntitySensors() {
        // 怪物: TLM 先例 MobCategory.MONSTER（MobSpawnInfoRegistry）
        EnvSenseRegistry.addEntitySensor("env_nearby_monster",
                e -> e.getType().getCategory() == MobCategory.MONSTER,
                null, null);

        // 友好生物: 非怪物非杂项的 Mob，排除女仆（女仆单列）
        EnvSenseRegistry.addEntitySensor("env_nearby_friendly",
                e -> e instanceof Mob && !(e instanceof EntityMaid)
                        && e.getType().getCategory() != MobCategory.MONSTER
                        && e.getType().getCategory() != MobCategory.MISC,
                null, null);

        // 其他女仆
        EnvSenseRegistry.addEntitySensor("env_nearby_maid",
                e -> e instanceof EntityMaid,
                null, null);
    }

    // ── v37.2 结构感知器（低频边沿，默认每 MC 天一次） ──

    private static void registerStructureSensors() {
        // 村庄 / 废弃矿井: 原版标签
        EnvSenseRegistry.addStructureSensor("env_village_nearby",
                StructureTags.VILLAGE, null, null);
        EnvSenseRegistry.addStructureSensor("env_mineshaft_nearby",
                StructureTags.MINESHAFT, null, null);

        // 掠夺者前哨站: 原版无此标签 → LMA 自带 datapack 标签
        // data/littlemaidmoreaction/tags/worldgen/structure/pillager_outpost.json
        TagKey<Structure> outpost = TagKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(LittleMaidMoreAction.MOD_ID, "pillager_outpost"));
        EnvSenseRegistry.addStructureSensor("env_pillager_outpost_nearby",
                outpost, null, null);
    }

    // ── 判定（供触发器与单测复用） ──

    static boolean isCold(WorldInfo w) {
        return w.temperature() < MoreActionConfig.ENV_COLD_THRESHOLD.get().floatValue();
    }

    static boolean isHot(WorldInfo w) {
        return w.temperature() > MoreActionConfig.ENV_HOT_THRESHOLD.get().floatValue();
    }

    static boolean isSnowing(WorldInfo w) {
        return w.raining() && "SNOW".equals(w.precipitation());
    }

    static boolean isDark(WorldInfo w) {
        return w.lightAtMaid() < MoreActionConfig.ENV_DARKNESS_THRESHOLD.get();
    }
}
