package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

/**
 * 环境感知信号枚举 (v63) — 边沿触发的环境事件。
 *
 * <p>广播器每 200 tick 对比 prev/now 快照，生成命中信号。
 * 被动任务 Pipeline 在 {@code validate()} 中声明需要的信号，
 * 命中时广播器调用 {@code onSignal()}。
 */
public enum EnvSignal {

    // ── 天气 (SnowShovelPipeline) ──
    /** 开始下雪（降水类型=SNOW） */
    SNOWING,
    /** 开始下雨 */
    RAINING,
    /** 雷暴开始 */
    THUNDER_START,
    /** 天气转晴 */
    WEATHER_CLEAR,

    // ── 温度 (TempAdaptPipeline) ──
    /** 进入寒冷区域（温度 < cold_threshold 默认0.15） */
    TEMP_COLD,
    /** 进入炎热区域（温度 > hot_threshold 默认1.0） */
    TEMP_HOT,
    /** 返回常温 */
    TEMP_NORMAL,

    // ── 时间/光照 (LightControlPipeline) ──
    /** 昼夜切换（天亮/天黑边界） */
    DAY_NIGHT_CHANGE,
    /** 进入黑暗（光照 < darkness_threshold 默认7） */
    DARKNESS,

    // ── 维度/时段 ──
    /** 女仆切换维度 */
    DIMENSION_CHANGE,
    /** 时间段切换（DAY/DUSK/NIGHT/DAWN） */
    TIME_SEGMENT,

    // ── 实体 (MonsterLogPipeline) ──
    /** 附近出现怪物 */
    MONSTER_NEARBY,
    /** 附近怪物清除 */
    MONSTER_CLEAR,
    /** 附近有友好生物 */
    FRIENDLY_NEARBY,
    /** 附近有其他女仆 */
    MAID_NEARBY,

    // ── v79.3: 生物群系/站立点结构 (200t 零成本通道) ──
    /** 生物群系切换 (biomeId 变化) */
    BIOME_CHANGE,
    /** 进入站立点所在结构 (getAllStructuresAt 非空) */
    STRUCTURE_ENTER,
    /** 离开站立点所在结构 */
    STRUCTURE_LEAVE,

    // ── 结构 (低频边沿, 默认每MC天一次) ──
    /** 附近发现村庄 */
    VILLAGE_NEARBY,
    /** 附近发现废弃矿井 */
    MINESHAFT_NEARBY,
    /** 附近发现掠夺者前哨站 */
    OUTPOST_NEARBY
}
