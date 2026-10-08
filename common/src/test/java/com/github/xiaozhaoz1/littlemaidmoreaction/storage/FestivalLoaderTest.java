package com.github.xiaozhaoz1.littlemaidmoreaction.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FestivalLoader} 纯 JVM 测试。
 *
 * <p>契约 (v79.64 用户裁定): 节日表 = **jar 预设唯源** ⇒ 断言"打包里的预设真能被解析且字段齐全"
 * (旧设计曾有"config 副本 + 缺字段自愈", 已随 config 通道整体删除 — 见 lessons #341)。
 */
class FestivalLoaderTest {

    @Test
    @DisplayName("jar 预设真能被解析: 非空 + id/name 齐全 + foods 池非空 (回归 #335/#337 病根)")
    void jarPreset_parsesWithFoods() {
        List<FestivalTable.Festival> list = FestivalLoader.loadFromJar();
        assertFalse(list.isEmpty(), "jar 预设必须能解析出节日 (打包缺资源/路径错会在此红)");
        for (FestivalTable.Festival f : list) {
            assertNotNull(f.id(), "id 必填: " + f);
            assertNotNull(f.name(), "name 必填: " + f);
            assertNotNull(f.foods(), "foods 不应为 null: " + f.id());
            assertFalse(f.foods().isEmpty(),
                    "预设条目必须有礼物池 foods (史上就是缺它导致节日不送礼): " + f.id());
        }
    }

    @Test
    @DisplayName("生产入口 load(): 从 jar 注入 FestivalTable (无 config 副本通道)")
    void load_readsJarPreset() {
        FestivalTable.setFestivals(List.of());   // 清场, 确保断言的是 load() 的结果
        FestivalLoader.load();
        List<FestivalTable.Festival> all = FestivalTable.all();
        assertFalse(all.isEmpty(), "load() 必须从 jar 预设注入非空节日表");
        assertTrue(all.stream().allMatch(f -> f.foods() != null && !f.foods().isEmpty()),
                "load() 注入的每条都带 foods (jar 预设口径)");
    }

    @Test
    @DisplayName("有效表: 公历 + 农历条目解析注入 (lunar 缺省 false 兼容)")
    void load_valid(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("festival.json");
        Files.writeString(f, """
                {"festivals":[
                  {"id":"new_year","name":"新年","month":1,"day":1,"text":"新年快乐"},
                  {"id":"mid_autumn","name":"中秋节","month":8,"day":15,"lunar":true,"text":"赏月"}]}""");
        List<FestivalTable.Festival> list = FestivalLoader.loadFromFile(f);
        assertEquals(2, list.size(), "2 节日解析");
        assertEquals(2, FestivalTable.all().size(), "并注入 FestivalTable");
        assertNotNull(FestivalTable.lookup(LocalDate.of(2026, 1, 1)), "公历命中");
        assertNotNull(FestivalTable.lookup(LocalDate.of(2026, 9, 25)), "农历 8/15 实测映射命中");
    }

    @Test
    @DisplayName("非法 JSON → 空表兜底 (不炸)")
    void load_invalid(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("bad.json");
        Files.writeString(f, "{not json");
        FestivalLoader.loadFromFile(f);
        assertTrue(FestivalTable.all().isEmpty(), "解析失败 → 空表");
    }

    @Test
    @DisplayName("缺失文件 → 空表 (FileNotFound 兜底)")
    void load_missing(@TempDir Path dir) {
        FestivalLoader.loadFromFile(dir.resolve("nope.json"));
        assertTrue(FestivalTable.all().isEmpty());
    }

    @Test
    @DisplayName("条目缺 foods → 照常解析 (缺字段容忍; id/name 保留)")
    void load_entryWithoutFoods(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("legacy.json");
        Files.writeString(f, """
                {"festivals":[
                  {"id":"spring_festival","name":"春节","month":1,"day":1,"lunar":true,"text":"新年快乐"}]}""");
        List<FestivalTable.Festival> list = FestivalLoader.loadFromFile(f);
        assertEquals(1, list.size(), "缺 foods 不能丢条目");
        var fest = list.get(0);
        assertTrue(fest.foods() == null || fest.foods().isEmpty(), "缺 foods ⇒ 空池");
        assertNotNull(fest.id());
        assertNotNull(fest.name());
    }

    @Test
    @DisplayName("无 festivals 字段 → 空表")
    void load_no_field(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("empty.json");
        Files.writeString(f, "{\"other\":1}");
        FestivalLoader.loadFromFile(f);
        assertTrue(FestivalTable.all().isEmpty());
    }

    @Test
    @DisplayName("条目缺 text → 空文案不炸; all() 防御拷贝")
    void load_no_text(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("t.json");
        Files.writeString(f, """
                {"festivals":[{"id":"x","name":"X","month":1,"day":1}]}""");
        FestivalLoader.loadFromFile(f);
        assertEquals("", FestivalTable.all().get(0).text(), "缺 text → 空串");
        List<FestivalTable.Festival> copy = FestivalTable.all();
        assertThrows(UnsupportedOperationException.class, copy::clear,
                "List.copyOf 不可变 = 防御 (clear 抛异常, 内部不受影响)");
        assertEquals(1, FestivalTable.all().size());
    }
}
