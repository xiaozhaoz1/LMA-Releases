package littlemaidmoreaction.littlemaidmoreaction.task.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskToggle;
import net.minecraft.server.level.ServerLevel;

/**
 * 任务中央调度器 (v43) — 替代分散的任务启动/停止入口。
 *
 * <p>所有任务生命周期操作 (提交/取消/完成/失败) 必须通过本类。
 * 禁止直接操作 PersistentData 中的 lma_flow_* 字段。
 */
public final class TaskDispatcher {

    /** v67.3: 失败气泡节流 — 同一女仆 30 秒内不重复刷 (validate 失败无限重试循环防刷屏) */
    private static final long FAIL_BUBBLE_INTERVAL = 600;
    private static final java.util.Map<EntityMaid, Long> FAIL_BUBBLE =
            new java.util.WeakHashMap<>();

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
        if (!result.completed()) {
            // v67.3: 失败气泡 — 显示 validate feedback (30秒节流防无限重试刷屏)
            if (!result.feedback().isEmpty() && maid.level() instanceof ServerLevel sl) {
                long now = sl.getGameTime();
                Long last = FAIL_BUBBLE.get(maid);
                if (last == null || now - last >= FAIL_BUBBLE_INTERVAL) {
                    FAIL_BUBBLE.put(maid, now);
                    maid.getChatBubbleManager().addTextChatBubble("§c" + result.feedback());
                }
            }
            return false;
        }

        // 2. 冲突检测: 验证通过后再取消旧任务
        String current = FlowTaskData.getTask(maid);
        if (!current.isEmpty() && !current.equals(taskType)) {
            cancel(maid);
        }

