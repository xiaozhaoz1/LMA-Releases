package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * AI 操控权限门控 (v73) — 女仆 PersistentData 标记。
 *
 * <p>由主动任务 {@code ai_control} 生命周期驱动: 任务开启 → enable (写 PD),
 * 任务取消/清理 → disable (删键, 闭环)。世界操作类 AI 工具在
 * {@code trigger()} 中检查 {@link #isEnabled} — 未开启时工具不暴露给 LLM (只读工具不受限)。
 */
public final class AiControlGate {

    private static final String KEY = "lma_ai_control";

    private AiControlGate() {}

    /** AI 操控权限是否开启 (任务 ai_control 运行中) */
    public static boolean isEnabled(EntityMaid maid) {
        return maid.getPersistentData().getBoolean(KEY);
    }

    /** 开启 (由 ai_control 任务 executor 首次调用写入) */
    public static void enable(EntityMaid maid) {
        maid.getPersistentData().putBoolean(KEY, true);
    }

    /** 关闭 (由 ai_control 任务 onCleanup 调用, 键删除闭环) */
    public static void disable(EntityMaid maid) {
        maid.getPersistentData().remove(KEY);
    }
}
