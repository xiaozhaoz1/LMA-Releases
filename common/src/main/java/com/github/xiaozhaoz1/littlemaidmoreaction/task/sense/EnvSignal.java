package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

/**
 * 环境感知信号枚举 (v63, v79.47 补 8; v79.58 删 MONSTER 2; v79.61x 死信号同族清理
 * — 删 DARKNESS_CLEAR + RAINING/THUNDER_START/DIMENSION_CHANGE/TIME_SEGMENT/BIOME_CHANGE/
 * STRUCTURE_ENTER/LEAVE/FRIENDLY_NEARBY/FRIENDLY_CLEAR/MAID_CLEAR 共 11 个零消费信号,
 * v79.62.5: 删温度 3 (TEMP_COLD/HOT/NORMAL — 温度边沿检测删, TLM 覆盖) — 剩 6 个有消费方) — 边沿触发的环境事件。
 *
 * <p>广播器每 200 tick 对比 prev/now 快照，生成命中信号。
 * 被动任务 Pipeline 在 {@code validate()} 中声明需要的信号，
 * 命中时广播器调用 {@code onSignal()}。
 */
public enum EnvSignal {

    // ── 天气 (v79.62: snow_shovel 删, 检出保留给 LLM 对话) ──
    /** 开始下雪（降水类型=SNOW） */
    SNOWING,
    /** 天气转晴 (雨→晴边沿) */
    WEATHER_CLEAR,

    /** 进入黑暗（光照 < darkness_threshold 默认7） — TorchLightPipeline */
    DARKNESS,

    // ── 实体 (HaqiPipeline 用 MAID_NEARBY) ──
    /** 附近有其他女仆 */
    MAID_NEARBY,

    // ── 节日 (日历驱动, 现实日期口径, v79.47, stateless 广播) ──
    /** 节日状态广播 (stateless: 广播器每轮查表非空即发; 消费端 per-maid 当天首收去重) */
    FESTIVAL_ENTER,

    // ── 稀有群系 (v79.62.1, stateless 广播 — 当前在稀有群系即发; 消费端每群系去重) ──
    /** 进入稀有群系 (蘑菇岛/深暗之域/溶洞/繁茂洞穴等) */
    RARE_BIOME
}
