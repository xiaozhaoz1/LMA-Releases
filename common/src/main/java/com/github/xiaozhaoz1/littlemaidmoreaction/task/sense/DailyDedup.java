package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import java.time.LocalDate;

/**
 * 日历日去重纯函数 (v79.61x S3 — 从 {@code FestivalPipeline.shouldAnnounce} 上提共享)。
 *
 * <p>语义: 上次触发日 (EpochDay {@code long}, 完整日期含年份) 与今日不同 → 应触发。
 * 同天静默, 跨天/跨年 (EpochDay 不同) 自然再触发。
 *
 * <p><b>坑 (用户裁定)</b>: 去重键禁存 month/day — 明年同日月日相同 → 误判同天 → 永久静默。
 * EpochDay 含年份, 天然规避。
 */
public final class DailyDedup {

    /** 上次触发日 != 今天 → 应触发 ({@code 0} = 从未触发, 首次必触发) */
    public static boolean shouldAnnounce(long lastEpochDay, LocalDate today) {
        return lastEpochDay != today.toEpochDay();
    }

    private DailyDedup() {}
}
