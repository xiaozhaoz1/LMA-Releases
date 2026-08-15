package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.DataKey;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskProgressDisplay;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import net.minecraft.server.level.ServerLevel;

/**
 * 任务中央调度器 (v43) — 替代分散的任务启动/停止入口。
 *
 * <p>所有任务生命周期操作 (提交/取消/完成/失败) 必须通过本类。
 * 禁止直接操作 PersistentData 中的 lma_flow_* 字段。
 *
 * <p>v79.21: 失败/超时气泡统一走 {@link MaidChatBubbleApi} (节流内置于 API, 600t) —
 * 删除本地 FAIL_BUBBLE 节流表; 顺带补上超时气泡缺失的节流 (错题: 同类刷屏 bug)。
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
     * 提交任务 — 验证 → 冲突检测 → 写入状态 (先验证再取消, 防止验证失败导致旧任务丢失)
     */
    public static boolean submit(EntityMaid maid, String taskType, String target, int count) {
        if (!(maid.level() instanceof ServerLevel)) return false;

        // 1. 先验证 (失败则旧任务不受影响)
        PipelineResult result = TaskRegistry.validate(maid, taskType,
            "", target != null ? target : "", count);
        if (!result.completed()) {
            // 失败气泡 (红色 ✘, API 内置 30 秒节流防无限重试刷屏)
            if (!result.feedback().isEmpty()) {
                MaidChatBubbleApi.showFail(maid, result.feedback());
            }
            return false;
        }

        // 哈气互斥 — 哈气运行中拒绝主动任务
        if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData()
                .getString(TaskKeys.passiveKey("haqi")))) {
            return false;
        }

        // 2. 冲突检测: 验证通过后再取消旧任务
        // 优先级策略 — 新任务严格更低 → 拒绝 (失败气泡节流); 等/高 → 抢占 (既有行为)
        String current = FlowTaskData.getTask(maid);
        if (!current.isEmpty() && !current.equals(taskType)) {
            if (!shouldPreempt(priorityOf(current), priorityOf(taskType))) {
                MaidChatBubbleApi.showFail(maid, "已有更高优先级任务: " + current);
                return false;
            }
            cancel(maid);
        }

        // 3. 统一写入
        long now = maid.level().getGameTime();
        TaskStateManager.init(maid, taskType, now);
        // 存储 target 到 NBT
        if (target != null && !target.isEmpty()) {
            MaidData.put(maid, DataKey.TASK_TARGET, target);
        }
        // 存储数量 — 0=无限, >0=指定数量
        if (count > 0) {
            FlowTaskData.setMaxCount(maid, count);
        }
        // 重试机制删除 (RetryPolicy) — 主动任务靠 TLM 任务栏自动重启, 被动靠信号重触发
        // 任务开始气泡 (信息型, 无节流 — 任务生命周期天然限频)
        LmaTaskProgressDisplay.showTaskStart(maid, taskType);
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] submit maid={} task={} target={} count={}",
            maid.getStringUUID(), taskType, target, count);
        return true;
    }

    /** 取消任务 — 通知管线中断 (→onCleanup) + 设取消标记 + 清理 */
    public static void cancel(EntityMaid maid) {
        // 堆栈日志降级 debug — cancel 高频路径 (任务切换/取消), 生产每次抓 5 层
        // 堆栈 = 开销 + 日志噪音; 调试需求保留在 debug 层
        if (LittleMaidMoreAction.LOGGER.isDebugEnabled()) {
            LittleMaidMoreAction.LOGGER.debug("[LMA/Task] cancel CALLED from: {}",
                java.util.Arrays.stream(Thread.currentThread().getStackTrace()).skip(1).limit(5)
                    .map(StackTraceElement::toString).reduce((a,b) -> a + "\n  <- " + b).orElse("?"));
        }
        // interrupt→onCleanup
        String task = FlowTaskData.getTask(maid);
        if (!task.isEmpty()) {
            var h = TaskRegistry.get(task);
            if (h != null) {
                h.pipeline().interrupt(maid);
            }
        }
        FlowTaskData.setState(maid, TaskKeys.STATE_CANCELLED);
        TaskStateManager.clearAll(maid); // cancel 后清除残留 NBT (同 complete/fail)
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] cancel maid={} task={}",
            maid.getStringUUID(), task);
    }

    /** 超时 — 由 TaskTickHandler 超时看门狗调用。编排 onTimeout→interrupt(→onCleanup)→retry */
    public static void timeout(EntityMaid maid) {
        String task = FlowTaskData.getTask(maid);
        var h = getHandler(task);
        if (h != null) {
            // 统一失败气泡 (红色 ✘ + API 内置 600t 节流 — 补超时气泡缺失的节流, 错题: 同类刷屏 bug)
            MaidChatBubbleApi.showFail(maid, task + " 超时");
            h.pipeline().interrupt(maid);  // interrupt→onCleanup
        }
        FlowTaskData.setState(maid, TaskKeys.STATE_FAILED);
        TaskStateManager.clearAll(maid);
        LittleMaidMoreAction.LOGGER.warn("[LMA/Task] timeout maid={} task={}",
            maid.getStringUUID(), task);
        // 重试机制删除 — 主动任务 TLM 任务栏自动重启, 被动信号重触发
    }

    /** 标记任务完成 — onCleanup→STATE_COMPLETED→clearAll */
    public static void complete(EntityMaid maid) {
        String task = FlowTaskData.getTask(maid);
        var h = getHandler(task);
        if (h != null) {
            h.pipeline().onCleanup(maid);
        }
        // 完成气泡 (绿色 ✔) — clearAll 前读 counter/max (clearAll 清 FLOW_COUNTER/FLOW_MAX_COUNT)
        int counter = (int) (long) MaidData.get(maid, DataKey.FLOW_COUNTER);
        int maxCount = (int) (long) MaidData.get(maid, DataKey.FLOW_MAX_COUNT);
        FlowTaskData.setState(maid, TaskKeys.STATE_COMPLETED);
        TaskStateManager.clearAll(maid);
        if (!task.isEmpty()) {
            LmaTaskProgressDisplay.showComplete(maid, task, counter, maxCount);
        }
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] complete maid={} task={}",
            maid.getStringUUID(), task);
    }

    /** 标记任务失败 — interrupt(→onCleanup)→STATE_FAILED→clearAll→retry? */
    public static void fail(EntityMaid maid, String reason) {
        // FAIL_REASON 死写已删 (v79.55, 错题 #181): put 后同方法 clearAll 立即删, 零读方 — reason 仅用于日志
        String task = FlowTaskData.getTask(maid);
        var h = getHandler(task);
        if (h != null) {
            h.pipeline().interrupt(maid);  // interrupt→onCleanup
        }
        FlowTaskData.setState(maid, TaskKeys.STATE_FAILED);
        TaskStateManager.clearAll(maid);
        LittleMaidMoreAction.LOGGER.warn("[LMA/Task] fail maid={} task={} reason={}",
            maid.getStringUUID(), task, reason);
    }

    private static TaskRegistry.TaskHandler getHandler(String taskType) {
        return TaskRegistry.get(taskType);
    }

    /**
     * 优先级抢占策略 (纯函数, 可 JVM 测) — 等优先级 = 抢占.
     * 裁定依据: 树内 12 任务全默认 0; GUI_INIT/TLM_SWITCH/绑定提交均依赖"等优先级可抢占",
     * "保持"语义会破坏既有切换 — 仅严格更低拒绝.
     */
    static boolean shouldPreempt(int currentPriority, int newPriority) {
        return newPriority >= currentPriority;
    }

    private static int priorityOf(String taskType) {
        TaskRegistry.TaskHandler h = TaskRegistry.get(taskType);
        return h == null ? 0 : h.pipeline().priority();
    }

    // ── 被动任务 — 与主动任务隔离, 可并行运行 ──

    /** 提交被动任务 (与 lma_flow_task 不冲突) — 哈气运行中拒绝其他被动 (互斥) */
    public static void submitPassive(EntityMaid maid, String taskType) {
        if (TaskRegistry.get(taskType) == null) return;
        if (!TaskToggle.isEnabled(taskType)) return;
        // 哈气互斥 — 哈气运行中其他被动不启动 (哈气自身防重复在 onSignal)
        if (!"haqi".equals(taskType) && TaskKeys.STATE_IN_PROGRESS.equals(
                maid.getPersistentData().getString(TaskKeys.passiveKey("haqi")))) {
            return;
        }
        maid.getPersistentData().putString(TaskKeys.passiveKey(taskType), TaskKeys.STATE_IN_PROGRESS);
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] submitPassive maid={} task={}", maid.getStringUUID(), taskType);
    }

    /** 取消被动任务 */
    public static void cancelPassive(EntityMaid maid, String taskType) {
        var h = TaskRegistry.get(taskType);
        if (h != null) h.pipeline().onCleanup(maid);
        maid.getPersistentData().remove(TaskKeys.passiveKey(taskType));
        // GMPM 被动掩码缓存失效 — 否则 10t 缓存窗口内缓存掩码仍含该管线 → 仍驱动 1 次 (AUDIT LOW #4)
        GameTickPipelineManager.clearMaidCaches(maid);
        LittleMaidMoreAction.LOGGER.info("[LMA/Task] cancelPassive maid={} task={}", maid.getStringUUID(), taskType);
    }
}
