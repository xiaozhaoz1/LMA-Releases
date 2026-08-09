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
        // 管线在 task.pipeline.sense.* — 编译时自动发现
        // 为防步骤2编译: 若 Pipeline 类未加载则跳过
        try {
            registerIfExists("snow_shovel", "task.pipeline.sense.SnowShovelPipeline");
            registerIfExists("light_control", "task.pipeline.sense.LightControlPipeline");
            registerIfExists("temp_adapt", "task.pipeline.sense.TempAdaptPipeline");
            registerIfExists("monster_log", "task.pipeline.sense.MonsterLogPipeline");
            // v79.9: 哈气 (默认关闭 — HAQI_ENABLED 门控; 触发走 MAID_NEARBY 信号)
            registerIfExists("haqi", "task.pipeline.sense.HaqiPipeline");
            LittleMaidMoreAction.LOGGER.info("[EnvSense] 被动感知任务注册完成");
        } catch (Exception ex) {
            LittleMaidMoreAction.LOGGER.warn("[EnvSense] 被动感知任务注册跳过: {}", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerIfExists(String taskType, String className) {
        try {
            Class<?> clazz = Class.forName("com.github.xiaozhaoz1.littlemaidmoreaction." + className);
            var pipeline = (com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline) clazz
                    .getDeclaredConstructor().newInstance();
            TaskRegistry.registerPassive(taskType, pipeline);
        } catch (ClassNotFoundException e) {
            LittleMaidMoreAction.LOGGER.info("[EnvSense] 跳过未实现的管线: {}", taskType);
        } catch (Exception e) {
            LittleMaidMoreAction.LOGGER.error("[EnvSense] 管线 {} 初始化失败", taskType, e);
        }
    }
}
