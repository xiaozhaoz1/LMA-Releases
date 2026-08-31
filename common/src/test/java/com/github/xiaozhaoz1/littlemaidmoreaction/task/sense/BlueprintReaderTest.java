package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BlueprintReader} 纯 JVM 测试 (v79.49) — 蓝图统计纯函数 + 文件读取。
 */
class BlueprintReaderTest {

    @Test
    @DisplayName("describe: 相对坐标统计 (尺寸/用料/层分布)")
    void describe_stats() {
        List<BlueprintReader.BlueprintBlock> blocks = List.of(
                new BlueprintReader.BlueprintBlock(0, 0, 0, "minecraft:stone"),
                new BlueprintReader.BlueprintBlock(2, 0, 1, "minecraft:stone"),
                new BlueprintReader.BlueprintBlock(0, 1, 0, "minecraft:oak_planks"));
        var info = BlueprintReader.describe("test", blocks);
        assertEquals(3, info.sizeX(), "x 0..2 → 3");
        assertEquals(2, info.sizeY(), "y 0..1 → 2");
        assertEquals(2, info.sizeZ(), "z 0..1 → 2");
        assertEquals(2, info.materials().get("minecraft:stone"), "石头 2 块");
        assertEquals(1, info.materials().get("minecraft:oak_planks"));
        assertEquals(2, info.layers().size(), "2 层");
        assertTrue(info.layers().get(0).contains("2xminecraft:stone"), "层 0 统计");
    }

    @Test
    @DisplayName("describe: 空块列表 → 零尺寸空用料")
    void describe_empty() {
        var info = BlueprintReader.describe("empty", List.of());
        assertEquals(0, info.sizeX());
        assertEquals(0, info.sizeY());
        assertEquals(0, info.sizeZ());
        assertTrue(info.materials().isEmpty());
        assertTrue(info.layers().isEmpty());
    }

    @Test
    @DisplayName("describe: 负坐标归一 (min 偏移)")
    void describe_negative() {
        List<BlueprintReader.BlueprintBlock> blocks = List.of(
                new BlueprintReader.BlueprintBlock(-1, -2, 0, "minecraft:stone"),
                new BlueprintReader.BlueprintBlock(0, -2, 0, "minecraft:stone"));
        var info = BlueprintReader.describe("neg", blocks);
        assertEquals(2, info.sizeX());
        assertEquals(1, info.sizeY(), "-2..-2 → 1");
    }

    @Test
    @DisplayName("load: 临时文件有效 JSON → 统计")
    void load_valid(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("hut.json");
        Files.writeString(f, """
                {"name":"小屋","blocks":[
                  {"x":0,"y":0,"z":0,"block":"minecraft:stone"},
                  {"x":1,"y":0,"z":0,"block":"minecraft:stone"}]}""");
        var info = BlueprintReader.load(f);
        assertNotNull(info);
        assertEquals("小屋", info.name());
        assertEquals(2, info.materials().get("minecraft:stone"));
    }

    @Test
    @DisplayName("load: 非法 JSON → null 兜底")
    void load_invalid(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("bad.json");
        Files.writeString(f, "{not json");
        assertNull(BlueprintReader.load(f));
    }

    @Test
    @DisplayName("load: 文件缺失 → null")
    void load_missing(@TempDir Path dir) {
        assertNull(BlueprintReader.load(dir.resolve("nope.json")));
    }
}
