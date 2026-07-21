package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import net.minecraft.nbt.CompoundTag;

/**
 * FlowTask 数据读写便捷封装 (v42)。
 *
 * <p>封装 PersistentData 读写，统一入口。替代分散在各处的 {@code maid.getPersistentData().getString("lma_flow_*")} 调用。
 * 内部仍用 PersistentData（兼容 24 个现有文件），未来可透明切换至 TaskDataKey。
 *
 * <h3>v42 新增</h3>
 * <ul>
 *   <li>{@link #startFlowTask} — 启动流程任务（替代6处手动NBT写入）</li>
 *   <li>{@link #initFull} — 8字段完整初始化（替代StartTaskTool/Action的手动写入）</li>
 *   <li>{@link #getFlowStep}/{@link #setFlowStep} — FLOW_STEP读写</li>
 *   <li>{@link #getFlowMaxCount}/{@link #setFlowMaxCount} — FLOW_MAX_COUNT读写</li>
 * </ul>
 */
public final class LmaTaskDataHelper {

    // ── 读取 ──

    public static String getFlowTask(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.FLOW_TASK);
    }

    public static String getFlowState(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.FLOW_STATE);
    }

    public static String getFlowStep(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.FLOW_STEP);
    }

    public static long getFlowTick(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.FLOW_TICK);
    }

    public static long getFlowTimeout(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.FLOW_TIMEOUT);
    }

    public static long getFlowMaxCount(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.FLOW_MAX_COUNT);
    }

    public static String getFlowData(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.FLOW_DATA);
    }

    // ── 写入 ──

    public static void setFlowTask(EntityMaid maid, String taskType) {
        maid.getPersistentData().putString(TaskKeys.FLOW_TASK, taskType);
    }

    public static void setFlowState(EntityMaid maid, String state) {
        maid.getPersistentData().putString(TaskKeys.FLOW_STATE, state);
    }

    public static void setFlowStep(EntityMaid maid, String step) {
        maid.getPersistentData().putString(TaskKeys.FLOW_STEP, step);
    }

    public static void setFlowTick(EntityMaid maid, long tick) {
        maid.getPersistentData().putLong(TaskKeys.FLOW_TICK, tick);
    }

    public static void setFlowCounter(EntityMaid maid, long count) {
        maid.getPersistentData().putLong(TaskKeys.FLOW_COUNTER, count);
    }

    /** v44: 读取重试计数器 — 与 setFlowCounter 对称 */
    public static long getFlowCounter(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.FLOW_COUNTER);
    }

    public static void setFlowMaxCount(EntityMaid maid, long count) {
        maid.getPersistentData().putLong(TaskKeys.FLOW_MAX_COUNT, count);
    }

    public static void setFlowTimeout(EntityMaid maid, long timeout) {
        maid.getPersistentData().putLong(TaskKeys.FLOW_TIMEOUT, timeout);
    }

    // ── 便捷操作 ──

    /**
     * 启动流程任务 — 写三字段 + 切换女仆Brain。
     * 替代6处重复的 NBT三字段写入模式。
     */
    public static void startFlowTask(EntityMaid maid, String taskType) {
        maid.getPersistentData().putString(TaskKeys.FLOW_TASK, taskType);
        maid.getPersistentData().putString(TaskKeys.FLOW_STATE, TaskKeys.STATE_IN_PROGRESS);
        maid.getPersistentData().putLong(TaskKeys.FLOW_TICK, maid.level().getGameTime());
        maid.setTask(LmaTaskTypeRegistry.findByTaskType(taskType));
    }

    /**
     * 完整任务初始化 — 8字段写入（替代 StartTaskTool/StartTaskAction 的手动写入）。
     */
    public static void initFull(EntityMaid maid, String taskType, String taskId, long targetCount) {
        long now = maid.level().getGameTime();
        CompoundTag data = maid.getPersistentData();
        data.putString(TaskKeys.FLOW_TASK, taskType);
        data.putString(TaskKeys.FLOW_TASK_ID, taskId);
        data.putString(TaskKeys.FLOW_STATE, TaskKeys.STATE_IN_PROGRESS);
        data.putString(TaskKeys.FLOW_STEP, "");
        data.putLong(TaskKeys.FLOW_MAX_COUNT, targetCount);
        data.putLong(TaskKeys.FLOW_COUNTER, 0);
        data.putLong(TaskKeys.FLOW_TICK, now);
        data.remove(TaskKeys.FLOW_CACHED);
    }

    // ── 防抖工具 (v42) ──

    /**
     * 检查是否在 tick 防抖窗口内（距上次标记 ≤20 ticks）。
     * 替代 IsOwnerTarget/IsOwnerAttacker/IsMainhandAttack 中的重复防抖逻辑。
     */
    public static boolean isInTickWindow(CompoundTag data, String key, long now) {
        long t = data.getLong(key + "_tick");
        return t > 0 && now - t <= 20 && t <= now;
    }

    /** 标记当前 tick 到防抖键 */
    public static void markTick(CompoundTag data, String key, long now) {
        data.putLong(key + "_tick", now);
    }

    // ── 清理 ──

    /** 清除所有流程任务状态 */
    /** 清除所有流程任务状态 (v43: 补全 TASK_COMPLETED/FAIL_REASON/TASK_TARGET/SAVED_*) */
    public static void clearAll(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        data.remove(TaskKeys.FLOW_TASK);
        data.remove(TaskKeys.FLOW_TASK_ID);
        data.remove(TaskKeys.FLOW_STATE);
        data.remove(TaskKeys.FLOW_STEP);
        data.remove(TaskKeys.FLOW_COUNTER);
        data.remove(TaskKeys.FLOW_MAX_COUNT);
        data.remove(TaskKeys.FLOW_TICK);
        data.remove(TaskKeys.FLOW_TIMEOUT);
        data.remove(TaskKeys.FLOW_DATA);
        data.remove(TaskKeys.FLOW_CACHED);
        // v43: 补全 — 防止跨session残留
        data.remove(TaskKeys.TASK_COMPLETED);
        data.remove(TaskKeys.FAIL_REASON);
        data.remove(TaskKeys.TASK_TARGET);
        data.remove(TaskKeys.SAVED_HOME);
        data.remove(TaskKeys.SAVED_PICKUP);
        data.remove(TaskKeys.SAVED_HOME_POS);
        // v44: 动画/唱片机/输入残留清理
        data.remove(TaskKeys.ANIM_MODE);
        data.remove(TaskKeys.ANIM_TICK);
        data.remove(TaskKeys.ANIM_DUR);
        data.remove(TaskKeys.ANIM_ID);
        data.remove(TaskKeys.ANIM_NAME);
        data.remove(TaskKeys.JUKEBOX_PHASE);
        data.remove(TaskKeys.JUKEBOX_TICK);
        data.remove(TaskKeys.JUKEBOX_LAST);
        data.remove("lma_task_input"); // v45 TODO: migrate to TaskKeys.TASK_INPUT
    }

    private LmaTaskDataHelper() {}
}
