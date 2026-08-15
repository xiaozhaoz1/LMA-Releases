package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;

/**
 * 类型化数据键 (v79.30 Phase 2 — TLM TaskDataKey 参考, LMA 简化版: 静态键对象 + 类型参数,
 * 不做注册表+Codec — Codec 对基础类型无收益)。
 *
 * <p>key 引用 {@link TaskKeys} 常量 (零重复字面量); 类型由 {@link #type()} 声明,
 * {@link MaidData#get}/{@link MaidData#put} 按类型分发读写 root CompoundTag。
 * 编译期泛型 + 静态键表保证键-类型一致。
 *
 * <p>仅 PL 分区 (lma_pl_&lt;type&gt;, 动态 taskType) 不走本键表 — 用
 * {@link com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable#pipelineData}。
 */
public record DataKey<T>(String key, DataType type, T def) {

    public enum DataType { STRING, INT, LONG, BOOLEAN, COMPOUND }

    // ── FLOW 区 (lma_flow_*, 任务状态) ──

    public static final DataKey<String> FLOW_TASK = new DataKey<>(TaskKeys.FLOW_TASK, DataType.STRING, "");
    public static final DataKey<String> FLOW_STATE = new DataKey<>(TaskKeys.FLOW_STATE, DataType.STRING, "");
    public static final DataKey<Long> FLOW_TICK = new DataKey<>(TaskKeys.FLOW_TICK, DataType.LONG, 0L);
    public static final DataKey<Long> FLOW_COUNTER = new DataKey<>(TaskKeys.FLOW_COUNTER, DataType.LONG, 0L);
    public static final DataKey<Long> FLOW_MAX_COUNT = new DataKey<>(TaskKeys.FLOW_MAX_COUNT, DataType.LONG, 0L);

    // ── META 区 (lma_task_* / lma_saved_* / adapter 标记) ──

    public static final DataKey<String> TASK_TARGET = new DataKey<>(TaskKeys.TASK_TARGET, DataType.STRING, "");
    public static final DataKey<String> TLM_SWITCH = new DataKey<>(TaskKeys.TLM_SWITCH, DataType.STRING, "");
    public static final DataKey<String> GUI_INIT = new DataKey<>(TaskKeys.GUI_INIT, DataType.STRING, "");

    // ── ANIM 区 (lma_anim_* 运行时键) ──

    public static final DataKey<String> ANIM_MODE = new DataKey<>(TaskKeys.ANIM_MODE, DataType.STRING, "");
    public static final DataKey<String> ANIM_NAME = new DataKey<>(TaskKeys.ANIM_NAME, DataType.STRING, "");
    public static final DataKey<Integer> ANIM_SEQ = new DataKey<>(TaskKeys.ANIM_SEQ, DataType.INT, 0);
    public static final DataKey<String> ANIM_PHASE = new DataKey<>(TaskKeys.ANIM_PHASE, DataType.STRING, "");
    public static final DataKey<String> ANIM_START = new DataKey<>(TaskKeys.ANIM_START, DataType.STRING, "");
    public static final DataKey<String> ANIM_CASTING = new DataKey<>(TaskKeys.ANIM_CASTING, DataType.STRING, "");
    public static final DataKey<String> ANIM_END = new DataKey<>(TaskKeys.ANIM_END, DataType.STRING, "");
    public static final DataKey<Boolean> LOCK_MOVE = new DataKey<>(TaskKeys.LOCK_MOVE, DataType.BOOLEAN, false);

    // ── MISC 区 (散键收编) ──

    public static final DataKey<Boolean> AI_CONTROL = new DataKey<>(TaskKeys.AI_CONTROL, DataType.BOOLEAN, false);
    public static final DataKey<Integer> JUKEBOX_PHASE = new DataKey<>(TaskKeys.JUKEBOX_PHASE, DataType.INT, 0);
    public static final DataKey<Long> JUKEBOX_TICK = new DataKey<>(TaskKeys.JUKEBOX_TICK, DataType.LONG, 0L);
    public static final DataKey<Integer> FURNACE_PHASE = new DataKey<>(TaskKeys.FURNACE_PHASE, DataType.INT, 0);
    // 3 个 COMPOUND 默认值在 <clinit> 实例化 new CompoundTag() — 纯 JVM 实测安全:
    // CompoundTag 是纯 NBT 数据结构, 无注册表依赖 (DataKeyConsistencyTest 现网直接加载
    // 本类触发 clinit 全绿实证; 与 Items.*/BuiltInRegistries 等注册表类不同, 不触发 MC bootstrap)
    public static final DataKey<net.minecraft.nbt.CompoundTag> ARM_TAKE = new DataKey<>(TaskKeys.ARM_TAKE, DataType.COMPOUND, new net.minecraft.nbt.CompoundTag());
    public static final DataKey<net.minecraft.nbt.CompoundTag> ARM_DEPOSIT = new DataKey<>(TaskKeys.ARM_DEPOSIT, DataType.COMPOUND, new net.minecraft.nbt.CompoundTag());
    public static final DataKey<String> ARM_ITEM = new DataKey<>(TaskKeys.ARM_ITEM, DataType.STRING, "");
    public static final DataKey<net.minecraft.nbt.CompoundTag> ASSEMBLY_INV = new DataKey<>(TaskKeys.ASSEMBLY_INV, DataType.COMPOUND, new net.minecraft.nbt.CompoundTag());

    // 动态前缀键 (PASSIVE_PREFIX / RESIST_PREFIX / PL_PREFIX / CFG_PREFIX)
    // — 运行时拼接, 不走本键表 (用 MaidData.root 直读或专用 API)

    // ── 终结清理集合 (FlowTaskData.clearAll 键表驱动 — 消除手写清单双源漂移) ──

    /**
     * 任务终结清理键 — clearAll 遍历 remove。
     * 收敛为在用的 DataKey; 已删 DataKey 的清理面: ANIM 运行时键由
     * TaskKeys.ANIM_RUNTIME_KEYS 覆盖 (clearAll 内), FLOW_TASK_ID/TASK_COMPLETED/SAVED_HOME_POS
     * 与已删死键 (9 个: lma_retry_count/lma_saved_home/lma_saved_pickup/lma_jukebox_last/
     * lma_weapon_anim/lma_last_emoji_tick/lma_freeze_ticks/lma_bleed_ticks/lma_bleed_dmg
     * + v79.55 删: FLOW_TIMEOUT/FAIL_REASON/TASK_INPUT/ANIM_TIME/FLOW_STEP; v79.61 批2: FLOW_DATA)
     * 由 FlowTaskData.clearAll 字面量补 (旧存档残留清理)。
     */
    public static final java.util.List<DataKey<?>> CLEAR_ALL_KEYS = java.util.List.of(
            FLOW_TASK, FLOW_STATE, FLOW_TICK, FLOW_COUNTER,
            FLOW_MAX_COUNT,
            TASK_TARGET,
            ANIM_MODE, ANIM_NAME, ANIM_SEQ, ANIM_PHASE,
            ANIM_START, ANIM_CASTING, ANIM_END, LOCK_MOVE,
            FURNACE_PHASE,
            JUKEBOX_PHASE, JUKEBOX_TICK);
}
