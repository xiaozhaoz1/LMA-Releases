package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import javax.annotation.Nullable;

/**
 * 信号 id 常量 (v72) — 被动任务统一使用 String 信号 id。
 *
 * <p>v79.42: 事件信号体系删除 (event: 5 常量 0 引用 — v77.4 JSON 平台退役后事件桥无接线);
 * env: 常量收拢为在用的 7 个, 其余走 envOf(EnvSignal) 动态拼串。
 * v79.47: 补齐 env: 常量 (含 3 CLEAR/3 结构 LEAVE/节日 ENTER);
 * FESTIVAL_LEAVE 已删 — 节日改 stateless 状态广播 + per-maid 当天首收去重。
 * v79.58: 删 MONSTER_NEARBY/CLEAR (monster_log 管线退役)。
 * v79.61x S4: 常量/枚举实 19 个 (与 {@link EnvSignal} 一一对应 — 历史注释 28/26 计数漂移修正)。
 *
 * <h3>信号 → 消费管线 → 配置面 (19 全量映射)</h3>
 * <ul>
 *   <li>SNOWING → SnowShovelPipeline → ENV_DEFAULT_RADIUS</li>
 *   <li>TEMP_COLD/HOT/NORMAL → TempAdaptPipeline → ENV_COLD/HOT_THRESHOLD</li>
 *   <li>DARKNESS → TorchLightPipeline → ENV_DARKNESS_THRESHOLD</li>
 *   <li>MAID_NEARBY → HaqiPipeline → HAQI_*</li>
 *   <li>FESTIVAL_ENTER → FestivalPipeline → showTrigger 100t (无阈值)</li>
 *   <li>结构动态信号 (STRUCTURE_PREFIX + id:discover/refresh/enter/leave) → StructureSensePipeline
 *       → ENV_STRUCTURE_* (独立于本 19 常量, 走 StructureSense.PREFIX 通配)</li>
 *   <li>RAINING/THUNDER_START/WEATHER_CLEAR/DARKNESS_CLEAR/DIMENSION_CHANGE/TIME_SEGMENT/
 *       FRIENDLY_NEARBY/FRIENDLY_CLEAR/MAID_CLEAR/BIOME_CHANGE/STRUCTURE_ENTER/STRUCTURE_LEAVE
 *       → 侦测端生成, 当前无管线消费 (预留)</li>
 * </ul>
 */
public final class Signals {

    /** 信号 id 前缀 — 环境信号 */
    public static final String ENV_PREFIX = "env:";

    // ── env: 常量 (19 个, 与 EnvSignal 枚举一一对应; v79.58 删 MONSTER 2 个) ──

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
