package com.github.xiaozhaoz1.littlemaidmoreaction.task.passive;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;

/**
 * v79.62.1 被动管线启用判定 — haqi/jiuhu_milk 由管线 per-maid 配置 (pipelineConfig "enabled",
 * 子任务界面开关) 门控, 不再用全局 TaskToggle. 缺省 = pipelineConfig 无 enabled 键 → true.
 */
public final class PassiveConfigUtil {

    /** 管线 per-maid 启用 — v79.62.1 用户裁定 haqi/jiuhu_milk 默认关闭 (缺省 false);
     *  子屏开关显式设 true 才启用 */
    public static boolean isEnabled(EntityMaid maid, String taskType) {
        TaskRegistry.TaskHandler h = TaskRegistry.get(taskType);
        if (h == null || !(h.pipeline() instanceof TaskConfigurable c)) return false;
        var cfg = c.pipelineConfig(maid);
        if (cfg == null) return false;
        return cfg.getBoolean("enabled");
    }

    private PassiveConfigUtil() {}
}
