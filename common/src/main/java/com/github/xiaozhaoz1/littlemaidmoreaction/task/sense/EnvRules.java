package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

/**
 * 环境感知纯逻辑规则 (v79.3) — 温度档/时间段分类, 零 MC 依赖可 JVM 测。
 *
 * <p>从 EnvScanner 搬移 (字段级逐字节一致), EnvScanner 改委托。
 */
public final class EnvRules {

    /** 温度档名 — COLD(<0.15) / OCEAN(<0.55) / MEDIUM(<0.95) / WARM */
    public static final String CAT_COLD = "COLD";
    public static final String CAT_OCEAN = "OCEAN";
    public static final String CAT_MEDIUM = "MEDIUM";
    public static final String CAT_WARM = "WARM";

    /** 时间段名 — DAY(0-11999) / DUSK(12000-13799) / NIGHT(13800-22199) / DAWN(22200-23999) */
    public static final String SEG_DAY = "DAY";
    public static final String SEG_DUSK = "DUSK";
    public static final String SEG_NIGHT = "NIGHT";
    public static final String SEG_DAWN = "DAWN";

    private EnvRules() {}

    /** TLM IMaid.getAtBiomeTemp 四档阈值 */
    public static String tempCategory(float baseTemp) {
        if (baseTemp < 0.15f) return CAT_COLD;
        if (baseTemp < 0.55f) return CAT_OCEAN;
        if (baseTemp < 0.95f) return CAT_MEDIUM;
        return CAT_WARM;
    }

    /** v37.2 时间段划分 */
    public static String timeSegment(long dayTime) {
        if (dayTime < 12000) return SEG_DAY;
        if (dayTime < 13800) return SEG_DUSK;
        if (dayTime < 22200) return SEG_NIGHT;
        return SEG_DAWN;
    }
}
