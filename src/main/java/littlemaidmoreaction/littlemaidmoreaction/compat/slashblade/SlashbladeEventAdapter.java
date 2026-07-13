package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.RuleEngine;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * SlashBlade 重锋事件 → RuleEngine 桥接。
 * <p>仅在 SlashBlade 加载时注册。BreakEvent/AddProudSoulEvent/AddKillCountEvent 无 getUser() 故不桥接。</p>
 */
public final class SlashbladeEventAdapter {

    public static void register() {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("slashblade")) return;
        MinecraftForge.EVENT_BUS.register(new SlashbladeEventAdapter());
        LittleMaidMoreAction.LOGGER.info("[SlashbladeEvent] 事件桥接已注册 (5 events)");
    }

    /** 连段推进 — §c可取消。 */
    @SubscribeEvent
    public void onNextCombo(SlashBladeEvent.NextComboEvent e) {
        if (!(e.getUser() instanceof EntityMaid maid)) return;
        var ctx = new RuleContext(maid);
        ctx.setAttribute("slashblade_next", e.getNextCombo().toString());
        if (RuleEngine.handleEvent("slashblade_next_combo", ctx)) e.setCanceled(true);
    }

    /** 出刀。 */
    @SubscribeEvent
    public void onDoSlash(SlashBladeEvent.DoSlashEvent e) {
        if (!(e.getUser() instanceof EntityMaid maid)) return;
        var ctx = new RuleContext(maid);
        ctx.setAttribute("slashblade_damage", String.valueOf(e.getDamage()));
        ctx.setAttribute("slashblade_critical", String.valueOf(e.isCritical()));
        RuleEngine.handleEvent("slashblade_do_slash", ctx);
    }

    /** 蓄力SA — §c可取消。 */
    @SubscribeEvent
    public void onChargeAction(SlashBladeEvent.ChargeActionEvent e) {
        if (!(e.getEntityLiving() instanceof EntityMaid maid)) return;
        var ctx = new RuleContext(maid);
        ctx.setAttribute("slashblade_charge_ticks", String.valueOf(e.getChargeTicks()));
        if (RuleEngine.handleEvent("slashblade_charge_action", ctx)) e.setCanceled(true);
    }

    /** SA执行 — §c可取消。 */
    @SubscribeEvent
    public void onPerformSA(SlashBladeEvent.PerformSlashArtEvent e) {
        if (!(e.getEntityLiving() instanceof EntityMaid maid)) return;
        var ctx = new RuleContext(maid);
        ctx.setAttribute("slashblade_combo_state", e.getComboState() != null ? e.getComboState().toString() : "");
        ctx.setAttribute("slashblade_arts_type", e.getType() != null ? e.getType().name() : "");
        if (RuleEngine.handleEvent("slashblade_perform_sa", ctx)) e.setCanceled(true);
    }

    /** SA命中实体。 */
    @SubscribeEvent
    public void onHit(SlashBladeEvent.HitEvent e) {
        if (!(e.getUser() instanceof EntityMaid maid)) return;
        RuleEngine.handleEvent("slashblade_hit", new RuleContext(maid, e.getTarget()));
    }
}
