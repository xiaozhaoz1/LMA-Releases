package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import javax.annotation.Nullable;

/**
 * 信号 id 常量 (v72, v79.47 补齐 28 个与 EnvSignal 一一对应 — 2026-08-11c 修正: 实 28 非 29,
 *  v79.42 删 1 个事件常量后未同步计数) — 被动任务统一使用 String 信号 id。
 *
 * <p>v79.42: 事件信号体系删除 (event: 5 常量 0 引用 — v77.4 JSON 平台退役后事件桥无接线);
 * env: 常量收拢为在用的 7 个, 其余走 envOf(EnvSignal) 动态拼串。
 * v79.47: 补齐全部 28 个 env: 常量 (与 EnvSignal 枚举一一对应, 含 3 CLEAR/3 结构 LEAVE/节日 ENTER;
 * FESTIVAL_LEAVE 已删 — 节日改 stateless 状态广播 + per-maid 当天首收去重)。
 * v79.58: 删 MONSTER_NEARBY/CLEAR (monster_log 管线退役) — 实 26 个。
 */
public final class Signals {

    /** 信号 id 前缀 — 环境信号 */
    public static final String ENV_PREFIX = "env:";

    // ── env: 常量 (26 个, 与 EnvSignal 枚举一一对应; v79.58 删 MONSTER 2 个) ──

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
    /** 进入黑暗 */
    public static final String ENV_DARKNESS = "env:DARKNESS";
    /** 脱离黑暗 */
    public static final String ENV_DARKNESS_CLEAR = "env:DARKNESS_CLEAR";
    /** 女仆切换维度 */
    public static final String ENV_DIMENSION_CHANGE = "env:DIMENSION_CHANGE";
    /** 时间段切换 */
    public static final String ENV_TIME_SEGMENT = "env:TIME_SEGMENT";
    /** 附近有友好生物 */
    public static final String ENV_FRIENDLY_NEARBY = "env:FRIENDLY_NEARBY";
    /** 附近友好生物清除 */
    public static final String ENV_FRIENDLY_CLEAR = "env:FRIENDLY_CLEAR";
    /** 附近有其他女仆 */
    public static final String ENV_MAID_NEARBY = "env:MAID_NEARBY";
    /** 附近女仆离开 */
    public static final String ENV_MAID_CLEAR = "env:MAID_CLEAR";
    /** 生物群系切换 */
    public static final String ENV_BIOME_CHANGE = "env:BIOME_CHANGE";
    /** 进入站立点所在结构 */
    public static final String ENV_STRUCTURE_ENTER = "env:STRUCTURE_ENTER";
    /** 离开站立点所在结构 */
    public static final String ENV_STRUCTURE_LEAVE = "env:STRUCTURE_LEAVE";
    /** 附近发现村庄 */
    /** 村庄信号消失 */
    /** 附近发现废弃矿井 */
    /** 矿井信号消失 */
    /** 附近发现掠夺者前哨站 */
    /** 前哨站信号消失 */
    /** 节日状态广播 (stateless — 每轮查表非空即发, 消费端当天首收去重) */
    public static final String ENV_FESTIVAL_ENTER = "env:FESTIVAL_ENTER";

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
