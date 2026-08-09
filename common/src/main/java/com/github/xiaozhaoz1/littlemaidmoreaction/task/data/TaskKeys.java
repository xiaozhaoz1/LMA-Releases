package com.github.xiaozhaoz1.littlemaidmoreaction.task.data;
import java.util.List;

/**
 * 任务 PersistentData Key 常量 (v35.1)。
 *
 * <p>统一管理所有 lma_flow_* / lma_task_* / lma_anim_* / lma_saved_* 键名。
 * 防止分散在 21 个文件中的硬编码字符串导致 #67 类跨 session 残留 bug。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * CompoundTag data = maid.getPersistentData();
 * data.putString(TaskKeys.FLOW_TASK, "craft_chain");
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

    // ── 动画运行时 (v75.4 标准化: AnimExecute 写入键收拢) ──
    public static final String ANIM_SEQ        = "lma_anim_seq";
    public static final String ANIM_PHASE      = "lma_anim_phase";
    public static final String ANIM_START      = "lma_anim_start";
    public static final String ANIM_CASTING    = "lma_anim_casting";
    public static final String ANIM_END        = "lma_anim_end";
    public static final String ANIM_PRIORITY   = "lma_anim_priority";
    public static final String LOCK_MOVE       = "lma_lock_move";
    public static final String DUR_START       = "lma_dur_start";
    public static final String DUR_CASTING     = "lma_dur_casting";
    public static final String DUR_END         = "lma_dur_end";
    /** 只写不读键 (v75.4 审计实证: 全 common 无读取方) — 随任务清理删除 */
    public static final String WAIT_TICKS      = "lma_wait_ticks";

    /** 动画运行时全部键 — clearAll 统一清理源 (防 v67 #67 类跨 session 残留) */
    public static final List<String> ANIM_RUNTIME_KEYS = List.of(
            ANIM_MODE, ANIM_TICK, ANIM_DUR, ANIM_ID, ANIM_NAME,
            ANIM_SEQ, ANIM_PHASE, ANIM_START, ANIM_CASTING, ANIM_END,
            ANIM_PRIORITY, LOCK_MOVE, DUR_START, DUR_CASTING, DUR_END, WAIT_TICKS);

    /** 客户端渲染清理键 = ANIM_RUNTIME_KEYS 去 ANIM_SEQ (SEQ 保留供新请求对比, 见 LmaMagicCastingProvider.cleanup) */
    public static final List<String> ANIM_CLEANUP_KEYS = List.of(
            ANIM_MODE, ANIM_TICK, ANIM_DUR, ANIM_ID, ANIM_NAME,
            ANIM_PHASE, ANIM_START, ANIM_CASTING, ANIM_END,
            ANIM_PRIORITY, LOCK_MOVE, DUR_START, DUR_CASTING, DUR_END, WAIT_TICKS);

    // ── 唱片机 ──
    public static final String JUKEBOX_PHASE = "lma_jukebox_phase";
    public static final String JUKEBOX_TICK  = "lma_jukebox_tick";
    public static final String JUKEBOX_LAST  = "lma_jukebox_last";

    // ── adapter→task 通信标记 (v49) ──
    /** TLM 任务切换标记 — TlmTaskMonitor 写，TaskTickHandler 读 (v64 迁入) */
    public static final String TLM_SWITCH = "lma_tlm_switch";
    /** GUI 启动新任务标记 — LmaFlowCoordinationBehavior 写，TaskTickHandler 读 (v64 迁入) */
    public static final String GUI_INIT = "lma_gui_init";

    // ── 任务开关 ──
    public static final String TASK_ENABLED_PREFIX = "lma_task_enabled_";

    // ── 被动任务 (v61) ──
    public static final String PASSIVE_PREFIX = "lma_passive_";
    public static String passiveKey(String taskType) { return PASSIVE_PREFIX + taskType; }

    // ── 环境感知 (v63) ──
    /** master开关 — 默认 false */
    public static final String ENVSENSE_ENABLED = "lma_envsense_enabled";

    // ── 机械臂 (v52→v63.2: 标准化) ──
    public static final String ARM_TAKE = "lma_arm_take";
    public static final String ARM_DEPOSIT = "lma_arm_deposit";
    public static final String ARM_ITEM = "lma_arm_item";

    // ── 任务切换 (v63.2) ──
    public static final String PREV_TASK = "lma_prev_task";
    public static final String AUTOCROP_ENABLED = "lma_autocrop_enabled";

    // ── 女仆属性 (v63.2) ──
    public static final String RESIST_PREFIX = "lma_resist_";

    // ── 熔炉 (v63.2) ──
    public static final String FURNACE_PHASE = "lma_furnace_phase";

    // ── 重试计数 (v53) ──
    public static final String RETRY_COUNT = "lma_retry_count";

    // ── 事件桥 (v72 Phase 4) ──
    /** MaidTick 信号节流时间戳 (per-maid) */
    public static final String TICK_LAST = "lma_tick_last";

    // ── 其他运行时键 (v75.4 标准化: 高频内联字面量收拢) ──
    /** 便携装配背包键 (MaidAssemblyInventory) */
    public static final String ASSEMBLY_INV       = "lma_assembly_inv";
    /** 任务进度气泡消息/时间戳 (ProgressNotifier) */
    public static final String BUBBLE_MSG         = "lma_bubble_msg";
    public static final String BUBBLE_TICK        = "lma_bubble_tick";
    /** BlockInteract 木棍绑定位置 (BlockInteractSetupHandler) */
    public static final String BIND_POS           = "lma_bind_pos";
    /** 女仆进食追踪 (OwnerFoodTracker) */
    public static final String LAST_FOOD          = "lma_last_food";
    /** 移动冻结剩余 ticks (MovementOutput) */
    public static final String FREEZE_TICKS       = "lma_freeze_ticks";
    /** 流血效果时间/伤害 (CombatOutput) */
    public static final String BLEED_TICKS        = "lma_bleed_ticks";
    public static final String BLEED_DMG          = "lma_bleed_dmg";
    /** 主人目标刷新节流 (TargetStateReader) */
    public static final String OWNER_TARGET_TICK  = "lma_owner_target_tick";
    /** 动态动画集开关 (StartupLoader) */
    public static final String DYNAMIC_ANIMATIONS = "lma_dynamic_animations";

    // v62: 任务类型常量已删除 — 每个 Pipeline 的 taskType() 是正源

    private TaskKeys() {}
}
