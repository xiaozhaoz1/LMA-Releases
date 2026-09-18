package com.github.xiaozhaoz1.littlemaidmoreaction.task.passive;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;

/**
 * 被动任务启用判定统一入口 (v79.62.1 立, v79.63 收敛为单一真相源)。
 *
 * <p><b>开关归属由任务自己声明</b> ({@link TaskConfigurable#switchScope()}):
 * <ul>
 *   <li>{@link TaskConfigurable.SwitchScope#PER_MAID} — haqi / jiuhu_milk 等: 开关存
 *       {@code pipelineConfig(maid)["enabled"]}, 缺省 <b>false</b> (默认关闭, 子任务界面显式开启)</li>
 *   <li>{@link TaskConfigurable.SwitchScope#GLOBAL} (默认) — 其余被动: 全局
 *       {@link TaskToggle} (黑名单语义, 缺省开启)</li>
 * </ul>
 *
 * <p>本类替换 v79.62.1 在 {@code TaskDispatcher.submitPassive} 内的硬编码
 * {@code if ("haqi".equals(type) || "jiuhu_milk".equals(type))} — 三处判定
 * (submitPassive 提交门 / GameTickPipelineManager 运行判定 / 运行中关开关的 10t 清理)
 * 全部改走本方法, 消灭"提交看 per-maid, 清理看全局"的双轨漂移。
 */
public final class PassiveConfigUtil {

    /** 该任务是否属于"每女仆开关"型 (声明式判定, 无 taskType 字面量) */
    public static boolean isPerMaidSwitch(String taskType) {
        TaskRegistry.TaskHandler h = TaskRegistry.get(taskType);
        return h != null && h.pipeline() instanceof TaskConfigurable c
                && c.switchScope() == TaskConfigurable.SwitchScope.PER_MAID;
    }

    /**
     * 被动任务当前是否启用 — 提交门 / 运行判定 / 关开关清理**共用同一判据**。
     *
     * <p>per-maid 型: {@code pipelineConfig["enabled"]} (缺省 false);
     * 全局型: {@code TaskToggle.isEnabled} (缺省 true)。未注册 / 无 pipeline → false。
     */
    public static boolean isPassiveEnabled(EntityMaid maid, String taskType) {
        TaskRegistry.TaskHandler h = TaskRegistry.get(taskType);
        if (h == null) return false;
        if (h.pipeline() instanceof TaskConfigurable c
                && c.switchScope() == TaskConfigurable.SwitchScope.PER_MAID) {
            var cfg = c.pipelineConfig(maid);
            return cfg != null && cfg.getBoolean(com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable.KEY_ENABLED);
        }
        return TaskToggle.isEnabled(taskType);
    }

    /** 兼容旧名 (v79.62.1 起为 haqi/jiuhu_milk 专用) — 语义同 per-maid 判定, 委托 {@link #isPassiveEnabled} */
    @Deprecated
    public static boolean isEnabled(EntityMaid maid, String taskType) {
        return isPassiveEnabled(maid, taskType);
    }

    private PassiveConfigUtil() {}
}
