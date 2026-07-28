package littlemaidmoreaction.littlemaidmoreaction.task.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * 动画/唱片机/开关/被动/重试 数据读写 (v62).
 */
public final class TaskExtraData {

    // ── 动画 ──

    public static String getAnimMode(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.ANIM_MODE);
    }

    public static void setAnimMode(EntityMaid maid, String mode) {
        maid.getPersistentData().putString(TaskKeys.ANIM_MODE, mode);
    }

    public static long getAnimTick(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.ANIM_TICK);
    }

    public static void setAnimTick(EntityMaid maid, long tick) {
        maid.getPersistentData().putLong(TaskKeys.ANIM_TICK, tick);
    }

    public static long getAnimDur(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.ANIM_DUR);
    }

    public static void setAnimDur(EntityMaid maid, long dur) {
        maid.getPersistentData().putLong(TaskKeys.ANIM_DUR, dur);
    }

    public static String getAnimId(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.ANIM_ID);
    }

    public static void setAnimId(EntityMaid maid, String id) {
        maid.getPersistentData().putString(TaskKeys.ANIM_ID, id);
    }

    public static String getAnimName(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.ANIM_NAME);
    }

    public static void setAnimName(EntityMaid maid, String name) {
        maid.getPersistentData().putString(TaskKeys.ANIM_NAME, name);
    }

    // ── 唱片机 ──

    public static String getJukeboxPhase(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.JUKEBOX_PHASE);
    }

    public static void setJukeboxPhase(EntityMaid maid, String phase) {
        maid.getPersistentData().putString(TaskKeys.JUKEBOX_PHASE, phase);
    }

    public static long getJukeboxTick(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.JUKEBOX_TICK);
    }

    public static void setJukeboxTick(EntityMaid maid, long tick) {
        maid.getPersistentData().putLong(TaskKeys.JUKEBOX_TICK, tick);
    }

    public static String getJukeboxLast(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.JUKEBOX_LAST);
    }

    public static void setJukeboxLast(EntityMaid maid, String last) {
        maid.getPersistentData().putString(TaskKeys.JUKEBOX_LAST, last);
    }

    // ── 任务开关 ──

    /** 全局开关 + per-maid NBT: 两个都通过才启用 */
    public static boolean isEnabledFor(EntityMaid maid, String taskType) {
        return TaskToggle.isEnabled(taskType)
            && !maid.getPersistentData().getBoolean(TaskKeys.TASK_ENABLED_PREFIX + taskType);
    }

    public static void setEnabledFor(EntityMaid maid, String taskType, boolean enabled) {
        maid.getPersistentData().putBoolean(TaskKeys.TASK_ENABLED_PREFIX + taskType, !enabled);
    }

    // ── 被动任务 ──

    public static boolean isPassiveActive(EntityMaid maid, String taskType) {
        return TaskKeys.STATE_IN_PROGRESS.equals(
            maid.getPersistentData().getString(TaskKeys.passiveKey(taskType)));
    }

    public static void setPassiveActive(EntityMaid maid, String taskType) {
        maid.getPersistentData().putString(TaskKeys.passiveKey(taskType), TaskKeys.STATE_IN_PROGRESS);
    }

    public static void clearPassive(EntityMaid maid, String taskType) {
        maid.getPersistentData().remove(TaskKeys.passiveKey(taskType));
    }

    // ── 重试 ──

    public static int getRetryCount(EntityMaid maid) {
        return maid.getPersistentData().getInt(TaskKeys.RETRY_COUNT);
    }

    public static void setRetryCount(EntityMaid maid, int count) {
        maid.getPersistentData().putInt(TaskKeys.RETRY_COUNT, count);
    }

    public static void clearRetryCount(EntityMaid maid) {
        maid.getPersistentData().remove(TaskKeys.RETRY_COUNT);
    }

    private TaskExtraData() {}
}
