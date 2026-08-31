package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EnvEdgeDetector} 纯 JVM 测试 (v79.3) — 消费信号边沿逐条正反 + 首帧/无变化语义。
 * v79.61x: 死信号同族清理后只测 7 个有消费方检出 (SNOWING/WEATHER_CLEAR/TEMP_COLD/TEMP_HOT/TEMP_NORMAL/DARKNESS/MAID_NEARBY).
 */
class EnvEdgeDetectorTest {

    private static final EnvEdgeDetector.EnvConfig CFG =
            new EnvEdgeDetector.EnvConfig(0.15f, 1.0f, 7);

    /** WorldInfo helper — 默认全 false/0/空串, 按需覆写 */
    private static EnvSnapshot.WorldInfo info(boolean day, boolean raining, String precip,
                                              float temp, int light, String dimension,
                                              String timeSeg, String biomeId, String structsAt) {
        return new EnvSnapshot.WorldInfo(day, raining, false, 0, light, dimension,
                EnvRules.tempCategory(temp), temp, precip, 1000, timeSeg, biomeId, structsAt);
    }

    private static EnvSnapshot.WorldInfo base() {
        return info(true, false, "NONE", 0.5f, 12, "minecraft:overworld", "DAY", "minecraft:plains", "");
    }

    private static Set<EnvSignal> detect(EnvSnapshot.WorldInfo prev, EnvSnapshot.WorldInfo now) {
        return EnvEdgeDetector.detect(prev, now,
                EnvEdgeDetector.EntityPresence.NONE, EnvEdgeDetector.EntityPresence.NONE, CFG);
    }

    private static Set<EnvSignal> detectWithEnts(EnvSnapshot.WorldInfo prev, EnvSnapshot.WorldInfo now,
                                                 EnvEdgeDetector.EntityPresence p, EnvEdgeDetector.EntityPresence n) {
        return EnvEdgeDetector.detect(prev, now, p, n, CFG);
    }

    // ── 天气 (v79.62: 检出保留 — LLM 对话上下文, 用户裁定) ──

    @Test
    @DisplayName("SNOWING: 首帧即触发; 雨→雪边沿; 持续/停雪不触发")
    void snowing_onset() {
        var snowing = info(true, true, "SNOW", 0.2f, 12, "minecraft:overworld", "DAY", "minecraft:plains", "");
        assertTrue(detect(null, snowing).contains(EnvSignal.SNOWING), "首帧雪 → SNOWING");
        assertTrue(detect(base(), snowing).contains(EnvSignal.SNOWING), "雨→雪边沿");
        assertFalse(detect(snowing, snowing).contains(EnvSignal.SNOWING), "持续雪不重复");
        assertFalse(detect(snowing, base()).contains(EnvSignal.SNOWING), "停雪不触发");
    }

    @Test
    @DisplayName("WEATHER_CLEAR: 雨→晴 (RAINING 死信号已删)")
    void weather_clear() {
        var raining = info(true, true, "RAIN", 0.5f, 12, "minecraft:overworld", "DAY", "minecraft:plains", "");
        assertTrue(detect(raining, base()).contains(EnvSignal.WEATHER_CLEAR));
        assertFalse(detect(base(), base()).contains(EnvSignal.WEATHER_CLEAR), "持续晴不触发");
    }

    // ── 温度 (TempAdaptPipeline) ──

    @Test
    @DisplayName("TEMP_COLD/HOT/NORMAL 边沿")
    void temp_edges() {
        var cold = info(true, false, "NONE", 0.05f, 12, "minecraft:overworld", "DAY", "minecraft:plains", "");
        var hot = info(true, false, "NONE", 1.5f, 12, "minecraft:overworld", "DAY", "minecraft:desert", "");
        assertTrue(detect(null, cold).contains(EnvSignal.TEMP_COLD), "首帧冷触发");
        assertTrue(detect(base(), cold).contains(EnvSignal.TEMP_COLD));
        assertTrue(detect(cold, base()).contains(EnvSignal.TEMP_NORMAL), "冷→常 TEMP_NORMAL");
        assertTrue(detect(base(), hot).contains(EnvSignal.TEMP_HOT));
        assertTrue(detect(hot, base()).contains(EnvSignal.TEMP_NORMAL));
        assertFalse(detect(cold, cold).contains(EnvSignal.TEMP_COLD), "持续冷不重复");
    }

    // ── 黑暗 (TorchLightPipeline) ──

    @Test
    @DisplayName("DARKNESS: light < 7 onset")
    void darkness_edge() {
        var dark = info(true, false, "NONE", 0.5f, 3, "minecraft:overworld", "DAY", "minecraft:plains", "");
        assertTrue(detect(null, dark).contains(EnvSignal.DARKNESS), "首帧黑暗触发 (状态进入)");
        assertTrue(detect(base(), dark).contains(EnvSignal.DARKNESS));
        assertFalse(detect(dark, dark).contains(EnvSignal.DARKNESS));
    }

    // ── 实体 (HaqiPipeline) ──

    @Test
    @DisplayName("MAID_NEARBY 仅出现边沿 (FRIENDLY_* / MAID_CLEAR 死信号已删)")
    void maid_nearby_edge() {
        var hasMaid = new EnvEdgeDetector.EntityPresence(false, true);
        assertTrue(detectWithEnts(base(), base(), EnvEdgeDetector.EntityPresence.NONE, hasMaid)
                .contains(EnvSignal.MAID_NEARBY));
        assertFalse(detectWithEnts(base(), base(), hasMaid, hasMaid).contains(EnvSignal.MAID_NEARBY),
                "持续在场不重复");
        // 消失不触发 MAID_NEARBY (CLEAR 死信号已删, 不产出)
        assertFalse(detectWithEnts(base(), base(), hasMaid, EnvEdgeDetector.EntityPresence.NONE)
                .contains(EnvSignal.MAID_NEARBY));
    }

    @Test
    @DisplayName("无变化 → 零信号; now==null → 零信号")
    void no_change_and_null() {
        assertTrue(detect(base(), base()).isEmpty(), "完全无变化零信号");
        assertTrue(EnvEdgeDetector.detect(base(), null, null, EnvEdgeDetector.EntityPresence.NONE, CFG).isEmpty());
    }
}
