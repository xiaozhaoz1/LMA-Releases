package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;

/**
 * lma_flow_* 核心流程数据读写 (v62: 替代 LmaTaskDataHelper).
 *
 * <p>封装 PersistentData 读写, 统一入口。所有 lma_flow_* 键的操作必须通过本类。
 * v79.30: 内部实现收编到 {@link MaidData} 类型化 API ({@link DataKey}), 公开签名不变。
 */
public final class FlowTaskData {

    // ── 读取 ──

    public static String getTask(EntityMaid maid) {
        return MaidData.get(maid, DataKey.FLOW_TASK);
    }

    public static String getState(EntityMaid maid) {
        return MaidData.get(maid, DataKey.FLOW_STATE);
    }

    public static long getTick(EntityMaid maid) {
        return MaidData.get(maid, DataKey.FLOW_TICK);
    }

    public static long getMaxCount(EntityMaid maid) {
        return MaidData.get(maid, DataKey.FLOW_MAX_COUNT);
    }

    public static long getCounter(EntityMaid maid) {
        return MaidData.get(maid, DataKey.FLOW_COUNTER);
    }

    // ── 写入 ──

    public static void setTask(EntityMaid maid, String taskType) {
        MaidData.put(maid, DataKey.FLOW_TASK, taskType);
    }

    public static void setState(EntityMaid maid, String state) {
        MaidData.put(maid, DataKey.FLOW_STATE, state);
    }

    public static void setTick(EntityMaid maid, long tick) {
        MaidData.put(maid, DataKey.FLOW_TICK, tick);
    }

    public static void setCounter(EntityMaid maid, long count) {
        MaidData.put(maid, DataKey.FLOW_COUNTER, count);
    }

    public static void setMaxCount(EntityMaid maid, long count) {
        MaidData.put(maid, DataKey.FLOW_MAX_COUNT, count);
    }

    // setMaxCount/setStep/getStep/getTimeout 已删 (v79.55, 错题 #181): 零调用方死门面 —
    // FLOW_MAX_COUNT 由 TaskDispatcher:81 直写; FLOW_TIMEOUT 键无写方恒默认超时 (GMPM 恒 DEFAULT_TIMEOUT)

    // ── 清理 ──

    /**
     * 清除所有流程任务状态 (含 meta/saved/anim/jukebox 残留)。
     * 键表驱动 — {@link DataKey#CLEAR_ALL_KEYS} 遍历 remove (消除手写清单双源漂移,
     * 新增 DataKey 键只需加进 CLEAR_ALL_KEYS 即自动覆盖); 动画运行时键 + FLOW_CACHED 走字面量。
     */
    public static void clearAll(EntityMaid maid) {
        CompoundTag data = MaidData.root(maid);
        for (DataKey<?> k : DataKey.CLEAR_ALL_KEYS) {
            data.remove(k.key());
        }
        // 无 DataKey 的残留键 (旧缓存字段/无 DataKey 常量键, 随任务终结一并清)
        data.remove(TaskKeys.FLOW_CACHED);
        data.remove(TaskKeys.FLOW_TASK_ID);
        data.remove(TaskKeys.TASK_COMPLETED);
        data.remove(TaskKeys.SAVED_HOME_POS);
        // 死键删后旧存档残留清理 (键常量已删, 字面量集中此处兜底 — 零散落)
        data.remove("lma_retry_count");
        data.remove("lma_saved_home");
        data.remove("lma_saved_pickup");
        data.remove("lma_jukebox_last");
        data.remove("lma_weapon_anim");
        data.remove("lma_last_emoji_tick");
        data.remove("lma_freeze_ticks");
        data.remove("lma_bleed_ticks");
        data.remove("lma_bleed_dmg");
        // v79.55 死键旧存档残留清理 (FLOW_TIMEOUT/TASK_INPUT/FAIL_REASON/ANIM_TIME/FLOW_STEP) + v79.61 批2 FLOW_DATA
        data.remove("lma_flow_timeout");
        data.remove("lma_task_input");
        data.remove("lma_fail_reason");
        data.remove("lma_anim_time");
        data.remove("lma_flow_step");
        data.remove("lma_flow_data");
        // v79.61x S1 相位机迁移: JUKEBOX_PHASE/FURNACE_PHASE 键常量删 (状态入 FSM 内存态) — 旧存档残留字面量兜底
        data.remove("lma_jukebox_phase");
        data.remove("lma_furnace_phase");
        // 动画运行时键统一清理 — 原仅清 5 个 ANIM_* 主键, AnimExecute 写入的
        // 15 个运行时键 (seq/phase/start/casting/end/priority/lock_move/dur_*/wait_ticks) 全残留,
        // 跨 session 存活 (错题 #67 类 bug)。单一来源 = TaskKeys.ANIM_RUNTIME_KEYS。
        for (String key : TaskKeys.ANIM_RUNTIME_KEYS) {
            data.remove(key);
        }
    }

    private FlowTaskData() {}
}
