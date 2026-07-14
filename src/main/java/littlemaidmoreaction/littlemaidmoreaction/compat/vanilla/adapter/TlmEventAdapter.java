package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.event.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.RuleEngine;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * v5 事件桥接器 — 29 个 {@code @SubscribeEvent} 薄委托。
 *
 * <p>每个订阅者仅做字段提取和取消决策，匹配和执行委托给
 * {@link RuleEngine#handleEvent}。</p>
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class TlmEventAdapter {

    // ═══ TLM 战斗 (6) ═══

    @SubscribeEvent public static void onMaidHurtTargetPre(MaidHurtTarget.Pre e) {
        LivingEntity t = e.getTarget() instanceof LivingEntity le ? le : null;
        if (RuleEngine.handleEvent("maid_hurt_target_pre", new RuleContext(e.getMaid(), t, null)))
            e.setCanceled(true);
    }
    @SubscribeEvent public static void onMaidHurtTargetPost(MaidHurtTarget.Post e) {
        LivingEntity t = e.getTarget() instanceof LivingEntity le ? le : null;
        RuleEngine.handleEvent("maid_hurt_target_post", new RuleContext(e.getMaid(), t, null));
    }
    @SubscribeEvent public static void onMaidAttack(MaidAttackEvent e) {
        LivingEntity t = e.getSource().getEntity() instanceof LivingEntity le ? le : null;
        if (RuleEngine.handleEvent("maid_attack", new RuleContext(e.getMaid(), t, e.getSource())))
            e.setCanceled(true);
    }
    @SubscribeEvent public static void onMaidHurt(MaidHurtEvent e) {
        LivingEntity t = e.getSource().getEntity() instanceof LivingEntity le ? le : null;
        if (RuleEngine.handleEvent("maid_hurt", new RuleContext(e.getMaid(), t, e.getSource())))
            e.setCanceled(true);
    }
    @SubscribeEvent public static void onMaidDamage(MaidDamageEvent e) {
        LivingEntity t = e.getSource().getEntity() instanceof LivingEntity le ? le : null;
        RuleEngine.handleEvent("maid_damage", new RuleContext(e.getMaid(), t, e.getSource()));
    }
    @SubscribeEvent public static void onMaidDeath(MaidDeathEvent e) {
        if (RuleEngine.handleEvent("maid_death", new RuleContext(e.getMaid(), null, e.getSource())))
            e.setCanceled(true);
    }

    // ═══ TLM 交互 (3) ═══

    @SubscribeEvent public static void onMaidInteract(InteractMaidEvent e) {
        if (RuleEngine.handleEvent("maid_interact", new RuleContext(e.getMaid(), e.getPlayer(), null)))
            e.setCanceled(true);
    }
    @SubscribeEvent public static void onMaidTamed(MaidTamedEvent e) {
        RuleEngine.handleEvent("maid_tamed", new RuleContext(e.getMaid(), e.getPlayer(), null));
    }
    @SubscribeEvent public static void onMaidEquip(MaidEquipEvent e) {
        RuleEngine.handleEvent("maid_equip", new RuleContext(e.getMaid(), null, null));
    }

    // ═══ TLM 拾取 (5) ═══

    @SubscribeEvent public static void onMaidPickupPre(MaidPickupEvent.ItemResultPre e) {
        RuleEngine.handleEvent("maid_pickup_item_pre", new RuleContext(e.getMaid()));
    }
    @SubscribeEvent public static void onMaidPickupPost(MaidPickupEvent.ItemResultPost e) {
        RuleEngine.handleEvent("maid_pickup_item_post", new RuleContext(e.getMaid()));
    }
    @SubscribeEvent public static void onMaidPickupXp(MaidPickupEvent.ExperienceResult e) {
        RuleEngine.handleEvent("maid_pickup_xp", new RuleContext(e.getMaid()));
    }
    @SubscribeEvent public static void onMaidPickupArrow(MaidPickupEvent.ArrowResult e) {
        RuleEngine.handleEvent("maid_pickup_arrow", new RuleContext(e.getMaid()));
    }
    @SubscribeEvent public static void onMaidPickupPower(MaidPickupEvent.PowerPointResult e) {
        RuleEngine.handleEvent("maid_pickup_power", new RuleContext(e.getMaid()));
    }

    // ═══ TLM 状态 (5) ═══

    @SubscribeEvent public static void onMaidFavorChange(MaidFavorabilityLevelChangeEvent e) {
        RuleEngine.handleEvent("maid_favor_change", new RuleContext(e.getMaid()));
    }
    @SubscribeEvent public static void onMaidTaskEnable(MaidTaskEnableEvent e) {
        // ★ v12.5: 处理 GUI 手动任务切换 (简单/复杂任务分流)
        LmaTaskGuiHandler.handle(e);
        var ctx = new RuleContext(e.getEntityMaid());
        ctx.setAttribute("enabled_task_uid", e.getTargetTask().getUid().toString());
        RuleEngine.handleEvent("maid_task_enable", ctx);
    }
    @SubscribeEvent public static void onMaidAfterEat(MaidAfterEatEvent e) {
        RuleEngine.handleEvent("maid_after_eat", new RuleContext(e.getMaid()));
    }
    @SubscribeEvent public static void onMaidPlaySound(MaidPlaySoundEvent e) {
        RuleEngine.handleEvent("maid_play_sound", new RuleContext(e.getMaid()));
    }
    @SubscribeEvent public static void onMaidTypeName(MaidTypeNameEvent e) {
        RuleEngine.handleEvent("maid_type_name", new RuleContext(e.getMaid()));
    }

    // ═══ TLM 装备/物品 (3) ═══

    @SubscribeEvent public static void onMaidBackpackChange(MaidBackpackChangeEvent e) {
        RuleEngine.handleEvent("maid_backpack_change", new RuleContext(e.getMaid()));
    }
    @SubscribeEvent public static void onMaidBaubleChange(MaidBaubleChangeEvent e) {
        RuleEngine.handleEvent("maid_bauble_change", new RuleContext(e.getMaid()));
    }
    @SubscribeEvent public static void onMaidFished(MaidFishedEvent e) {
        RuleEngine.handleEvent("maid_fished", new RuleContext(e.getMaid()));
    }

    // ═══ TLM 转换 (2+1) ═══

    @SubscribeEvent public static void onMaidTick(MaidTickEvent e) {
        var ctx = new RuleContext(e.getMaid());
        // v11: 任务引擎 tick (状态轮询+超时检测, 在规则匹配前执行)
        littlemaidmoreaction.littlemaidmoreaction.compat.flowtask.api.TaskEngine.tick(ctx);
        RuleEngine.handleEvent("maid_tick", ctx);
    }
    @SubscribeEvent public static void onMaidTombstone(MaidTombstoneEvent e) {
        if (RuleEngine.handleEvent("maid_tombstone", new RuleContext(e.getMaid())))
            e.setCanceled(true);
    }
    @SubscribeEvent public static void onMaidConvert(ConvertMaidEvent e) {
        if (e.getMaid() instanceof EntityMaid m)
            RuleEngine.handleEvent("maid_convert", new RuleContext(m));
    }

    // ═══ Forge 事件 (4) ═══

    @SubscribeEvent public static void onLivingFall(LivingFallEvent e) {
        if (e.getEntity() instanceof EntityMaid m)
            RuleEngine.handleEvent("living_fall", new RuleContext(m));
    }
    @SubscribeEvent public static void onLivingKnockBack(LivingKnockBackEvent e) {
        if (e.getEntity() instanceof EntityMaid m)
            RuleEngine.handleEvent("living_knockback", new RuleContext(m));
    }
    @SubscribeEvent public static void onLivingHeal(LivingHealEvent e) {
        if (e.getEntity() instanceof EntityMaid m)
            RuleEngine.handleEvent("living_heal", new RuleContext(m));
    }
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent e) {
        Entity owner = e.getProjectile().getOwner();
        if (owner instanceof EntityMaid m)
            RuleEngine.handleEvent("projectile_impact", new RuleContext(m));
    }

    // ===== v9.1: 物品传输/转换 (2) =====
    @SubscribeEvent public static void onWirelessIO(MaidWirelessIOEvent e) {
        RuleEngine.handleEvent("wireless_io", new RuleContext(e.getMaid()));
    }
    @SubscribeEvent public static void onMaidTransform(MaidAndItemTransformEvent e) {
        RuleEngine.handleEvent("maid_transform", new RuleContext(e.getMaid()));
    }

    // ===== v9.3.2: LMA 自定义事件 (1) =====
    @SubscribeEvent
    public static void onMaidHarvestCrop(littlemaidmoreaction.littlemaidmoreaction.event.MaidHarvestCropEvent e) {
        RuleEngine.handleEvent("maid_harvest_crop", new RuleContext(e.getMaid()));
    }

    // ===== v15: LMA 任务循环事件 =====
    @SubscribeEvent
    public static void onLmaTaskStart(littlemaidmoreaction.littlemaidmoreaction.event.LmaTaskStartEvent e) {
        RuleEngine.handleEvent("lma_task_start", new RuleContext(e.getMaid()));
    }

    // ===== Entity Join — 清理跨session残留任务 =====
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent e) {
        if (!(e.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide()) return;

        var data = maid.getPersistentData();
        String task = data.getString("lma_flow_task");
        if (task.isEmpty()) return;

        // ★ Bug #68 fix: 无条件清理跨session残留任务。
        // 原时间戳检测 (savedTick > now) 在单人游戏中无效 — gameTime 随存档持久化，
        // 上次session的 tick < 本次session的 tick → 条件永不为真。
        // 改为无条件清理 — EntityJoinLevelEvent 在实体从磁盘加载时触发，cleanup 安全。
        {
            long savedTick = data.getLong("lma_flow_tick");
            long now = maid.level().getGameTime();
            LittleMaidMoreAction.LOGGER.warn("[LMA/Cleanup] stale task '{}' from previous session (tick={} >> now={}), cleaning",
                task, savedTick, now);
            LmaFlowTask.restorePreviousTask(maid);
            data.remove("lma_flow_task"); data.remove("lma_flow_task_id");
            data.remove("lma_flow_state"); data.remove("lma_flow_step");
            data.remove("lma_flow_counter"); data.remove("lma_flow_max_count");
            data.remove("lma_flow_tick"); data.remove("lma_flow_timeout");
            data.remove("lma_flow_data"); data.remove("lma_flow_cached");
            // 恢复原TLM任务 → brain回到正常状态
            var defaultTask = com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager.getIdleTask();
            maid.setTask(defaultTask);
        }
    }

    private TlmEventAdapter() {}
}
