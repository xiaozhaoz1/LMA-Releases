package com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FarmRegionStorage} / {@link FarmRegionLegacyImport} **纯函数**测试 (v79.64 区域改存女仆 NBT)。
 *
 * <p>边界 (项目铁律): 单测不得碰 MC 类型 ⇒ 这里只测与实体无关的部分 —
 * 几何/归一化/维度判定/NBT 编解码/老文件解析; **实体读写** (MaidData.cfgOrCreate + 女仆 NBT 往返)
 * 由 gametest 夹具覆盖 (lmaFarm*)。原 `storage/FarmRegionStorageTest` 的 uuid 版契约已随之退役。
 */
class FarmRegionStoragePureTest {

    // ── 几何 / 合法性 ──

    @Test
    @DisplayName("of 归一化端点 (反向选择修正) + contains 闭区间")
    void normalize_and_contains() {
        var r = FarmRegionStorage.of(10, 5, 8, 2, 1, 3, "minecraft:wheat");
        assertEquals(2, r.minX(), "min/max 归一化");
        assertEquals(10, r.maxX());
        assertEquals(1, r.minY());
        assertEquals(5, r.maxY());
        assertTrue(r.contains(2, 1, 3), "角点含");
        assertTrue(r.contains(10, 5, 8), "对角含");
        assertFalse(r.contains(11, 5, 8), "越界不含");
        assertTrue(r.isValid());
    }

    @Test
    @DisplayName("isValid: 收获方式 + 端点序 (cropId 可空 = 只收不种)")
    void valid_rules() {
        assertTrue(FarmRegionStorage.of(0, 0, 0, 1, 1, 1, "").isValid(), "空 cropId 合法 (只收)");
        assertTrue(FarmRegionStorage.of(0, 0, 0, 1, 1, 1, "minecraft:wheat", "right").isValid());
        var bad = new FarmRegionStorage.FarmRegion(0, 0, 0, 0, 0, 0, "x", "banana",
                null, null, null, null, null, null, "", "");
        assertFalse(bad.isValid(), "非法收获方式");
    }

    @Test
    @DisplayName("isActiveIn: 维度空=任意; 同维度=有效; 异维度=暂停 (用户裁定语义)")
    void dimension_activity() {
        var any = new FarmRegionStorage.FarmRegion(0, 0, 0, 1, 1, 1, "", "left",
                null, null, null, null, null, null, "", "");
        assertTrue(any.isActiveIn("minecraft:overworld"), "维度空 = 任意维度有效");

        var ow = new FarmRegionStorage.FarmRegion(0, 0, 0, 1, 1, 1, "", "left",
                null, null, null, null, null, null, "", "minecraft:overworld");
        assertTrue(ow.isActiveIn("minecraft:overworld"), "同维度有效");
        assertFalse(ow.isActiveIn("minecraft:the_nether"), "异维度 ⇒ 暂停");
    }

    @Test
    @DisplayName("displayName 兜底 + null 字段归一 (紧凑构造)")
    void display_name_and_nulls() {
        var r = new FarmRegionStorage.FarmRegion(0, 0, 0, 1, 1, 1, null, "left",
                null, null, null, null, null, null, null, null);
        assertEquals("区域", r.displayName(), "空名兜底「区域」");
        assertEquals("", r.cropId(), "cropId null → 空串");
        assertEquals("", r.dimension(), "dimension null → 空串");
        assertFalse(r.hasSeedBox());
        assertFalse(r.hasHarvestBox());
    }

    // ── NBT 编解码 (纯数据) ──

    @Test
    @DisplayName("encode/decode round-trip: 全字段 (含箱/维度/名) 保真")
    void nbt_roundtrip_full() {
        var r = new FarmRegionStorage.FarmRegion(-3157, 62, 2258, -3155, 63, 2260,
                "minecraft:wheat_seeds", "left", -3155, 63, 2258, -3160, 64, 2262, "麦田", "minecraft:overworld");
        var back = FarmRegionStorage.decode(FarmRegionStorage.encode(r));
        assertNotNull(back);
        assertEquals(r, back, "round-trip 必须逐字段相等");
    }

