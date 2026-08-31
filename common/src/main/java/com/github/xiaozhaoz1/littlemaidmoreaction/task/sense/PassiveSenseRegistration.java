package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.TaskRegistryManifest;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.PassiveDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.FestivalPassiveTask;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.RareBiomePassiveTask;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.passive.impl.StructureSensePassiveTask;

/**
 * v63: 被动感知任务注册入口。
 *
 * <p>由 {@code VanillaCompat.init()} 调用。
 * 管线在子包 {@code task.pipeline.sense} 中实现。
 *
 * <p>v79.61x 脱管线改造: 纯触发型 (structure_sense/festival — 零 tick) 不再实现 TaskPipeline,
 * 注册为无 pipeline 占位条目 (任务树可见 + TaskToggle 开关, 用户裁定) + 经
 * {@link PassiveDispatcher} 驱动; 跨 tick 动作型 (temp_adapt/torch_light) 与
 * 真 FSM/时间关键 (haqi/self_rescue) 仍注册管线 (GMPM 驱动, 后续阶段迁移 tick 面)。
 * v79.62: snow_shovel 管线已删 (TLM 原版清雪覆盖, 用户裁定)。
 */
public final class PassiveSenseRegistration {

    private PassiveSenseRegistration() {}

    /**
     * 注册所有环境感知被动任务。
     * 全部 showInBar=false，在任务树被动分区展示。
     */
    public static void init() {
        // 规格表驱动 (v79.61 规格化): 管线 4 + 纯触发 2, 名字单一真相 — 与主动任务同构
        for (TaskRegistryManifest.TaskSpec s : TaskRegistryManifest.PASSIVE) {
            TaskRegistry.registerPassive(s.taskType(), s.factory().get());
        }
        // 纯触发型 (无 pipeline 占位 — 用户裁定) — Dispatcher.register 校验 TaskRegistry 条目
        // 已存在 (防漂移, 漏注册/改名启动即炸)
        TaskRegistry.registerPassive("structure_sense");
        TaskRegistry.registerPassive("festival");
        TaskRegistry.registerPassive("rare_biome");
        PassiveDispatcher.register(new StructureSensePassiveTask());
        PassiveDispatcher.register(new FestivalPassiveTask());
        PassiveDispatcher.register(new RareBiomePassiveTask());
        // 注册完整性 fail-fast (v79.61 批 3c C3) — 被动 7 全注册, 漂移启动即炸
        for (TaskRegistryManifest.TaskSpec s : TaskRegistryManifest.PASSIVE) {
            if (TaskRegistry.get(s.taskType()) == null) {
                throw new IllegalStateException("[LMA] 被动任务注册缺失: " + s.taskType());
            }
        }
        for (String t : new String[]{"structure_sense", "festival", "rare_biome"}) {
            if (TaskRegistry.get(t) == null || PassiveDispatcher.get(t) == null) {
                throw new IllegalStateException("[LMA] 纯触发被动注册缺失: " + t);
            }
        }
        LittleMaidMoreAction.LOGGER.info("[EnvSense] 被动感知任务注册完成");
    }
}
