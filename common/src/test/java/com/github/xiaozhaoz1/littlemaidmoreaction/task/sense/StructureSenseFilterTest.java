package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StructBox;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StructureSense 纯逻辑单测 (v79.61 状态机重设计) — 白名单过滤/最近开关/文案。
 * 纯 JVM: 只测参数注入的 static 纯函数 (filterPure/matchList/textNearby),
 * 不触 PassiveTaskConfig (MC config 类, 错题 #174 铁律)。
 * BlockPos 与既有测试同模式 (构造器无 MC 环境依赖)。
 */
class StructureSenseFilterTest {

    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);
    private static final BlockPos FAR_NORTH = new BlockPos(0, 0, -100);

    private static StructBox pointBox(int x, int y, int z) {
        return new StructBox(x, y, z, x, y, z);
    }

    private static Map<String, StructBox> sample() {
        Map<String, StructBox> m = new LinkedHashMap<>();
        m.put("minecraft:village_plains", pointBox(30, 0, 0));
        m.put("minecraft:stronghold", pointBox(5, 0, 0));
        m.put("minecraft:fortress", pointBox(50, 0, 50));
        return m;
    }

    // ── filterPure: 白名单 ──

    @Test
    void 白名单空_全部保留() {
        Map<String, StructBox> out = StructureSense.filterPure(sample(), ORIGIN, List.of(), false);
        assertEquals(3, out.size());
    }

    @Test
    void 白名单精确_只保留命中() {
        Map<String, StructBox> out = StructureSense.filterPure(sample(), ORIGIN,
                List.of("minecraft:stronghold"), false);
        assertEquals(1, out.size());
        assertTrue(out.containsKey("minecraft:stronghold"));
    }

    @Test
    void 白名单通配_前缀命中() {
        Map<String, StructBox> out = StructureSense.filterPure(sample(), ORIGIN,
                List.of("minecraft:village_*"), false);
        assertEquals(1, out.size());
        assertTrue(out.containsKey("minecraft:village_plains"));
    }

    @Test
    void 白名单未命中_全部剔除() {
        Map<String, StructBox> out = StructureSense.filterPure(sample(), ORIGIN,
                List.of("minecraft:ancient_city"), false);
        assertTrue(out.isEmpty());
    }

    // ── filterPure: 最近开关 ──

    @Test
    void 最近开关_只取最近一个() {
        Map<String, StructBox> out = StructureSense.filterPure(sample(), ORIGIN, List.of(), true);
        assertEquals(1, out.size());
        // stronghold (5,0,0) 最近
        assertTrue(out.containsKey("minecraft:stronghold"));
    }

    @Test
    void 最近开关_白名单内取最近() {
        // 白名单只剩 village+fortress → 最近是 village (30) vs fortress (70.7)
        Map<String, StructBox> out = StructureSense.filterPure(sample(), ORIGIN,
                List.of("minecraft:village_*", "minecraft:fortress"), true);
        assertEquals(1, out.size());
        assertTrue(out.containsKey("minecraft:village_plains"));
    }

    @Test
    void 最近开关_空集不炸() {
        Map<String, StructBox> out = StructureSense.filterPure(sample(), ORIGIN,
                List.of("minecraft:ancient_city"), true);
        assertTrue(out.isEmpty());
    }

    // ── StructBox: 边界距离 (v79.6x BB 语义) ──

    @Test
    void 边界盒_盒内距离零() {
        var box = new StructBox(-10, 0, -10, 10, 5, 10);
        assertEquals(0.0, box.distSqrFrom(0, 3, 0));
        assertEquals(0.0, box.distSqrFrom(-10, 0, -10));   // 盒面
        assertEquals(0.0, box.distSqrFrom(10, 5, 10));     // 对角
    }

    @Test
    void 边界盒_盒外按最近面距离() {
        var box = new StructBox(-10, 0, -10, 10, 5, 10);
        assertEquals(1.0, box.distSqrFrom(11, 3, 0));      // 东侧 1 格
        assertEquals(4.0, box.distSqrFrom(0, 7, 0));       // 上方 2 格
        assertEquals(4.0, box.distSqrFrom(-12, 0, 10));    // 西侧 2 格 (z 在盒面)
    }

    @Test
    void 边界盒_中心锚点() {
        var box = new StructBox(-10, 0, -10, 10, 5, 10);
        assertEquals(0.5, box.centerX());   // -10 + 21/2
        assertEquals(3.0, box.centerY());   // 0 + 6/2
        assertEquals(0.5, box.centerZ());
    }

        // ── matchList ──

    @Test
    void 匹配_精确() {
        assertTrue(StructureSense.matchList("minecraft:fortress", List.of("minecraft:fortress")));
        assertFalse(StructureSense.matchList("minecraft:fortress", List.of("minecraft:village_plains")));
    }

    @Test
    void 匹配_通配() {
        assertTrue(StructureSense.matchList("minecraft:village_desert", List.of("minecraft:village_*")));
        assertFalse(StructureSense.matchList("minecraft:stronghold", List.of("minecraft:village_*")));
    }

    @Test
    void 匹配_空列表_恒false() {
        assertFalse(StructureSense.matchList("minecraft:fortress", List.of()));
    }

    // ── structureKeyOf: 结构 registry id → lang 键 (v79.6x; 变体精确键优先 + 前缀 fallback) ──

    @Test
    void 结构键_村庄变体精确区分() {
        assertEquals("structure_sense.structure.village_plains",
                StructureSense.structureKeyOf("minecraft:village_plains"));
        assertEquals("structure_sense.structure.village_desert",
                StructureSense.structureKeyOf("minecraft:village_desert"));
        assertEquals("structure_sense.structure.village_taiga",
                StructureSense.structureKeyOf("minecraft:village_taiga"));
    }

    @Test
    void 结构键_其他结构与未知fallback() {
        assertEquals("structure_sense.structure.mineshaft",
                StructureSense.structureKeyOf("minecraft:mineshaft_abandoned"));  // 未知变体 → 前缀归组
        assertEquals("structure_sense.structure.trail_ruins",
                StructureSense.structureKeyOf("minecraft:trail_ruins"));
        assertNull(StructureSense.structureKeyOf("minecraft:unknown_structure"));
    }
}
