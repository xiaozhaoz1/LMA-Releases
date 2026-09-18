package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

/**
 * 环境感知纯逻辑规则 (v79.3) — 时间段分类 (v79.62.5: 温度档已删 — TLM getAtBiomeTemp), 零 MC 依赖可 JVM 测。
 *
 * <p>从 EnvScanner 搬移 (字段级逐字节一致), EnvScanner 改委托。
 */
public final class EnvRules {

    /** 时间段名 — DAY(0-11999) / DUSK(12000-13799) / NIGHT(13800-22199) / DAWN(22200-23999) */
    public static final String SEG_DAY = "DAY";
    public static final String SEG_DUSK = "DUSK";
    public static final String SEG_NIGHT = "NIGHT";
    public static final String SEG_DAWN = "DAWN";

    private EnvRules() {}

    // v79.62.5: tempCategory/CAT_* 删 — 温度档经 TLM IMaid.getAtBiomeTemp (用户裁定不复刻)

    /** 时间段划分 */
    public static String timeSegment(long dayTime) {
        if (dayTime < 12000) return SEG_DAY;
        if (dayTime < 13800) return SEG_DUSK;
        if (dayTime < 22200) return SEG_NIGHT;
        return SEG_DAWN;
    }
}
