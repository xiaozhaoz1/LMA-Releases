package littlemaidmoreaction.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * 触发事件枚举 — 规则引擎据此注册 @SubscribeEvent 处理器。
 *
 * 分为 TLM 女仆专属事件和 Forge 原生事件两大类。
 * 编辑器下拉列表显示 getDisplayName() (中文)。
 *
 * 伤害链:
 * 女仆打人: MAID_HURT_TARGET_PRE → 伤害 → MAID_HURT_TARGET_POST
 * 女仆被打: MAID_ATTACK → MAID_HURT → MAID_DAMAGE → MAID_DEATH
 */
public enum RuleEvent {

    // ===== TLM 战斗 (6) =====
    MAID_ATTACK("maid_attack", "女仆受攻击前", MaidAttackEvent.class, true),
    MAID_HURT("maid_hurt", "女仆受伤计算", MaidHurtEvent.class, true),
    MAID_DAMAGE("maid_damage", "女仆最终伤害", MaidDamageEvent.class, true),
    MAID_HURT_TARGET_PRE("maid_hurt_target_pre", "女仆近战攻击前", MaidHurtTarget.Pre.class, true),
    MAID_HURT_TARGET_POST("maid_hurt_target_post", "女仆近战攻击后", MaidHurtTarget.Post.class, false),
    MAID_DEATH("maid_death", "女仆即将死亡", MaidDeathEvent.class, true),

    // ===== TLM 交互 (3) =====
    MAID_INTERACT("maid_interact", "玩家右键女仆", InteractMaidEvent.class, true),
    MAID_TAMED("maid_tamed", "女仆被驯服", MaidTamedEvent.class, false),
    MAID_EQUIP("maid_equip", "女仆更换装备", MaidEquipEvent.class, false),

    // ===== TLM 拾取 (5) =====
    MAID_PICKUP_ITEM_PRE("maid_pickup_item_pre", "女仆拾取物品前", MaidPickupEvent.ItemResultPre.class, true),
    MAID_PICKUP_ITEM_POST("maid_pickup_item_post", "女仆拾取物品后", MaidPickupEvent.ItemResultPost.class, false),
    MAID_PICKUP_XP("maid_pickup_xp", "女仆拾取经验球", MaidPickupEvent.ExperienceResult.class, true),
    MAID_PICKUP_ARROW("maid_pickup_arrow", "女仆拾取箭矢", MaidPickupEvent.ArrowResult.class, true),
    MAID_PICKUP_POWER("maid_pickup_power", "女仆拾取P点", MaidPickupEvent.PowerPointResult.class, true),

    // ===== TLM 状态 (5) =====
    MAID_FAVOR_CHANGE("maid_favor_change", "好感度等级变化", MaidFavorabilityLevelChangeEvent.class, false),
    MAID_TASK_ENABLE("maid_task_enable", "女仆任务切换", MaidTaskEnableEvent.class, true),
    MAID_AFTER_EAT("maid_after_eat", "女仆吃完食物", MaidAfterEatEvent.class, false),
    MAID_PLAY_SOUND("maid_play_sound", "女仆播放音效", MaidPlaySoundEvent.class, true),
    MAID_TYPE_NAME("maid_type_name", "获取类型名称", MaidTypeNameEvent.class, false),

    // ===== TLM 装备/物品 (3) =====
    MAID_BACKPACK_CHANGE("maid_backpack_change", "女仆更换背包", MaidBackpackChangeEvent.class, false),
    MAID_BAUBLE_CHANGE("maid_bauble_change", "女仆更换饰品", MaidBaubleChangeEvent.class, false),
    MAID_FISHED("maid_fished", "女仆钓鱼成功", MaidFishedEvent.class, false),

    // ===== TLM 转换 (2) =====
    MAID_TICK("maid_tick", "女仆每tick", MaidTickEvent.class, true),
    MAID_TOMBSTONE("maid_tombstone", "女仆墓碑事件", MaidTombstoneEvent.class, true),
    MAID_CONVERT("maid_convert", "生物转换为女仆", ConvertMaidEvent.class, false),

    // ===== Forge 补充 (4) =====
    LIVING_FALL("living_fall", "女仆摔落", LivingFallEvent.class, false),
    LIVING_KNOCKBACK("living_knockback", "女仆被击退", LivingKnockBackEvent.class, false),
    LIVING_HEAL("living_heal", "女仆被治疗", LivingHealEvent.class, false),
    PROJECTILE_IMPACT("projectile_impact", "弹射物碰撞", ProjectileImpactEvent.class, false),

    // ===== TLM 物品传输/转换 (2) — v9.1 =====
    WIRELESS_IO("wireless_io", "女仆无线传输物品", MaidWirelessIOEvent.class, true),
    MAID_TRANSFORM("maid_transform", "女仆与物品互转", MaidAndItemTransformEvent.class, false),

    // ===== TLM 物品请求 (1) — v9.2 (反射注册, TLM>=1.5.1) =====
    MAID_REQUEST_ITEM("maid_request_item", "女仆请求物品", Event.class, true),

    // ===== TPM (2) — v9.2 =====
    MAID_COMBO_PROGRESS("maid_combo_progress", "女仆推进连段", Event.class, true),

    // ===== LMA 自定义 (1) — v9.3.2 =====
    MAID_HARVEST_CROP("maid_harvest_crop", "女仆收获作物", MaidHarvestCropEvent.class, false),

    // ===== LMA 自定义 (2) — v11 =====
    TASK_CHANGED("task_changed", "任务状态变更", Event.class, false),
    LMA_TASK_START("lma_task_start", "女仆任务循环触发", LmaTaskStartEvent.class, false),
    LMA_ENV_SCAN("lma_env_scan", "环境感知扫描命中", MaidTickEvent.class, false),

    ;

    private final String eventId;
    private final String displayName;
    private final Class<? extends Event> eventClass;
    private final boolean cancellable;

    RuleEvent(String eventId, String displayName, Class<? extends Event> eventClass, boolean cancellable) {
        this.eventId = eventId;
        this.displayName = displayName;
        this.eventClass = eventClass;
        this.cancellable = cancellable;
    }

    public String getEventId()          { return eventId; }
    public String getDisplayName()      { return displayName; }
    public Class<? extends Event> getEventClass() { return eventClass; }
    public boolean isCancellable()      { return cancellable; }

    public static RuleEvent fromEventId(String eventId) {
        for (RuleEvent e : values()) {
            if (e.eventId.equals(eventId)) return e;
        }
        return null;
    }
}
