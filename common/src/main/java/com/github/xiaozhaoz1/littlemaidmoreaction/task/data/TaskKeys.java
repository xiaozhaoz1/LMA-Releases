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

    /**
     * **通用方块标记槽** (木棍 NBT, NbtCodecs blockPos) — 木棍右键**非容器方块**即写入。
     *
     * <p>v79.63 (用户裁定): 原先各任务 handler 各写各的键 ({@code void_start}/{@code lma_bind_pos}) ⇒ 相互抢键,
     * "标记了却说没标记"。现统一到本键, 各任务**只读不抢写**; 旧键仅作兼容读 (§兼容)。
     * 容器(箱子)不走此处 — 右键容器开角色菜单, 见 {@code FarmContainerScreen} + {@code FarmContainerBindPacket}。
     */
    public static final String MARK1 = "lma_mark1";


    /**
     * 区域任务起始点键 ({@code pipelineConfig} 内, NbtCodecs blockPos) — **dam_fill 与 void_excavation 共用**。
     *
     * <p>v79.63: 原本两个管线各自硬编码 {@code "start"} (per-task 键守护发现) ⇒ 收敛到此, 改名即静默失效的风险消除。
     */
    public static final String CFG_START = "start";

    // ── 流程任务核心 ──
    public static final String FLOW_TASK      = "lma_flow_task";
    public static final String FLOW_TASK_ID   = "lma_flow_task_id";
    public static final String FLOW_STATE     = "lma_flow_state";
    public static final String FLOW_TICK      = "lma_flow_tick";
    public static final String FLOW_COUNTER   = "lma_flow_counter";
    public static final String FLOW_MAX_COUNT = "lma_flow_max_count";
    public static final String FLOW_CACHED    = "lma_flow_cached";

    // ── 状态值 ──
    public static final String STATE_IN_PROGRESS = "in_progress";
    public static final String STATE_COMPLETED   = "completed";
    public static final String STATE_FAILED      = "failed";
    public static final String STATE_CANCELLED   = "cancelled";

    // ── 任务目标/反馈 ──
    public static final String TASK_TARGET    = "lma_task_target";
    public static final String TASK_COMPLETED = "lma_task_completed";

    // ── 状态保存/恢复 ──
    public static final String SAVED_HOME_POS = "lma_saved_home_pos";

    // ── 动画 ──
    public static final String ANIM_MODE     = "lma_anim_mode";
    public static final String ANIM_TICK     = "lma_anim_tick";
    public static final String ANIM_DUR      = "lma_anim_dur";
    public static final String ANIM_NAME     = "lma_anim";

    // ── 动画运行时 (标准化: AnimExecute 写入键收拢) ──
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

    /** 动画运行时全部键 — clearAll 统一清理源 (防错题 #67 类跨 session 残留) */
    public static final List<String> ANIM_RUNTIME_KEYS = List.of(
            ANIM_MODE, ANIM_TICK, ANIM_DUR, ANIM_NAME,
            ANIM_SEQ, ANIM_PHASE, ANIM_START, ANIM_CASTING, ANIM_END,
            ANIM_PRIORITY, LOCK_MOVE, DUR_START, DUR_CASTING, DUR_END);

    /** 客户端渲染清理键 = ANIM_RUNTIME_KEYS 去 ANIM_SEQ (SEQ 保留供新请求对比, 见 LmaMagicCastingProvider.cleanup) */
    public static final List<String> ANIM_CLEANUP_KEYS = List.of(
            ANIM_MODE, ANIM_TICK, ANIM_DUR, ANIM_NAME,
            ANIM_PHASE, ANIM_START, ANIM_CASTING, ANIM_END,
            ANIM_PRIORITY, LOCK_MOVE, DUR_START, DUR_CASTING, DUR_END);

    // ── 唱片机 ──
    public static final String JUKEBOX_TICK  = "lma_jukebox_tick";

    // ── adapter→task 通信标记 ──
    /** TLM 任务切换标记 — TlmTaskMonitor 写，TaskTickHandler 读 (值 = 完整 RL, 错题 #179 契约) */
    public static final String TLM_SWITCH = "lma_tlm_switch";
    /** GUI 启动新任务标记 — LmaFlowCoordinationBehavior 写，TaskTickHandler 读 */
    public static final String GUI_INIT = "lma_gui_init";

    // ── 被动任务 ──
    public static final String PASSIVE_PREFIX = "lma_passive_";
    public static String passiveKey(String taskType) { return PASSIVE_PREFIX + taskType; }

    // ── 环境感知 ──
    /** master开关 — 默认 false */
    public static final String ENVSENSE_ENABLED = "lma_envsense_enabled";

    // ── 机械臂 (标准化) ──
    public static final String ARM_TAKE = "lma_arm_take";
    public static final String ARM_DEPOSIT = "lma_arm_deposit";
    public static final String ARM_ITEM = "lma_arm_item";
    // v79.63 命名评审 D-1: 删死键 FARM_SEED_CONT / FARM_HARVEST_CONT ("lma_farm_seed_cont" /
    //   "lma_farm_harvest_cont") — v79.62 容器从「女仆 PD 全局」迁移到「per-region (FarmRegion
    //   record 字段)」后, 这两个键全源集零引用 (名与字面量双向 grep 实证), 属迁移遗留。

    // ── 女仆属性 ──
    public static final String RESIST_PREFIX = "lma_resist_";

    // ── 其他运行时键 (标准化: 高频内联字面量收拢) ──
    /** 便携装配背包键 (MaidAssemblyInventory) */
    public static final String ASSEMBLY_INV       = "lma_assembly_inv";
    /** BlockInteract 木棍绑定位置 (BlockInteractSetupHandler) */
    public static final String BIND_POS           = "lma_bind_pos";
    /** 女仆进食追踪 (OwnerFoodTracker) */
    public static final String LAST_FOOD          = "lma_last_food";

    // ── 散键收编 (Explore 实证 TaskKeys 外键) ──

    /** AI 操控权限标记 (AiControlGate) */
    public static final String AI_CONTROL = "lma_ai_control";
    /** 气泡节流时间戳 (MaidChatBubbleApi) */
    public static final String BUBBLE_FAIL_TICK = "lma_bubble_fail_tick";
    public static final String BUBBLE_TRIGGER_TICK = "lma_bubble_trigger_tick";
    /** 气泡全局公共 CD (2026-08-16 用户裁定: 公共 CD 3s — 同女仆任意气泡间至少隔 3 秒, 同时只有 1 格) */
    public static final String BUBBLE_GLOBAL_TICK = "lma_bubble_global_tick";
    /** 连锁采集队列/蓄力结束 (ChainHarvestExecute) */
    public static final String CHAIN_QUEUE = "lma_chain_queue";
    public static final String CHAIN_CHARGE_END = "lma_chain_charge_end";
    /** 连锁采集相位 (v79.61x 状态机化: SCAN/CHARGE — 入队单点写 CHARGE, 队列闭环单点清;
     *  仅 phaseOf() 读, 用于旧档兼容判据: 无 phase 键时队列存在 = CHARGE) */
    public static final String CHAIN_PHASE = "lma_chain_phase";
    /** 节日跨天去重 (FestivalPassiveTask, v79.61x 脱管线) */
    public static final String FESTIVAL_DAY = "lma_festival_day";
    /** 稀有群系通报去重: 上次通报群系 (biomeId) — 每群系一次 (RareBiomePassiveTask) */
    public static final String RARE_BIOME_LAST = "lma_rare_biome_last";
    /** 女仆图鉴击杀计数 (MaidCodexKillListener) */
    public static final String CODEX = "lma_codex";
    /** 假人绑定 UUID (NumenMaidBridge) */
    public static final String COMPANION_UUID = "lma_companion_uuid";
    /** 数据 schema 版本 (int) — 女仆加入世界时按此补跑迁移 (v79.63, 见 {@link TaskDataSchema})。
     *  用途: 删除任务 / 改键结构后, 旧存档残留键有明确收口入口 (此前全项目零 schema 键 — 评审 P1-4) */
    public static final String SCHEMA_VERSION = "lma_schema";
    /** 前缀 — 管线临时数据 (内存态, MaidData) / 管线持久配置 / 状态机状态 */
    public static final String PL_PREFIX = "lma_pl_";
    public static final String CFG_PREFIX = "lma_cfg_";

    // 任务类型常量已删除 — 每个 Pipeline 的 taskType() 是正源
    // 死键删 (RETRY_COUNT/SAVED_HOME/SAVED_PICKUP/JUKEBOX_LAST/WEAPON_ANIM/
    //   LAST_EMOJI_TICK/FREEZE_TICKS/BLEED_TICKS/BLEED_DMG/FLOW_TIMEOUT/TASK_INPUT/
    //   FAIL_REASON/ANIM_TIME — 全项目 0 引用实证 (v79.55);
    //   旧存档残留由 FlowTaskData.clearAll 字面量清理面兜底)

    private TaskKeys() {}
}
