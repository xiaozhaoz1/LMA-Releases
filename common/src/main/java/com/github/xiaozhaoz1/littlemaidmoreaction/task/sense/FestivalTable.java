package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import com.nlf.calendar.Lunar;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 节日表 (v79.47) — 纯数据 + 纯函数, 零 MC 依赖 (JVM 可测)。
 *
 * <p>广播器 (EnvSenseBroadcaster) 每轮用现实日期 {@code LocalDate.now()} 查表,
 * 非空 → FESTIVAL_ENTER 状态广播 (stateless); 消费端 per-maid 当天首收去重。
 * 数据由 festival.json 加载器注入 ({@link #setFestivals}); 默认空表 (无节日)。
 *
 * <p>v79.47 审查修复: 条目加 {@code lunar} 标志 — 农历条目 (春节/端午/七夕/中秋)
 * 用 cn.6tail:lunar 库把现实日期换算农历月日比对 (公历日期跨年漂移自动跟随);
 * 公历条目逻辑不变。
 */
public final class FestivalTable {

    /** 节日定义 — 纯数据 (festival.json: id/name/month/day/text + lunar 标志, 缺省 false) */
    public record Festival(String id, String name, int month, int day, String text, boolean lunar) {}

    private static volatile List<Festival> festivals = List.of();

    private FestivalTable() {}

    /** 数据注入 (festival.json 加载器调用; 默认空表) */
    public static void setFestivals(List<Festival> list) {
        festivals = list == null ? List.of() : List.copyOf(list);
    }

    /** 全部节日 (防御拷贝) */
    public static List<Festival> all() {
        return festivals;
    }

    /** 日期 → 节日 (无则 null) — 纯函数; 农历条目经 lunar 库换算 (农历月负数=闰月, 条目正数不匹配) */
    public static Festival lookup(LocalDate date) {
        for (Festival f : festivals) {
            if (f.lunar()) {
                Lunar l = Lunar.fromDate(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                if (f.month() == l.getMonth() && f.day() == l.getDay()) return f;
            } else {
                if (f.month() == date.getMonthValue() && f.day() == date.getDayOfMonth()) return f;
            }
        }
        return null;
    }
}
