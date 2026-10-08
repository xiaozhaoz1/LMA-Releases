package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * lma_task_* + lma_saved_* + adapter 通信标记 (v62).
 *
 * <p>目标/输入/完成/失败 + 状态保存恢复 + TLM/GUI 标记。
 * v79.30: 内部实现收编到 {@link MaidData} 类型化 API ({@link DataKey}), 公开签名不变。
 */
public final class TaskMetaData {

    // ── 目标 ──

    public static String getTarget(EntityMaid maid) {
        return MaidData.get(maid, DataKey.TASK_TARGET);
    }

    // setTarget 已删 (v79.55, 错题 #181): 零调用方死门面 — TASK_TARGET 由 TaskDispatcher:77 直写

    // ── adapter → task 通信标记 ──

    /** TLM 任务切换标记 — TlmTaskMonitor/LmaFlowCoordinationBehavior 写 (值 = 完整 RL, 错题 #179), GMPM 消费即删 */
    public static String getTlmSwitch(EntityMaid maid) {
        return MaidData.get(maid, DataKey.TLM_SWITCH);
    }

    public static void setTlmSwitch(EntityMaid maid, String uid) {
        MaidData.put(maid, DataKey.TLM_SWITCH, uid);
    }

    public static void clearTlmSwitch(EntityMaid maid) {
        MaidData.remove(maid, DataKey.TLM_SWITCH);
    }

    /** GUI 启动新任务标记 — LmaFlowCoordinationBehavior 写, TaskTickHandler 读 */
    public static String getGuiInit(EntityMaid maid) {
        return MaidData.get(maid, DataKey.GUI_INIT);
    }

    public static void setGuiInit(EntityMaid maid, String taskType) {
        MaidData.put(maid, DataKey.GUI_INIT, taskType);
    }

    public static void clearGuiInit(EntityMaid maid) {
        MaidData.remove(maid, DataKey.GUI_INIT);
    }

    private TaskMetaData() {}
}
