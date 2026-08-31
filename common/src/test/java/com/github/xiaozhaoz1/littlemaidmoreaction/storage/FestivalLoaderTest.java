package com.github.xiaozhaoz1.littlemaidmoreaction.storage;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.FestivalTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FestivalLoader} 纯 JVM 测试 (v79.49) — 路径注入版 loadFromFile (同包 package-private)。
 */
class FestivalLoaderTest {

    @Test
    @DisplayName("有效表: 公历 + 农历条目解析注入 (lunar 缺省 false 兼容)")
    void load_valid(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("festival.json");
        Files.writeString(f, """
                {"festivals":[
                  {"id":"new_year","name":"新年","month":1,"day":1,"text":"新年快乐"},
                  {"id":"mid_autumn","name":"中秋节","month":8,"day":15,"lunar":true,"text":"赏月"}]}""");
        FestivalLoader.loadFromFile(f);
        assertEquals(2, FestivalTable.all().size(), "2 节日注入");
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
