package com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FestivalPassiveTask#shouldAnnounce} 纯 JVM 测试 (v79.47 重设计, v79.61x 脱管线迁移) —
 * 当天首收去重语义。
 */
class FestivalPassiveTaskTest {

    @Test
    @DisplayName("同天二次信号 → 静默 (stored == today EpochDay)")
    void same_day_silent() {
        LocalDate today = LocalDate.of(2026, 9, 25);  // 2026 中秋 (农历 8/15, 库实测)
        assertFalse(FestivalPassiveTask.shouldAnnounce(today.toEpochDay(), today),
                "stored == today → 同天静默");
    }

    @Test
    @DisplayName("跨天再触发 (stored 昨天)")
    void next_day_trigger() {
        LocalDate today = LocalDate.of(2026, 9, 26);
        LocalDate yesterday = LocalDate.of(2026, 9, 25);
        assertTrue(FestivalPassiveTask.shouldAnnounce(yesterday.toEpochDay(), today),
                "昨天触发过 → 今天再触发");
    }

    @Test
    @DisplayName("跨年再触发 — EpochDay 不同 (2025 中秋 → 2026 中秋必须重新触发)")
    void cross_year_trigger() {
        // 坑: 禁存 month/day — 月日相同 (8/15) 会误判同天永久静默; EpochDay 含年份自然区分
        LocalDate lastYear = LocalDate.of(2025, 10, 6);   // 2025 中秋 (农历 8/15 公历日, 与 2026 不同)
        LocalDate thisYear = LocalDate.of(2026, 9, 25);   // 2026 中秋
        assertTrue(FestivalPassiveTask.shouldAnnounce(lastYear.toEpochDay(), thisYear),
                "去年触发 → 今年同日月必须重新触发 (EpochDay 不同)");
        assertNotEquals(lastYear.toEpochDay(), thisYear.toEpochDay(), "EpochDay 含年份必不同");
    }

    @Test
    @DisplayName("从未触发 (stored=0) → 触发")
    void never_triggered() {
        assertTrue(FestivalPassiveTask.shouldAnnounce(0L, LocalDate.of(2026, 2, 17)),
                "stored 0 (无键) → 首次触发");
    }
}