        // 3. 统一写入
        long now = maid.level().getGameTime();
        TaskStateManager.init(maid, taskType, now);
        // v44: 存储 target 到 NBT
        if (target != null && !target.isEmpty()) {
            maid.getPersistentData().putString(TaskKeys.TASK_TARGET, target);
        }
        // v64: 存储数量 — 0=无限, >0=指定数量
        if (count > 0) {
            maid.getPersistentData().putInt(TaskKeys.FLOW_MAX_COUNT, count);
        }
        // v53: 新任务启动时重置重试计数
        maid.getPersistentData().remove(TaskKeys.RETRY_COUNT);
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] submit maid={} task={} target={} count={}",
            maid.getStringUUID(), taskType, target, count);
        return true;
    }

    /** 取消任务 — 通知管线中断 (→onCleanup) + 设取消标记 + 清理 */
    public static void cancel(EntityMaid maid) {
        LittleMaidMoreAction.LOGGER.warn("[LMA/Task] cancel CALLED from: {}",
            java.util.Arrays.stream(Thread.currentThread().getStackTrace()).skip(1).limit(5)
                .map(StackTraceElement::toString).reduce((a,b) -> a + "\n  <- " + b).orElse("?"));
        // v62: interrupt→onCleanup
        String task = FlowTaskData.getTask(maid);
        if (!task.isEmpty()) {
            var h = TaskRegistry.get(task);
            if (h != null) {
                h.pipeline().interrupt(maid);
            }
        }
        FlowTaskData.setState(maid, TaskKeys.STATE_CANCELLED);
        TaskStateManager.clearAll(maid); // v53: cancel 后清除残留 NBT (同 complete/fail)
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] cancel maid={} task={}",
            maid.getStringUUID(), task);
    }

    /** 超时 — 由 TaskEngine 调用。编排 onTimeout→interrupt(→onCleanup)→retry */
    public static void timeout(EntityMaid maid) {
        String task = FlowTaskData.getTask(maid);
        var h = getHandler(task);
        if (h != null) {
            maid.getChatBubbleManager().addTextChatBubble("⏰ " + task + " 超时");
            h.pipeline().interrupt(maid);  // v62: interrupt→onCleanup
        }
        FlowTaskData.setState(maid, TaskKeys.STATE_FAILED);
        TaskStateManager.clearAll(maid);
        LittleMaidMoreAction.LOGGER.warn("[LMA/Task] timeout maid={} task={}",
            maid.getStringUUID(), task);
        // v53: 重试策略 — NBT 计数器防止 fixed(N) 无限重试
        if (h != null) {
            int retryCount = maid.getPersistentData().getInt(TaskKeys.RETRY_COUNT);
            if (h.pipeline().retryPolicy().shouldRetry(retryCount)) {
                maid.getPersistentData().putInt(TaskKeys.RETRY_COUNT, retryCount + 1);
                LittleMaidMoreAction.LOGGER.info("[LMA/Task] retry #{}/{} maid={} task={}",
                    retryCount + 1, h.pipeline().retryPolicy().maxRetries(), maid.getStringUUID(), task);
                submit(maid, task, null, 0);
            }
        }
    }

    /** 标记任务完成 — onCleanup→STATE_COMPLETED→clearAll */
    public static void complete(EntityMaid maid) {
        String task = FlowTaskData.getTask(maid);
        var h = getHandler(task);
        if (h != null) {
            h.pipeline().onCleanup(maid);
        }
        FlowTaskData.setState(maid, TaskKeys.STATE_COMPLETED);
        TaskStateManager.clearAll(maid);
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] complete maid={} task={}",
            maid.getStringUUID(), task);
    }

    /** 标记任务失败 — interrupt(→onCleanup)→STATE_FAILED→clearAll→retry? */
    public static void fail(EntityMaid maid, String reason) {
        maid.getPersistentData().putString(TaskKeys.FAIL_REASON, reason);
        String task = FlowTaskData.getTask(maid);
        var h = getHandler(task);
        if (h != null) {
            h.pipeline().interrupt(maid);  // v62: interrupt→onCleanup
        }
        FlowTaskData.setState(maid, TaskKeys.STATE_FAILED);
        TaskStateManager.clearAll(maid);
        LittleMaidMoreAction.LOGGER.warn("[LMA/Task] fail maid={} task={} reason={}",
            maid.getStringUUID(), task, reason);
        // v53: 重试策略 — NBT 计数器防止 fixed(N) 无限重试
        if (h != null) {
            int retryCount = maid.getPersistentData().getInt(TaskKeys.RETRY_COUNT);
            if (h.pipeline().retryPolicy().shouldRetry(retryCount)) {
                maid.getPersistentData().putInt(TaskKeys.RETRY_COUNT, retryCount + 1);
                LittleMaidMoreAction.LOGGER.info("[LMA/Task] retry #{}/{} maid={} task={}",
                    retryCount + 1, h.pipeline().retryPolicy().maxRetries(), maid.getStringUUID(), task);
                submit(maid, task, null, 0);
            }
        }
    }

    private static TaskRegistry.TaskHandler getHandler(String taskType) {
        return TaskRegistry.get(taskType);
    }

    // ── 被动任务 (v61) — 与主动任务隔离, 可并行运行 ──

    /** 提交被动任务 (与 lma_flow_task 不冲突) */
    public static void submitPassive(EntityMaid maid, String taskType) {
        if (TaskRegistry.get(taskType) == null) return;
        if (!TaskToggle.isEnabled(taskType)) return;
        maid.getPersistentData().putString(TaskKeys.passiveKey(taskType), TaskKeys.STATE_IN_PROGRESS);
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] submitPassive maid={} task={}", maid.getStringUUID(), taskType);
    }

    /** 取消被动任务 */
    public static void cancelPassive(EntityMaid maid, String taskType) {
        var h = TaskRegistry.get(taskType);
        if (h != null) h.pipeline().onCleanup(maid);
        maid.getPersistentData().remove(TaskKeys.passiveKey(taskType));
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] cancelPassive maid={} task={}", maid.getStringUUID(), taskType);
    }
}
