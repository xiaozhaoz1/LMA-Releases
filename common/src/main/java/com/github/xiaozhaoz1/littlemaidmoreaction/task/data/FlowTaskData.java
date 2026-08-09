package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;

/**
 * lma_flow_* 核心流程数据读写 (v62: 替代 LmaTaskDataHelper).
 *
 * <p>封装 PersistentData 读写, 统一入口。所有 lma_flow_* 键的操作必须通过本类。
 */
public final class FlowTaskData {

    // ── 读取 ──

    public static String getTask(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.FLOW_TASK);
    }

    public static String getState(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.FLOW_STATE);
    }

    public static String getStep(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.FLOW_STEP);
    }

    public static long getTick(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.FLOW_TICK);
    }

    public static long getTimeout(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.FLOW_TIMEOUT);
    }

    public static long getMaxCount(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.FLOW_MAX_COUNT);
    }

    public static long getCounter(EntityMaid maid) {
        return maid.getPersistentData().getLong(TaskKeys.FLOW_COUNTER);
    }

    public static String getData(EntityMaid maid) {
        return maid.getPersistentData().getString(TaskKeys.FLOW_DATA);
    }

    // ── 写入 ──

    public static void setTask(EntityMaid maid, String taskType) {
        maid.getPersistentData().putString(TaskKeys.FLOW_TASK, taskType);
    }

    public static void setState(EntityMaid maid, String state) {
        maid.getPersistentData().putString(TaskKeys.FLOW_STATE, state);
    }

    public static void setStep(EntityMaid maid, String step) {
        maid.getPersistentData().putString(TaskKeys.FLOW_STEP, step);
    }

    public static void setTick(EntityMaid maid, long tick) {
        maid.getPersistentData().putLong(TaskKeys.FLOW_TICK, tick);
    }

    public static void setCounter(EntityMaid maid, long count) {
        maid.getPersistentData().putLong(TaskKeys.FLOW_COUNTER, count);
    }

    public static void setMaxCount(EntityMaid maid, long count) {
        maid.getPersistentData().putLong(TaskKeys.FLOW_MAX_COUNT, count);
    }

    public static void setTimeout(EntityMaid maid, long timeout) {
        maid.getPersistentData().putLong(TaskKeys.FLOW_TIMEOUT, timeout);
    }

    // ── 便捷操作 ──

    // ── 防抖工具 ──

    /** 检查是否在 tick 防抖窗口内 (距上次标记 ≤20 ticks) */
    public static boolean inTickWindow(CompoundTag data, String key, long now) {
        long t = data.getLong(key + "_tick");
        return t > 0 && now - t <= 20 && t <= now;
    }

    /** 标记当前 tick 到防抖键 */
    public static void markTick(CompoundTag data, String key, long now) {
        data.putLong(key + "_tick", now);
    }

    // ── 清理 ──

    /** 清除所有流程任务状态 (含 meta/saved/anim/jukebox 残留) */
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
        data.remove(TaskKeys.TASK_COMPLETED);
        data.remove(TaskKeys.FAIL_REASON);
        data.remove(TaskKeys.TASK_TARGET);
        data.remove(TaskKeys.TASK_INPUT);
        data.remove(TaskKeys.SAVED_HOME);
        data.remove(TaskKeys.SAVED_PICKUP);
        data.remove(TaskKeys.SAVED_HOME_POS);
        data.remove(TaskKeys.ANIM_MODE);
        data.remove(TaskKeys.ANIM_TICK);
        data.remove(TaskKeys.ANIM_DUR);
        data.remove(TaskKeys.ANIM_ID);
        data.remove(TaskKeys.ANIM_NAME);
        data.remove(TaskKeys.JUKEBOX_PHASE);
        data.remove(TaskKeys.JUKEBOX_TICK);
        data.remove(TaskKeys.JUKEBOX_LAST);
        // v75.4: 动画运行时键统一清理 — 原仅清 5 个 ANIM_* 主键, AnimExecute 写入的
        // 15 个运行时键 (seq/phase/start/casting/end/priority/lock_move/dur_*/wait_ticks) 全残留,
        // 跨 session 存活 (v67 #67 类 bug)。单一来源 = TaskKeys.ANIM_RUNTIME_KEYS。
        for (String key : TaskKeys.ANIM_RUNTIME_KEYS) {
            data.remove(key);
        }
    }

    private FlowTaskData() {}
}
