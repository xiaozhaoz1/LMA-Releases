package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.envsense;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.envsense.EnvSnapshot.WorldInfo;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;

/**
 * 内置世界感知器 (v37.1) — 6 个常驻边沿触发器，判定与 TLM 对齐。
 *
 * <p>appliesTo=null（所有女仆常驻）、callback=null（纯 lma_env_scan 规则事件通道，
 * 代码消费方经 {@code EnvSenseScheduler.getSnapshot} 自取）。
 * 玩家用法：规则事件 {@code lma_env_scan} + 条件 {@code env_sensor_hit(sensor_id=...)}。
 *
 * <p>边沿检测：仅在<b>进入</b>状态那一轮触发一次，持续处于状态不重复触发。
 */
public final class BuiltinEnvSensors {

    private static volatile boolean registered = false;

    private BuiltinEnvSensors() {}

    /** 幂等注册（防多入口双重初始化 — 错题集 #19） */
    public static void init() {
        if (registered) return;
        registered = true;

        // 太冷: TLM IMaid.getAtBiomeTemp COLD 档（基础温度 < 冷阈值, 默认 0.15）
        EnvSenseRegistry.addWorldSensor("env_too_cold",
                (prev, now) -> isCold(now) && (prev == null || !isCold(prev)),
                null, null);

        // 太热: TLM SoundUtil.shouldSnowGolemBurn（位置温度 > 热阈值, 默认 1.0）
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

        LittleMaidMoreAction.LOGGER.info("[EnvSense] 内置世界感知器已注册 (6 个常驻)");
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
}
