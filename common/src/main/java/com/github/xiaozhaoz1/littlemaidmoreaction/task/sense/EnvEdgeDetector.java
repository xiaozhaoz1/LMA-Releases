package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;

/**
 * 环境边沿检测纯逻辑核心 (v79.3) — 从 EnvSenseBroadcaster.detectSignals 剥离。
 *
 * <p>零 MC 依赖 (WorldInfo 纯 record + EntityPresence 纯)。
 *
 * <p>v79.61x (用户裁定): 死信号同族清理 — RAINING/THUNDER_START/DIMENSION_CHANGE/TIME_SEGMENT/
 * BIOME_CHANGE/STRUCTURE_ENTER/LEAVE/FRIENDLY_NEARBY/FRIENDLY_CLEAR/MAID_CLEAR 10 个「检测生成
 * 但零消费」预留信号全删 (DARKNESS_CLEAR 已于前批删) — 每广播轮白算一次边沿, 无订阅方。
 * 保留检出 = 唯一有消费方的 7 种: SNOWING / WEATHER_CLEAR / TEMP_COLD / TEMP_HOT / TEMP_NORMAL
 * / DARKNESS / MAID_NEARBY (FESTIVAL_ENTER 为 stateless 广播, 不经此检) 。
 *
 * <p>语义约定 (与原实现一致): prev==null (首帧) 时"状态进入"类信号 (SNOWING/
 * TEMP_COLD/DARKNESS/MAID_NEARBY 等) 可触发, "回归"类 (WEATHER_CLEAR/TEMP_NORMAL) 需 prev 存在。
 */
public final class EnvEdgeDetector {

    /** 实体在场状态 — 由快照实体分类派生 (纯, 供检测核心; 仅 maid 消费)
     * v79.61x: friendly 字段保留 (快照分类仍采集), MAID_NEARBY 独用 */
    public record EntityPresence(boolean friendly, boolean maid) {
        public static final EntityPresence NONE = new EntityPresence(false, false);
    }

    /** 阈值配置 — 由 PassiveTaskConfig 注入 */
    public record EnvConfig(float coldThreshold, float hotThreshold, int darknessThreshold) {}

    private EnvEdgeDetector() {}

    /**
     * 边沿检测 — 对比 prev/now 世界信息与实体在场, 返回本轮命中信号。
     *
     * @param prev   上次世界信息 (null = 首帧)
     * @param now    当前世界信息 (null → 空信号)
     */
    public static Set<EnvSignal> detect(@Nullable EnvSnapshot.WorldInfo prev, EnvSnapshot.WorldInfo now,
                                        @Nullable EntityPresence prevEnt, EntityPresence nowEnt,
                                        EnvConfig cfg) {
        Set<EnvSignal> signals = EnumSet.noneOf(EnvSignal.class);
        if (now == null) return signals;

        // ── 天气 (v79.62: snow_shovel 已删, 检出保留给 LLM 对话/规则系统上下文 — 用户裁定) ──
        boolean wasSnowing = prev != null && prev.raining() && "SNOW".equals(prev.precipitation());
        boolean isSnowing = now.raining() && "SNOW".equals(now.precipitation());
        if (isSnowing && !wasSnowing) signals.add(EnvSignal.SNOWING);
        if (prev != null && prev.raining() && !now.raining()) signals.add(EnvSignal.WEATHER_CLEAR);

        // ── 温度 (TempAdaptPipeline 消费) ──
        boolean wasCold = prev != null && prev.temperature() < cfg.coldThreshold();
        boolean isCold = now.temperature() < cfg.coldThreshold();
        boolean wasHot = prev != null && prev.temperature() > cfg.hotThreshold();
        boolean isHot = now.temperature() > cfg.hotThreshold();
        if (isCold && !wasCold) signals.add(EnvSignal.TEMP_COLD);
        if (isHot && !wasHot) signals.add(EnvSignal.TEMP_HOT);
        if (!isCold && !isHot && (wasCold || wasHot)) signals.add(EnvSignal.TEMP_NORMAL);

        // ── 黑暗 (TorchLightPipeline 消费 DARKNESS; 死信号 DARKNESS_CLEAR 已删) ──
        boolean wasDark = prev != null && prev.lightAtMaid() < cfg.darknessThreshold();
        boolean isDark = now.lightAtMaid() < cfg.darknessThreshold();
        if (isDark && !wasDark) signals.add(EnvSignal.DARKNESS);

        // ── 实体 (HaqiPipeline 消费 MAID_NEARBY; MONSTER/DARKNESS_CLEAR/FRIENDLY/MAID_CLEAR 死信号全删) ──
        boolean hadMaid = prevEnt != null && prevEnt.maid();
        boolean hasMaid = nowEnt != null && nowEnt.maid();
        if (hasMaid && !hadMaid) signals.add(EnvSignal.MAID_NEARBY);

        return signals;
    }
}
