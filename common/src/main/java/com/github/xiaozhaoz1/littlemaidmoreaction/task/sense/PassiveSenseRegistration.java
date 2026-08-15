package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;

/**
 * v63: 被动感知任务注册入口。
 *
 * <p>由 {@code VanillaCompat.init()} 调用。
 * 管线在子包 {@code task.pipeline.sense} 中实现。
 */
public final class PassiveSenseRegistration {

    private PassiveSenseRegistration() {}

    /**
     * 注册所有环境感知被动任务。
     * 全部 showInBar=false，在任务树被动分区展示。
     */
    public static void init() {
        // 规格表驱动 (v79.61 规格化): 名字+构造引用单一真相 — 与主动任务同构, 名字不再抄两遍
        // (规格含逐任务注释: 哈气默认关闭/黑暗点亮/结构气泡/节日/自救 — 见 TaskRegistryManifest.PASSIVE)
        for (com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistryManifest.TaskSpec s
                : com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistryManifest.PASSIVE) {
            TaskRegistry.registerPassive(s.taskType(), s.factory().get());
        }
        // 注册完整性 fail-fast (v79.61 批 3c C3) — 被动 7 全注册, 漂移启动即炸
        for (com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistryManifest.TaskSpec s
                : com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistryManifest.PASSIVE) {
            if (TaskRegistry.get(s.taskType()) == null) {
                throw new IllegalStateException("[LMA] 被动任务注册缺失: " + s.taskType());
            }
        }
        LittleMaidMoreAction.LOGGER.info("[EnvSense] 被动感知任务注册完成");
    }
}
