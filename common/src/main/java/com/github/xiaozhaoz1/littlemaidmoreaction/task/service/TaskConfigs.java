package com.github.xiaozhaoz1.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import net.minecraft.nbt.CompoundTag;

/**
 * 任务配置静态访问 — 静态上下文 (Service/SetupHandler/网络包) 获取任务管线配置。
 *
 * <p>v67.1: 替代各 Pipeline 自己实现的静态 {@code config(maid)} (硬编码 lma_cfg_ 键名)。
 * 统一走 {@link TaskRegistry} → {@link com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline#pipelineConfig}，
 * 键名由 {@code lma_cfg_&lt;taskType&gt;} 自动派生。
 */
public final class TaskConfigs {

    private TaskConfigs() {}

    /**
     * 获取指定任务的管线配置 (跨任务持久, 不会被 onCleanup 清除)。
     *
     * @param taskType 任务类型 (如 "block_interact")
     */
    public static CompoundTag get(EntityMaid maid, String taskType) {
        TaskRegistry.TaskHandler handler = TaskRegistry.get(taskType);
        if (handler == null) {
            throw new IllegalArgumentException("未知任务类型: " + taskType);
        }
        return handler.pipeline().pipelineConfig(maid);
    }
}
