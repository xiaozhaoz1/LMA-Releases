package littlemaidmoreaction.littlemaidmoreaction.task.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;

/**
 * lma_task_* + lma_saved_* + adapter 通信标记 (v62).
 *
 * <p>目标/输入/完成/失败 + 状态保存恢复 + TLM/GUI 标记。
 */
public final class TaskMetaData {

    // ── 目标 ──

    public static String getTarget(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.TASK_TARGET);
    }

    public static void setTarget(EntityMaid maid, String target) {
        maid.getPersistentData().putString(TaskKeys.TASK_TARGET, target);
    }

    // ── 输入 ──

    public static String getInput(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.TASK_INPUT);
    }

    public static void setInput(EntityMaid maid, String input) {
        maid.getPersistentData().putString(TaskKeys.TASK_INPUT, input);
    }

    // ── 完成/失败 ──

    public static String getFailReason(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.FAIL_REASON);
    }

    public static void setFailReason(EntityMaid maid, String reason) {
        maid.getPersistentData().putString(TaskKeys.FAIL_REASON, reason);
    }

    // ── 状态保存/恢复 ──

    /** 保存女仆 home/pickup 状态到 NBT 备用 */
    public static void saveHomeMode(EntityMaid maid, boolean home, boolean pickup) {
        CompoundTag d = maid.getPersistentData();
        d.putBoolean(TaskKeys.SAVED_HOME, home);
        d.putBoolean(TaskKeys.SAVED_PICKUP, pickup);
    }

    /** 读取之前 saved 的 home 状态 */
    public static boolean readSavedHome(EntityMaid maid) {
        return maid.getPersistentData().getBoolean(TaskKeys.SAVED_HOME);
    }

    /** 读取之前 saved 的 pickup 状态 */
    public static boolean readSavedPickup(EntityMaid maid) {
        return maid.getPersistentData().getBoolean(TaskKeys.SAVED_PICKUP);
    }

    // ── adapter → task 通信标记 ──

    /** TLM 任务切换标记 — TlmTaskMonitor 写, TaskEngine 读 */
    public static String getTlmSwitch(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.TLM_SWITCH);
    }

    public static void setTlmSwitch(EntityMaid maid, String uid) {
        maid.getPersistentData().putString(TaskKeys.TLM_SWITCH, uid);
    }

    public static void clearTlmSwitch(EntityMaid maid) {
        maid.getPersistentData().remove(TaskKeys.TLM_SWITCH);
    }

    /** GUI 启动新任务标记 — LmaFlowCoordinationBehavior 写, TaskEngine 读 */
    public static String getGuiInit(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.GUI_INIT);
    }

    public static void setGuiInit(EntityMaid maid, String taskType) {
        maid.getPersistentData().putString(TaskKeys.GUI_INIT, taskType);
    }

    public static void clearGuiInit(EntityMaid maid) {
        maid.getPersistentData().remove(TaskKeys.GUI_INIT);
    }

    private TaskMetaData() {}
}
