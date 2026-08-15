package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla;

/**
 * 原版功能兼容模块 (v79.48: BuiltinMaidEditorRegistration 死链删 — 女仆编辑器已退役)。
 *
 * <p>保留: 被动环境感知任务注册 (活功能)。
 */
public final class VanillaCompat {

    private VanillaCompat() {}

    public static void init() {
        // 被动环境感知任务注册
        com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.PassiveSenseRegistration.init();
    }
}
