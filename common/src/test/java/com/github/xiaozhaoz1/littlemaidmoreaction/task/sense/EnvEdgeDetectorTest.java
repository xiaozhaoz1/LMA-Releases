package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EnvEdgeDetector} 纯 JVM 测试 (v79.3) — 21 边沿逐条正反 + 首帧/无变化语义。
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

    // ── 天气 ──

    @Test
    @DisplayName("SNOWING: 首帧即触发 (状态进入语义); 非雪不触发")
    void snowing_onset() {
        var snowing = info(true, true, "SNOW", 0.2f, 12, "minecraft:overworld", "DAY", "minecraft:plains", "");
        assertTrue(detect(null, snowing).contains(EnvSignal.SNOWING), "首帧雪 → SNOWING");
        assertTrue(detect(base(), snowing).contains(EnvSignal.SNOWING), "雨→雪边沿");
        assertFalse(detect(snowing, snowing).contains(EnvSignal.SNOWING), "持续雪不重复");
        assertFalse(detect(snowing, base()).contains(EnvSignal.SNOWING), "停雪不触发");
    }

    @Test
    @DisplayName("RAINING: 非雪雨 onset; WEATHER_CLEAR: 雨→晴")
    void raining_and_clear() {
        var raining = info(true, true, "RAIN", 0.5f, 12, "minecraft:overworld", "DAY", "minecraft:plains", "");
        assertTrue(detect(base(), raining).contains(EnvSignal.RAINING));
        assertTrue(detect(raining, base()).contains(EnvSignal.WEATHER_CLEAR));
        assertTrue(detect(null, raining).contains(EnvSignal.RAINING), "首帧雨触发 (状态进入语义)");
    }

    @Test
    @DisplayName("THUNDER_START: thundering 假→真")
    void thunder_start() {
        var thundering = new EnvSnapshot.WorldInfo(true, true, true, 0, 12, "minecraft:overworld",
                EnvRules.tempCategory(0.5f), 0.5f, "RAIN", 1000, "DAY", "minecraft:plains", "");
        assertTrue(detect(base(), thundering).contains(EnvSignal.THUNDER_START));
        assertFalse(detect(null, thundering).contains(EnvSignal.THUNDER_START), "首帧不触发变化类");
    }

    // ── 温度 ──

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

    // ── 昼夜/黑暗/维度/时段 ──

    @Test
    @DisplayName("DAY_NIGHT_CHANGE 需 prev; 首帧不触发")
    void day_night_change() {
        var night = info(false, false, "NONE", 0.5f, 3, "minecraft:overworld", "NIGHT", "minecraft:plains", "");
        assertTrue(detect(base(), night).contains(EnvSignal.DAY_NIGHT_CHANGE));
        assertFalse(detect(null, night).contains(EnvSignal.DAY_NIGHT_CHANGE), "首帧不触发变化类");
    }

    @Test
    @DisplayName("DARKNESS: light < 7 onset")
    void darkness_edge() {
        var dark = info(true, false, "NONE", 0.5f, 3, "minecraft:overworld", "DAY", "minecraft:plains", "");
        assertTrue(detect(null, dark).contains(EnvSignal.DARKNESS), "首帧黑暗触发 (状态进入)");
        assertTrue(detect(base(), dark).contains(EnvSignal.DARKNESS));
        assertFalse(detect(dark, dark).contains(EnvSignal.DARKNESS));
    }

    @Test
    @DisplayName("DIMENSION_CHANGE / TIME_SEGMENT 需 prev")
    void dimension_and_time() {
        var nether = info(true, false, "NONE", 0.5f, 12, "minecraft:the_nether", "DAY", "minecraft:plains", "");
        var dusk = info(true, false, "NONE", 0.5f, 12, "minecraft:overworld", "DUSK", "minecraft:plains", "");
        assertTrue(detect(base(), nether).contains(EnvSignal.DIMENSION_CHANGE));
        assertTrue(detect(base(), dusk).contains(EnvSignal.TIME_SEGMENT));
        assertFalse(detect(null, nether).contains(EnvSignal.DIMENSION_CHANGE));
        assertFalse(detect(null, dusk).contains(EnvSignal.TIME_SEGMENT));
    }

    // ── v79.3 新信号 ──

    @Test
    @DisplayName("BIOME_CHANGE: biomeId 变化; 首帧不触发; 同 biome 不触发")
    void biome_change() {
        var desert = info(true, false, "NONE", 0.5f, 12, "minecraft:overworld", "DAY", "minecraft:desert", "");
        assertTrue(detect(base(), desert).contains(EnvSignal.BIOME_CHANGE));
        assertFalse(detect(null, desert).contains(EnvSignal.BIOME_CHANGE), "首帧不触发");
        assertFalse(detect(desert, desert).contains(EnvSignal.BIOME_CHANGE), "同 biome 不触发");
        // unknown 双方 unknown → equals 相等不触发
        var unknown1 = info(true, false, "NONE", 0.5f, 12, "minecraft:overworld", "DAY", "unknown", "");
        var unknown2 = info(true, false, "NONE", 0.5f, 12, "minecraft:overworld", "DAY", "unknown", "");
        assertFalse(detect(unknown1, unknown2).contains(EnvSignal.BIOME_CHANGE));
    }

    @Test
    @DisplayName("STRUCTURE_ENTER/LEAVE: 站立点结构空/非空边沿; 首帧不触发")
    void structure_enter_leave() {
        var inVillage = info(true, false, "NONE", 0.5f, 12, "minecraft:overworld", "DAY", "minecraft:plains", "minecraft:village");
        assertTrue(detect(base(), inVillage).contains(EnvSignal.STRUCTURE_ENTER), "进结构 → ENTER");
        assertFalse(detect(null, inVillage).contains(EnvSignal.STRUCTURE_ENTER), "首帧不触发");
        assertTrue(detect(inVillage, base()).contains(EnvSignal.STRUCTURE_LEAVE), "离结构 → LEAVE");
        assertFalse(detect(inVillage, inVillage).contains(EnvSignal.STRUCTURE_ENTER), "持续在结构不重复");
        assertFalse(detect(inVillage, inVillage).contains(EnvSignal.STRUCTURE_LEAVE));
    }

    // ── 实体 ──

    @Test
    @DisplayName("MONSTER_NEARBY/CLEAR: 实体在场边沿")
    void monster_edges() {
        var hasMonster = new EnvEdgeDetector.EntityPresence(true, false, false);
        var none = EnvEdgeDetector.EntityPresence.NONE;
        assertTrue(detectWithEnts(null, base(), none, hasMonster).contains(EnvSignal.MONSTER_NEARBY), "首帧有怪触发 (状态进入)");
        assertTrue(detectWithEnts(base(), base(), none, hasMonster).contains(EnvSignal.MONSTER_NEARBY));
        assertTrue(detectWithEnts(base(), base(), hasMonster, none).contains(EnvSignal.MONSTER_CLEAR));
        assertFalse(detectWithEnts(base(), base(), hasMonster, hasMonster).contains(EnvSignal.MONSTER_NEARBY));
    }

    @Test
    @DisplayName("FRIENDLY_NEARBY / MAID_NEARBY 仅出现边沿")
    void friendly_maid_edges() {
        var hasFriendly = new EnvEdgeDetector.EntityPresence(false, true, false);
        var hasMaid = new EnvEdgeDetector.EntityPresence(false, false, true);
        assertTrue(detectWithEnts(base(), base(), EnvEdgeDetector.EntityPresence.NONE, hasFriendly)
                .contains(EnvSignal.FRIENDLY_NEARBY));
        assertTrue(detectWithEnts(base(), base(), EnvEdgeDetector.EntityPresence.NONE, hasMaid)
                .contains(EnvSignal.MAID_NEARBY));
        assertFalse(detectWithEnts(base(), base(), hasFriendly, hasFriendly).contains(EnvSignal.FRIENDLY_NEARBY));
        assertFalse(detectWithEnts(base(), base(), hasFriendly, EnvEdgeDetector.EntityPresence.NONE)
                .contains(EnvSignal.FRIENDLY_NEARBY), "消失无 CLEAR (原语义)");
    }

    @Test
    @DisplayName("无变化 → 零信号; now==null → 零信号")
    void no_change_and_null() {
        assertTrue(detect(base(), base()).isEmpty(), "完全无变化零信号");
        assertTrue(EnvEdgeDetector.detect(base(), null, null, EnvEdgeDetector.EntityPresence.NONE, CFG).isEmpty());
    }
}
