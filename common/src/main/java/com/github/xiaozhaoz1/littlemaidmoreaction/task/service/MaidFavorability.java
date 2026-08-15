package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 女仆好感度乘区 (v79.39) — 等级读取 + 两条升级线 (效率/消耗), 管线自己乘。
 *
 * <p>效率线: {@link #workSpeedMultiplier} — 速度倍率 (管线乘到速度上: 间隔 = 基准 / speed)。
 * 消耗线: {@link #costMultiplier} — 消耗倍率 (管线乘到耐久/食物等消耗上)。
 * 每级递增可配 (ActiveTaskConfig maid_favorability 组 + Cloth GUI); 总开关关 → 恒 1.0。
 */
public final class MaidFavorability {

    private MaidFavorability() {}

    /** TLM 好感度等级 (0-3, 4 级) */
    public static int getLevel(EntityMaid maid) {
        return maid.getFavorabilityManager().getLevel();
    }

    /**
     * 纯函数 (审计 T1 抽取 — 错题 #174 铁律): 等级/总开关/三级参数注入, JVM 可测。
     * 效率线与消耗线共用同形 switch; Lv0 与未知等级恒 1.0。
     */
    public static double forLevel(int level, boolean enabled, double l1, double l2, double l3) {
        if (!enabled) return 1.0;
        return switch (level) {
            case 3 -> l3;
            case 2 -> l2;
            case 1 -> l1;
            default -> 1.0;
        };
    }

    /**
     * 效率乘区 — Lv0=1.0, Lv1/2/3 查配置 (默认 1.1/1.25/1.5)。
     * 管线用法: 间隔 = (int)(基准间隔 / workSpeedMultiplier)。
     */
    public static double workSpeedMultiplier(EntityMaid maid) {
        return forLevel(getLevel(maid),
                ActiveTaskConfig.MAID_FAVORABILITY_ENABLED.get(),
                ActiveTaskConfig.FAVOR_SPEED_L1.get(),
                ActiveTaskConfig.FAVOR_SPEED_L2.get(),
                ActiveTaskConfig.FAVOR_SPEED_L3.get());
    }

    /**
     * 消耗乘区 — Lv0=1.0, Lv1/2/3 查配置 (默认 0.9/0.75/0.5)。
     * 管线用法: 消耗 = max(1, (int)(基准消耗 × costMultiplier))。
     */
    public static double costMultiplier(EntityMaid maid) {
        return forLevel(getLevel(maid),
                ActiveTaskConfig.MAID_FAVORABILITY_ENABLED.get(),
                ActiveTaskConfig.FAVOR_COST_L1.get(),
                ActiveTaskConfig.FAVOR_COST_L2.get(),
                ActiveTaskConfig.FAVOR_COST_L3.get());
    }
}
