package littlemaidmoreaction.littlemaidmoreaction.task;

/**
 * 任务 PersistentData Key 常量 (v35.1)。
 *
 * <p>统一管理所有 lma_flow_* / lma_task_* / lma_anim_* / lma_saved_* 键名。
 * 防止分散在 21 个文件中的硬编码字符串导致 #67 类跨 session 残留 bug。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * CompoundTag data = maid.getPersistentData();
 * data.putString(TaskKeys.FLOW_TASK, "altar_craft");
 * data.putString(TaskKeys.FLOW_STATE, TaskKeys.STATE_IN_PROGRESS);
 * }</pre>
 */
public final class TaskKeys {

    // ── 流程任务核心 ──
    public static final String FLOW_TASK      = "lma_flow_task";
    public static final String FLOW_TASK_ID   = "lma_flow_task_id";
    public static final String FLOW_STATE     = "lma_flow_state";
    public static final String FLOW_STEP      = "lma_flow_step";
    public static final String FLOW_TICK      = "lma_flow_tick";
    public static final String FLOW_COUNTER   = "lma_flow_counter";
    public static final String FLOW_MAX_COUNT = "lma_flow_max_count";
    public static final String FLOW_TIMEOUT   = "lma_flow_timeout";
    public static final String FLOW_DATA      = "lma_flow_data";
    public static final String FLOW_CACHED    = "lma_flow_cached";

    // ── 状态值 ──
    public static final String STATE_IN_PROGRESS = "in_progress";
    public static final String STATE_COMPLETED   = "completed";
    public static final String STATE_FAILED      = "failed";
    public static final String STATE_STOPPED     = "stopped";
    public static final String STATE_QUEUED      = "queued";
    public static final String STATE_CANCELLED   = "cancelled";

    // ── 任务目标/反馈 ──
    public static final String TASK_TARGET    = "lma_task_target";
    public static final String TASK_INPUT     = "lma_task_input";
    public static final String TASK_COMPLETED = "lma_task_completed";
    public static final String FAIL_REASON    = "lma_fail_reason";

    // ── 状态保存/恢复 ──
    public static final String SAVED_HOME     = "lma_saved_home";
    public static final String SAVED_PICKUP   = "lma_saved_pickup";
    public static final String SAVED_HOME_POS = "lma_saved_home_pos";

    // ── 动画 ──
    public static final String ANIM_MODE     = "lma_anim_mode";
    public static final String ANIM_TICK     = "lma_anim_tick";
    public static final String ANIM_DUR      = "lma_anim_dur";
    public static final String ANIM_ID       = "lma_anim_id";
    public static final String ANIM_NAME     = "lma_anim";

    // ── 唱片机 ──
    public static final String JUKEBOX_PHASE = "lma_jukebox_phase";
    public static final String JUKEBOX_TICK  = "lma_jukebox_tick";
    public static final String JUKEBOX_LAST  = "lma_jukebox_last";

    // ── adapter→task 通信标记 (v49) ──
    /** TLM 任务切换标记 — TlmTaskMonitor 写，TaskEngine 读 */
    public static final String TLM_SWITCH = "lma_tlm_switch";
    /** GUI 启动新任务标记 — LmaFlowCoordinationBehavior 写，TaskEngine 读 */
    public static final String GUI_INIT = "lma_gui_init";

    // ── 任务开关 ──
    public static final String TASK_ENABLED_PREFIX = "lma_task_enabled_";

    // ── 任务类型常量 (v42) ──
    public static final String TASK_CRAFT_CHAIN  = "craft_chain";
    public static final String TASK_FURNACE      = "furnace";
    public static final String TASK_JUKEBOX      = "jukebox";
    public static final String TASK_BELL_RING    = "bell_ring";
    public static final String TASK_ARM_TRANSFER = "arm_transfer";
    public static final String TASK_CRANK        = "crank";
    public static final String TASK_POWER        = "power";
    public static final String TASK_PRESS        = "press";
    public static final String TASK_MIX          = "mix";
    public static final String TASK_RUNNING_BELT = "running_belt";

    private TaskKeys() {}
}
