package com.github.xiaozhaoz1.littlemaidmoreaction.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FarmRegionStorage} 纯 JVM 测试 (v79.62 作物区域存储) —
 * FarmRegion record 归一化/contains/isValid + 存储 CRUD + json round-trip (零 MC 依赖)。
 */
class FarmRegionStorageTest {

    @TempDir
    Path tmp;

    // ── FarmRegion 纯数据 ──

    @Test
    @DisplayName("of 归一化端点 (反向选择修正) + contains 闭区间")
    void region_normalize_contains() {
        // 反向选择 (大→小) 归一化
        var r = FarmRegionStorage.of(10, 20, 30, 5, 8, 1, "minecraft:wheat");
        assertEquals(5, r.minX());
        assertEquals(10, r.maxX());
        assertEquals(8, r.minY());
        assertEquals(20, r.maxY());
        assertEquals(1, r.minZ());
        assertEquals(30, r.maxZ());
        // 闭区间包含端点
        assertTrue(r.contains(5, 8, 1));
        assertTrue(r.contains(10, 20, 30));
        assertTrue(r.contains(7, 10, 15));
        assertFalse(r.contains(4, 1, 1));
        assertFalse(r.contains(11, 1, 1));
        assertFalse(r.contains(7, 21, 15));
        // of 默认无箱
        assertFalse(r.hasSeedBox());
        assertFalse(r.hasHarvestBox());
    }

    @Test
    @DisplayName("isValid: 收获方式 + 端点序 (v79.62 cropId 可空 = 只收不种)")
    void region_valid() {
        assertTrue(FarmRegionStorage.of(0, 0, 0, 1, 1, 1, "minecraft:wheat_seeds").isValid());
        // cropId 空/null = 只收不种区域 (合法; 绑定后 GUI 里补作物)
        assertTrue(FarmRegionStorage.of(0, 0, 0, 1, 1, 1, "").isValid());
        assertTrue(FarmRegionStorage.of(0, 0, 0, 1, 1, 1, null).isValid());
        // 收获方式非法 → 不合法
        var badMode = FarmRegionStorage.of(0, 0, 0, 1, 1, 1, "minecraft:wheat_seeds", "x");
        assertFalse(badMode.isValid());
        // 指定 right 收获方式合法
        var right = FarmRegionStorage.of(0, 0, 0, 1, 1, 1, "minecraft:wheat_seeds", "right");
        assertTrue(right.isValid());
        assertTrue(right.isRightHarvest());
        // raw 构造 (绕过 of 归一化) 才可能端点序错误
        var bad = new FarmRegionStorage.FarmRegion(2, 0, 0, 1, 1, 1, "minecraft:wheat_seeds", "left",
                null, null, null, null, null, null);
        assertFalse(bad.isValid()); // minX>maxX
    }

    @Test
    @DisplayName("of 带 per-region 箱: 负坐标 = 无箱; hasSeedBox/hasHarvestBox")
    void region_boxes() {
        var withBox = FarmRegionStorage.of(0, 0, 0, 3, 3, 3, "minecraft:wheat_seeds", "left",
                10, 20, 30, 40, 50, 60);
        assertTrue(withBox.hasSeedBox());
        assertTrue(withBox.hasHarvestBox());
        assertEquals(10, withBox.seedX());
        assertEquals(60, withBox.harvestZ());
        var onlySeed = FarmRegionStorage.of(0, 0, 0, 1, 1, 1, "", "left",
                10, 20, 30, FarmRegionStorage.NO_BOX, FarmRegionStorage.NO_BOX, FarmRegionStorage.NO_BOX);
        assertTrue(onlySeed.hasSeedBox());
        assertFalse(onlySeed.hasHarvestBox());
    }

    // ── 内存 CRUD ──

