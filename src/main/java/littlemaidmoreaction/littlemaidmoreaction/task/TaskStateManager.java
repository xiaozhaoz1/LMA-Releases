package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.resources.ResourceLocation;

/**
 * 任务状态管理器 (v43) — 所有 lma_flow_* 状态写入的唯一入口。
 *
 * <p>v43: 底层仍用 PersistentData (兼容24个现有文件)。
 * TaskDataKey 注册备用，v44 统一切换。
 *
 * <p>替代分散的 TaskStateService + LmaTaskDataHelper + 5个直接NBT入口。
 */
public final class TaskStateManager {

    private TaskStateManager() {}

    /** 初始化任务 — 仅 TaskDispatcher 调用 (v43.2: package-private) */
    static void init(EntityMaid maid, String taskType, long now) {
        LmaTaskDataHelper.setFlowTask(maid, taskType);
        LmaTaskDataHelper.setFlowState(maid, TaskKeys.STATE_IN_PROGRESS);
        LmaTaskDataHelper.setFlowTick(maid, now);
        // TLM 任务切换 — TaskManager.findTask 返回 IMaidTask
        TaskManager.findTask(ResourceLocation.fromNamespaceAndPath("lma", taskType))
            .ifPresent(maid::setTask);
    }

    /** 心跳 — 刷新活跃时间戳 */
    public static void heartbeat(EntityMaid maid, long now) {
        LmaTaskDataHelper.setFlowTick(maid, now);
    }

    /** 清除所有流程任务状态 — 仅 TaskDispatcher 调用 */
    public static void clearAll(EntityMaid maid) {
        LmaTaskDataHelper.clearAll(maid);
    }

    /** v43.1: 检查任务是否已被取消 — 执行器tick入口检查 */
    public static boolean isCancelled(EntityMaid maid) {
        return TaskKeys.STATE_CANCELLED.equals(LmaTaskDataHelper.getFlowState(maid));
    }
}
