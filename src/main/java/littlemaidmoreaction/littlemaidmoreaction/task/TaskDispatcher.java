package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.server.level.ServerLevel;

/**
 * 任务中央调度器 (v43) — 替代分散的任务启动/停止入口。
 *
 * <p>所有任务生命周期操作 (提交/取消/完成/失败) 必须通过本类。
 * 禁止直接操作 PersistentData 中的 lma_flow_* 字段。
 */
public final class TaskDispatcher {

    private TaskDispatcher() {}

    /**
     * 提交任务 — 冲突检测 → 验证 → 写入状态。
     *
     * @param maid     女仆实体
     * @param taskType 任务类型 (如 "craft_chain", "furnace")
     * @param target   目标物品/方块 (可为null或空)
     * @param count    目标数量 (0=默认)
     * @return true 表示任务已启动
     */
    /**
     * 提交任务 — 验证 → 冲突检测 → 写入状态 (v43.1 fix: 先验证再取消, 防止验证失败导致旧任务丢失)
     */
    public static boolean submit(EntityMaid maid, String taskType, String target, int count) {
        if (!(maid.level() instanceof ServerLevel)) return false;

        // 1. 先验证 (失败则旧任务不受影响)
        PipelineResult result = TaskRegistry.validate(maid, taskType,
            "", target != null ? target : "", count);
        if (!result.completed()) return false;

        // 2. 冲突检测: 验证通过后再取消旧任务
        String current = LmaTaskDataHelper.getFlowTask(maid);
        if (!current.isEmpty() && !current.equals(taskType)) {
            cancel(maid);
        }

        // 3. 统一写入
        long now = maid.level().getGameTime();
        TaskStateManager.init(maid, taskType, now);
        // v44: 存储 target 到 NBT, 保持向后兼容 (pipeline executor 从 lma_task_target 读取)
        if (target != null && !target.isEmpty()) {
            maid.getPersistentData().putString(TaskKeys.TASK_TARGET, target);
        }
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] submit maid={} task={} target={} count={}",
            maid.getStringUUID(), taskType, target, count);
        return true;
    }

    /** 取消任务 — 通知管线中断 + onCleanup + 设取消标记 */
    public static void cancel(EntityMaid maid) {
        // v44: 完整取消流程 — interrupt → onStop → onCleanup
        String task = LmaTaskDataHelper.getFlowTask(maid);
        if (!task.isEmpty()) {
            var h = TaskRegistry.get(task);
            if (h != null) {
                h.pipeline().interrupt(maid);
                h.executor().onStop(maid);
                h.pipeline().onCleanup(maid);
            }
        }
        LmaTaskDataHelper.setFlowState(maid, TaskKeys.STATE_CANCELLED);
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] cancel maid={} task={}",
            maid.getStringUUID(), task);
    }

    /** 超时 — 由 TaskEngine 调用。编排 interrupt→onStop→onCleanup→retry */
    public static void timeout(EntityMaid maid) {
        String task = LmaTaskDataHelper.getFlowTask(maid);
        var h = getHandler(task);
        if (h != null) {
            h.pipeline().interrupt(maid);
            h.executor().onStop(maid);
            h.pipeline().onCleanup(maid);
        }
        LmaTaskDataHelper.setFlowState(maid, TaskKeys.STATE_FAILED);
        TaskStateManager.clearAll(maid);
        LittleMaidMoreAction.LOGGER.warn("[LMA/Task] timeout maid={} task={}",
            maid.getStringUUID(), task);
        // v45: 重试策略
        if (h != null && h.pipeline().retryPolicy().shouldRetry(0)) {
            LittleMaidMoreAction.LOGGER.info("[LMA/Task] retry submit maid={} task={}",
                maid.getStringUUID(), task);
            submit(maid, task, null, 0);
        }
    }

    /** 标记任务完成 — onComplete→onCleanup→STATE_COMPLETED→clearAll */
    public static void complete(EntityMaid maid) {
        String task = LmaTaskDataHelper.getFlowTask(maid);
        var h = getHandler(task);
        if (h != null) {
            h.executor().onComplete(
                maid.level() instanceof ServerLevel sl ? sl : null, maid, null);
            h.pipeline().onCleanup(maid);
        }
        LmaTaskDataHelper.setFlowState(maid, TaskKeys.STATE_COMPLETED);
        TaskStateManager.clearAll(maid);
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] complete maid={} task={}",
            maid.getStringUUID(), task);
    }

    /** 标记任务失败 — interrupt→onStop→onCleanup→STATE_FAILED→clearAll→retry? */
    public static void fail(EntityMaid maid, String reason) {
        maid.getPersistentData().putString(TaskKeys.FAIL_REASON, reason);
        String task = LmaTaskDataHelper.getFlowTask(maid);
        var h = getHandler(task);
        if (h != null) {
            h.pipeline().interrupt(maid);
            h.executor().onStop(maid);
            h.pipeline().onCleanup(maid);
        }
        LmaTaskDataHelper.setFlowState(maid, TaskKeys.STATE_FAILED);
        TaskStateManager.clearAll(maid);
        LittleMaidMoreAction.LOGGER.warn("[LMA/Task] fail maid={} task={} reason={}",
            maid.getStringUUID(), task, reason);
        if (h != null && h.pipeline().retryPolicy().shouldRetry(0)) {
            LittleMaidMoreAction.LOGGER.info("[LMA/Task] retry submit maid={} task={}",
                maid.getStringUUID(), task);
            submit(maid, task, null, 0);
        }
    }

    private static TaskRegistry.TaskHandler getHandler(String taskType) {
        return TaskRegistry.get(taskType);
    }
}