    @Test
    @DisplayName("encode/decode: 无箱不带箱字段 (decode 缺键 → null)")
    void nbt_roundtrip_no_boxes() {
        var r = FarmRegionStorage.of(0, 1, 2, 3, 4, 5, "minecraft:beetroot_seeds", "right");
        var back = FarmRegionStorage.decode(FarmRegionStorage.encode(r));
        assertNotNull(back);
        assertNull(back.seedX(), "无种子箱 → null (不是 NO_BOX 哨兵)");
        assertNull(back.harvestX(), "无收获箱 → null");
        assertEquals("right", back.harvestMode());
    }

    @Test
    @DisplayName("decode 容错: 空 tag → 不抛, 缺键 → 0/null/空 (不伪造 harvestMode)")
    void nbt_decode_degrades() {
        var back = FarmRegionStorage.decode(new net.minecraft.nbt.CompoundTag());
        assertNotNull(back, "空 tag 不抛 (坏条目由 getFor 侧按 isValid 过滤)");
        assertEquals(0, back.minX());
        assertEquals("", back.harvestMode(), "缺键 → 空串 (不伪造 left; of() 的 left 是构造默认值, 不在这条路径)");
        assertNull(back.seedX(), "缺箱键 → null");
        assertNull(back.harvestX());
        assertFalse(back.isValid(), "缺 harvestMode ⇒ 非法 ⇒ 由 getFor 侧过滤掉 (容错链: decode 不抛 → isValid 拦)");
    }

    // ── 老文件解析 (迁移) ──

    @Test
    @DisplayName("parseLegacy: 老格式 {uuid:[区域]} 解析 (缺维度 → 空串, 由导入时补当前维度)")
    void legacy_parse() {
        String json = """
                {
                  "6875f144-0000-0000-0000-000000000000": [
                    {"minX":-3157,"minY":62,"minZ":2258,"maxX":-3155,"maxY":63,"maxZ":2260,
                     "cropId":"minecraft:wheat_seeds","harvestMode":"left",
                     "seedX":-3155,"seedY":63,"seedZ":2258,"name":"麦田"}
                  ],
                  "14b91d8b-0000-0000-0000-000000000000": [
                    {"minX":-37,"minY":-61,"minZ":31,"maxX":-33,"maxY":-59,"maxZ":34,
                     "cropId":"minecraft:beetroot_seeds","harvestMode":"left"}
                  ]
                }""";
        Map<String, List<FarmRegionStorage.FarmRegion>> m = FarmRegionLegacyImport.parseLegacy(json);
        assertEquals(2, m.size(), "两个女仆条目");
        var a = m.get("6875f144-0000-0000-0000-000000000000");
        assertNotNull(a);
        assertEquals(1, a.size());
        assertEquals("麦田", a.get(0).name());
        assertEquals(-3155, a.get(0).seedX(), "箱字段解析");
        assertEquals("", a.get(0).dimension(), "老数据无维度 → 空串 (导入时补女仆当前维度)");
        assertEquals(1, m.get("14b91d8b-0000-0000-0000-000000000000").size());
    }

    @Test
    @DisplayName("parseLegacy 容错: 坏 JSON → 空表; 坏条目跳过; 非法区域丢弃")
    void legacy_parse_tolerant() {
        assertTrue(FarmRegionLegacyImport.parseLegacy("{not json").isEmpty(), "坏 JSON → 空");
        String partial = """
                {"u1":[{"minX":0,"minY":0,"minZ":0,"maxX":1,"maxY":1,"maxZ":1,
                        "cropId":"x","harvestMode":"left"},
                       {"bad":"entry"}],
                 "u2":[]}""";
        Map<String, List<FarmRegionStorage.FarmRegion>> m = FarmRegionLegacyImport.parseLegacy(partial);
        assertEquals(1, m.size(), "u1 保留 1 条合法, u2 空列表不建条目");
        assertEquals(1, m.get("u1").size());
    }
}
