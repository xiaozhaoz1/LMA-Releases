package littlemaidmoreaction.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.task.LmaTaskDataHelper;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskKeys;
import net.minecraft.nbt.CompoundTag;

/**
 * @deprecated v43: 由 {@link littlemaidmoreaction.littlemaidmoreaction.task.TaskStateManager} 替代。
 * 新代码使用 TaskStateManager + TaskDispatcher。旧调用方逐步迁移。
 *
 * <p>v41: clearAll() 委托到 LmaTaskDataHelper，消除手动 NBT remove 的分散调用。
 *
 * <h3>心跳协议</h3>
 * TaskEngine 以 FLOW_TICK 距今超过 FLOW_TIMEOUT(默认1200t) 判定超时。
 * 活跃执行器必须周期调 {@link #heartbeat} — 活着的任务永不被超时杀。
 */
@Deprecated
public final class TaskStateService {

    private TaskStateService() {}

    /** GUI-init: 初始化/覆盖流程任务状态 */
    public static void init(EntityMaid maid, String taskType, long now) {
        CompoundTag data = maid.getPersistentData();
        data.putString(TaskKeys.FLOW_TASK, taskType);
        data.putString(TaskKeys.FLOW_STATE, TaskKeys.STATE_IN_PROGRESS);
        data.putLong(TaskKeys.FLOW_TICK, now);
    }

    /** 心跳: 刷新活跃时间戳 — 防 TaskEngine 超时误杀 */
    public static void heartbeat(EntityMaid maid, long now) {
        maid.getPersistentData().putLong(TaskKeys.FLOW_TICK, now);
    }

    /** 完成: state=completed + 记录完成任务名（LmaStatusContext 消费） */
    public static void complete(EntityMaid maid, long now) {
        CompoundTag data = maid.getPersistentData();
        data.putString(TaskKeys.FLOW_STATE, TaskKeys.STATE_COMPLETED);
        data.putString(TaskKeys.TASK_COMPLETED, data.getString(TaskKeys.FLOW_TASK));
        data.putLong(TaskKeys.FLOW_TICK, now);
        data.remove(TaskKeys.FLOW_CACHED);
    }

    /** 失败: state=failed + 原因（注意: 链式任务应待机而非 fail — 见执行器空闲扫描） */
    public static void fail(EntityMaid maid, String reason, long now) {
        CompoundTag data = maid.getPersistentData();
        data.putString(TaskKeys.FLOW_STATE, TaskKeys.STATE_FAILED);
        data.putLong(TaskKeys.FLOW_TICK, now);
        data.remove(TaskKeys.FLOW_CACHED);
        data.putString(TaskKeys.FAIL_REASON, reason);
    }

    /** 全清 — v41: 委托到 LmaTaskDataHelper（统一清理入口） */
    public static void clearAll(EntityMaid maid) {
        LmaTaskDataHelper.clearAll(maid);
    }
}
