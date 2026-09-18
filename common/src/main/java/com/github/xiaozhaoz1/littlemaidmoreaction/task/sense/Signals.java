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
 * <h3>信号 → 消费管线 → 配置面 (8 全量映射; v79.61x 死信号同族清理后; v79.62 snow_shovel 删)</h3>
 * <ul>
 *   <li>SNOWING/WEATHER_CLEAR → 天气检出保留 (LLM 对话/规则系统上下文, 用户裁定; 原 SnowShovelPipeline 消费已删)</li>
 *   <li>DARKNESS → TorchLightPipeline → ENV_DARKNESS_THRESHOLD</li>
 *   <li>MAID_NEARBY → HaqiPipeline → HAQI_*</li>
 *   <li>FESTIVAL_ENTER → FestivalPassiveTask (纯触发型) → showTrigger 100t (无阈值)</li>
 *   <li>结构动态信号 (STRUCTURE_PREFIX + id:discover/refresh/enter/leave) → StructureSensePassiveTask
 *       → ENV_STRUCTURE_* (独立于本 8 常量, 走 StructureSense.PREFIX 通配)</li>
 *   <li>v79.61x: RAINING/THUNDER_START/DIMENSION_CHANGE/TIME_SEGMENT/FRIENDLY_NEARBY/FRIENDLY_CLEAR/
 *       MAID_CLEAR/BIOME_CHANGE/STRUCTURE_ENTER/STRUCTURE_LEAVE + DARKNESS_CLEAR 共 11 个
 *       零消费死信号全删 (用户裁定同族清理)</li>
 * </ul>
 */
public final class Signals {

    /** 信号 id 前缀 — 环境信号 */
    public static final String ENV_PREFIX = "env:";

    // ── env: 常量 (8 个, 与 EnvSignal 枚举一一对应; v79.58 删 MONSTER 2, v79.61x 删死信号 11) ──

    /** 开始下雪 */
    public static final String ENV_SNOWING = "env:SNOWING";
    /** 天气转晴 */
    public static final String ENV_WEATHER_CLEAR = "env:WEATHER_CLEAR";
    /** 进入黑暗 */
    public static final String ENV_DARKNESS = "env:DARKNESS";
    /** 附近有其他女仆 */
    public static final String ENV_MAID_NEARBY = "env:MAID_NEARBY";
    /** 节日状态广播 (stateless — 每轮查表非空即发, 消费端当天首收去重) */
    public static final String ENV_FESTIVAL_ENTER = "env:FESTIVAL_ENTER";
    /** 稀有群系 (stateless — 每轮查当前 biome 稀有即发, 消费端每群系去重) */
    public static final String ENV_RARE_BIOME = "env:RARE_BIOME";

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
