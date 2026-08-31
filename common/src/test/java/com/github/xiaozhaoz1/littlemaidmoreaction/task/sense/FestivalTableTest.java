package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FestivalTable} 纯 JVM 测试 (v79.47) — 节日表纯函数 + 农历条目 (cn.6tail:lunar 实测映射)。
 */
class FestivalTableTest {

    private static final FestivalTable.Festival NEW_YEAR =
            new FestivalTable.Festival("new_year", "新年", 1, 1, "新年快乐", false, java.util.List.of());
    private static final FestivalTable.Festival SPRING =
            new FestivalTable.Festival("spring_festival", "春节", 1, 1, "春节快乐", true, java.util.List.of());
    private static final FestivalTable.Festival DRAGON_BOAT =
            new FestivalTable.Festival("dragon_boat", "端午节", 5, 5, "粽子好吃", true, java.util.List.of());
    private static final FestivalTable.Festival MID_AUTUMN =
            new FestivalTable.Festival("mid_autumn", "中秋节", 8, 15, "赏月", true, java.util.List.of());

    @Test
    @DisplayName("lookup 公历: 命中/未命中; 年份不影响 (现实日期口径 month/day)")
    void lookup_solar() {
        FestivalTable.setFestivals(List.of(NEW_YEAR));
        assertEquals("新年", FestivalTable.lookup(LocalDate.of(2026, 1, 1)).name());
        assertNull(FestivalTable.lookup(LocalDate.of(2026, 1, 2)), "非节日日返回 null");
        assertEquals("新年", FestivalTable.lookup(LocalDate.of(2025, 1, 1)).name(),
                "年份不影响 — 只比 month/day (2025 元旦同命中)");
    }

    @Test
    @DisplayName("lookup 农历: 库实测映射 (2026 春节 2/17 端午 6/19 中秋 9/25); 公历日期跨年漂移自动跟随")
    void lookup_lunar() {
        FestivalTable.setFestivals(List.of(SPRING, DRAGON_BOAT, MID_AUTUMN));
        // 映射值经 cn.6tail:lunar 实测 (javac probe): 2026-02-17→1/1, 2026-06-19→5/5, 2026-09-25→8/15
        assertEquals("春节", FestivalTable.lookup(LocalDate.of(2026, 2, 17)).name());
        assertEquals("端午节", FestivalTable.lookup(LocalDate.of(2026, 6, 19)).name());
        assertEquals("中秋节", FestivalTable.lookup(LocalDate.of(2026, 9, 25)).name());
        assertNull(FestivalTable.lookup(LocalDate.of(2026, 2, 18)), "春节次日非节日");
        assertNull(FestivalTable.lookup(LocalDate.of(2026, 3, 1)), "非节日日 null");
    }

    @Test
    @DisplayName("setFestivals: null/空表防御; 注入后全量可查")
    void set_empty() {
        FestivalTable.setFestivals(null);
        assertTrue(FestivalTable.all().isEmpty(), "null → 空表");
        assertNull(FestivalTable.lookup(LocalDate.of(2026, 1, 1)));

        FestivalTable.setFestivals(List.of());
        assertTrue(FestivalTable.all().isEmpty(), "空列表 → 空表");

        FestivalTable.setFestivals(List.of(NEW_YEAR, SPRING));
        assertEquals(2, FestivalTable.all().size());
        assertNotNull(FestivalTable.lookup(LocalDate.of(2026, 1, 1)));
        assertNotNull(FestivalTable.lookup(LocalDate.of(2026, 2, 17)));
    }
}
