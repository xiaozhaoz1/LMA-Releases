package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 环境边沿检测纯逻辑核心 (v79.3) — 从 EnvSenseBroadcaster.detectSignals 剥离。
 *
 * <p>零 MC 依赖 (WorldInfo 纯 record + EntityPresence 纯), 字段级等价搬移原 18 边沿
 * + 新 3 边沿 (BIOME_CHANGE / STRUCTURE_ENTER / STRUCTURE_LEAVE)。
 *
 * <p>语义约定 (与原实现一致): prev==null (首帧) 时"状态进入"类信号 (SNOWING/RAINING/
 * TEMP_COLD/DARKNESS/MONSTER_NEARBY 等) 可触发, "变化"类 (DAY_NIGHT_CHANGE/
 * DIMENSION_CHANGE/TIME_SEGMENT/BIOME_CHANGE/STRUCTURE_*) 需 prev 存在。
 */
public final class EnvEdgeDetector {

    /** 实体在场状态 — 由快照实体分类派生 (纯, 供检测核心) */
    public record EntityPresence(boolean monster, boolean friendly, boolean maid) {
        public static final EntityPresence NONE = new EntityPresence(false, false, false);
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

        // ── 天气 ──
        boolean wasSnowing = prev != null && prev.raining() && "SNOW".equals(prev.precipitation());
        boolean isSnowing = now.raining() && "SNOW".equals(now.precipitation());
        if (isSnowing && !wasSnowing) signals.add(EnvSignal.SNOWING);

        boolean wasRaining = prev != null && prev.raining() && !"SNOW".equals(prev.precipitation());
        boolean isRaining = now.raining() && !"SNOW".equals(now.precipitation());
        if (isRaining && !wasRaining) signals.add(EnvSignal.RAINING);

        if (prev != null && !prev.thundering() && now.thundering()) signals.add(EnvSignal.THUNDER_START);
        if (prev != null && prev.raining() && !now.raining()) signals.add(EnvSignal.WEATHER_CLEAR);

        // ── 温度 ──
        boolean wasCold = prev != null && prev.temperature() < cfg.coldThreshold();
        boolean isCold = now.temperature() < cfg.coldThreshold();
        boolean wasHot = prev != null && prev.temperature() > cfg.hotThreshold();
        boolean isHot = now.temperature() > cfg.hotThreshold();
        if (isCold && !wasCold) signals.add(EnvSignal.TEMP_COLD);
        if (isHot && !wasHot) signals.add(EnvSignal.TEMP_HOT);
        if (!isCold && !isHot && (wasCold || wasHot)) signals.add(EnvSignal.TEMP_NORMAL);

        // ── 昼夜 ──
        if (prev != null && prev.day() != now.day()) signals.add(EnvSignal.DAY_NIGHT_CHANGE);

        // ── 黑暗 ──
        boolean wasDark = prev != null && prev.lightAtMaid() < cfg.darknessThreshold();
        boolean isDark = now.lightAtMaid() < cfg.darknessThreshold();
        if (isDark && !wasDark) signals.add(EnvSignal.DARKNESS);

        // ── 维度/时段 ──
        if (prev != null && !prev.dimension().equals(now.dimension())) signals.add(EnvSignal.DIMENSION_CHANGE);
        if (prev != null && !prev.timeSegment().equals(now.timeSegment())) signals.add(EnvSignal.TIME_SEGMENT);

        // ── v79.3: 生物群系 ──
        if (prev != null && !Objects.equals(prev.biomeId(), now.biomeId())) signals.add(EnvSignal.BIOME_CHANGE);

        // ── v79.3: 站立点所在结构 (零成本通道, 与 24000t 最近结构通道互补) ──
        if (prev != null && prev.structuresAt().isEmpty() && !now.structuresAt().isEmpty()) {
            signals.add(EnvSignal.STRUCTURE_ENTER);
        }
        if (prev != null && !prev.structuresAt().isEmpty() && now.structuresAt().isEmpty()) {
            signals.add(EnvSignal.STRUCTURE_LEAVE);
        }

        // ── 实体 ──
        boolean hadMonster = prevEnt != null && prevEnt.monster();
        boolean hasMonster = nowEnt != null && nowEnt.monster();
        if (hasMonster && !hadMonster) signals.add(EnvSignal.MONSTER_NEARBY);
        if (!hasMonster && hadMonster) signals.add(EnvSignal.MONSTER_CLEAR);

        boolean hadFriendly = prevEnt != null && prevEnt.friendly();
        boolean hasFriendly = nowEnt != null && nowEnt.friendly();
        if (hasFriendly && !hadFriendly) signals.add(EnvSignal.FRIENDLY_NEARBY);

        boolean hadMaid = prevEnt != null && prevEnt.maid();
        boolean hasMaid = nowEnt != null && nowEnt.maid();
        if (hasMaid && !hadMaid) signals.add(EnvSignal.MAID_NEARBY);

        return signals;
    }
}
