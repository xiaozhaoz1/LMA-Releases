package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla;

import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.maideditor.BuiltinMaidEditorRegistration;

/**
 * 原版功能兼容模块 (v73 清理: CompatScanner 扫描已删 — 扫描目标包随规则引擎 impl 裁撤为空)。
 *
 * <p>保留: 女仆编辑器注册 + 被动环境感知任务注册 (均为活功能)。
 */
public final class VanillaCompat {

    private VanillaCompat() {}

    public static void init() {
        BuiltinMaidEditorRegistration.init();
        // v63: 被动环境感知任务注册
        com.github.xiaozhaoz1.littlemaidmoreaction.task.sense.PassiveSenseRegistration.init();
    }
}