    @Test
    @DisplayName("put/add/remove/get + updateRegion")
    void storage_crud() {
        String uid = "aa-bb-cc";
        FarmRegionStorage.reset();
        assertTrue(FarmRegionStorage.getFor(uid).isEmpty());

        FarmRegionStorage.addRegion(uid, FarmRegionStorage.of(0, 0, 0, 3, 2, 3, "minecraft:wheat"));
        FarmRegionStorage.addRegion(uid, FarmRegionStorage.of(5, 0, 5, 8, 2, 8, "minecraft:sugar_cane"));
        assertEquals(2, FarmRegionStorage.getFor(uid).size());

        // cropId 空 = 只收不种区域 — 也入表
        FarmRegionStorage.addRegion(uid, FarmRegionStorage.of(9, 0, 9, 10, 1, 10, ""));
        assertEquals(3, FarmRegionStorage.getFor(uid).size());

        // remove 越界失败
        assertFalse(FarmRegionStorage.removeRegion(uid, 5));
        assertTrue(FarmRegionStorage.removeRegion(uid, 0));
        assertEquals(2, FarmRegionStorage.getFor(uid).size());
        assertEquals("minecraft:sugar_cane", FarmRegionStorage.getFor(uid).get(0).cropId());

        // updateRegion: 改 cropId/收获方式, 保留端点与箱
        FarmRegionStorage.updateRegion(uid, 1, "minecraft:potato", "right");
        var upd = FarmRegionStorage.getFor(uid).get(1);
        assertEquals("minecraft:potato", upd.cropId());
        assertTrue(upd.isRightHarvest());
        // 越界/非法不更新
        assertFalse(FarmRegionStorage.updateRegion(uid, 99, "x", "left"));
        assertFalse(FarmRegionStorage.updateRegion(uid, 0, "x", "badmode"));
    }

    @Test
    @DisplayName("put 覆盖 (cropId 空也保留 — 只收不种)")
    void storage_put() {
        String uid = "xx";
        FarmRegionStorage.reset();
        FarmRegionStorage.putRegions(uid, new java.util.ArrayList<>(java.util.List.of(
                FarmRegionStorage.of(0, 0, 0, 1, 1, 1, "minecraft:wheat"),
                FarmRegionStorage.of(0, 0, 0, 1, 1, 1, "")
        )));
        assertEquals(2, FarmRegionStorage.getFor(uid).size());
    }

    // ── json round-trip ──

    @Test
    @DisplayName("save→load round-trip: 按 uuid 分区保存恢复 (含 per-region 箱), 坏条目跳过")
    void storage_roundtrip() {
        FarmRegionStorage.reset();
        FarmRegionStorage.addRegion("u1", FarmRegionStorage.of(0, 0, 0, 3, 3, 3, "minecraft:wheat"));
        FarmRegionStorage.addRegion("u1", FarmRegionStorage.of(4, 0, 4, 7, 3, 7, "minecraft:potato", "right",
                10, 20, 30, 40, 50, 60));
        FarmRegionStorage.addRegion("u2", FarmRegionStorage.of(0, 0, 0, 5, 2, 5, "minecraft:nether_wart"));

        FarmRegionStorage.save(tmp);
        FarmRegionStorage.reset();
        FarmRegionStorage.load(tmp);

        assertEquals(2, FarmRegionStorage.getFor("u1").size());
        assertEquals("minecraft:potato", FarmRegionStorage.getFor("u1").get(1).cropId());
        // per-region 箱 round-trip
        assertTrue(FarmRegionStorage.getFor("u1").get(1).hasSeedBox());
        assertEquals(10, FarmRegionStorage.getFor("u1").get(1).seedX());
        assertTrue(FarmRegionStorage.getFor("u1").get(1).hasHarvestBox());
        assertEquals(60, FarmRegionStorage.getFor("u1").get(1).harvestZ());
        assertEquals(1, FarmRegionStorage.getFor("u2").size());
        assertEquals("minecraft:nether_wart", FarmRegionStorage.getFor("u2").get(0).cropId());
    }

    @Test
    @DisplayName("坏 JSON / 缺失文件 → 空表不炸")
    void storage_tolerant() throws java.io.IOException {
        FarmRegionStorage.reset();
        FarmRegionStorage.load(tmp); // 无文件 → 空
        assertTrue(FarmRegionStorage.getFor("u1").isEmpty());
        // filePath 归一化后直接落 configDir/farm_regions.json (v79.62 修双嵌套)
        java.nio.file.Path file = tmp.resolve("farm_regions.json");
        java.nio.file.Files.writeString(file, "garbage not json");
        FarmRegionStorage.load(tmp); // 坏 JSON → 空, 不抛
        assertTrue(FarmRegionStorage.getFor("u1").isEmpty());
    }
}
