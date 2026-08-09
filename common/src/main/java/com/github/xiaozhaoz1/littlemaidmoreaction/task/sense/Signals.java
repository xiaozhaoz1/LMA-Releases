package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import javax.annotation.Nullable;

/**
 * 信号 id 常量 (v72) — 信号泛化后被动任务/JSON 任务统一使用 String 信号 id。
 *
 * <p>两类前缀:
 * <ul>
 *   <li>{@code event:} — 事件信号 (规则引擎事件体系, Phase 3+ 事件桥接线)</li>
 *   <li>{@code env:} — 环境信号 (映射 {@link EnvSignal} 枚举, 与枚举名一一对应, 零映射表)</li>
 * </ul>
 */
public final class Signals {

    /** 信号 id 前缀 — 事件信号 */
    public static final String EVENT_PREFIX = "event:";
    /** 信号 id 前缀 — 环境信号 */
    public static final String ENV_PREFIX = "env:";

    // ── event: 常量 (与规则引擎事件体系同名 — Phase 3 事件桥按清单补全) ──

    /** 女仆攻击事件 */
    public static final String EVENT_MAID_ATTACK = "event:maid_attack";
    /** 女仆命中目标前事件 (可取消) */
    public static final String EVENT_MAID_HURT_TARGET = "event:maid_hurt_target_pre";
    /** 女仆右键交互事件 */
    public static final String EVENT_MAID_INTERACT = "event:maid_interact";
    /** 女仆 tick 事件 (低频轮询信号) */
    public static final String EVENT_MAID_TICK = "event:maid_tick";
    /** 女仆收割作物事件 */
    public static final String EVENT_MAID_HARVEST_CROP = "event:maid_harvest_crop";

    // ── env: 常量 (18 个, 与 EnvSignal 枚举一一对应) ──

    /** 开始下雪 */
    public static final String ENV_SNOWING = "env:SNOWING";
    /** 开始下雨 */
    public static final String ENV_RAINING = "env:RAINING";
    /** 雷暴开始 */
    public static final String ENV_THUNDER_START = "env:THUNDER_START";
    /** 天气转晴 */
    public static final String ENV_WEATHER_CLEAR = "env:WEATHER_CLEAR";

    /** 进入寒冷区域 */
    public static final String ENV_TEMP_COLD = "env:TEMP_COLD";
    /** 进入炎热区域 */
    public static final String ENV_TEMP_HOT = "env:TEMP_HOT";
    /** 返回常温 */
    public static final String ENV_TEMP_NORMAL = "env:TEMP_NORMAL";

    /** 昼夜切换 */
    public static final String ENV_DAY_NIGHT_CHANGE = "env:DAY_NIGHT_CHANGE";
    /** 进入黑暗 */
    public static final String ENV_DARKNESS = "env:DARKNESS";

    /** 女仆切换维度 */
    public static final String ENV_DIMENSION_CHANGE = "env:DIMENSION_CHANGE";
    /** 时间段切换 */
    public static final String ENV_TIME_SEGMENT = "env:TIME_SEGMENT";

    /** 附近出现怪物 */
    public static final String ENV_MONSTER_NEARBY = "env:MONSTER_NEARBY";
    /** 附近怪物清除 */
    public static final String ENV_MONSTER_CLEAR = "env:MONSTER_CLEAR";
    /** 附近有友好生物 */
    public static final String ENV_FRIENDLY_NEARBY = "env:FRIENDLY_NEARBY";
    /** 附近有其他女仆 */
    public static final String ENV_MAID_NEARBY = "env:MAID_NEARBY";

    /** 生物群系切换 (v79.3) */
    public static final String ENV_BIOME_CHANGE = "env:BIOME_CHANGE";
    /** 进入站立点所在结构 (v79.3) */
    public static final String ENV_STRUCTURE_ENTER = "env:STRUCTURE_ENTER";
    /** 离开站立点所在结构 (v79.3) */
    public static final String ENV_STRUCTURE_LEAVE = "env:STRUCTURE_LEAVE";

    /** 附近发现村庄 */
    public static final String ENV_VILLAGE_NEARBY = "env:VILLAGE_NEARBY";
    /** 附近发现废弃矿井 */
    public static final String ENV_MINESHAFT_NEARBY = "env:MINESHAFT_NEARBY";
    /** 附近发现掠夺者前哨站 */
    public static final String ENV_OUTPOST_NEARBY = "env:OUTPOST_NEARBY";

    /** EnvSignal → 信号 id */
    public static String envOf(EnvSignal signal) {
        return ENV_PREFIX + signal.name();
    }

    /** 信号 id → EnvSignal; 非 env: 前缀或未知值返回 null */
    @Nullable
    public static EnvSignal parseEnv(String signalId) {
        if (signalId == null || !signalId.startsWith(ENV_PREFIX)) return null;
        String name = signalId.substring(ENV_PREFIX.length());
        for (EnvSignal s : EnvSignal.values()) {
            if (s.name().equals(name)) return s;
        }
        return null;
    }

    private Signals() {}
}
