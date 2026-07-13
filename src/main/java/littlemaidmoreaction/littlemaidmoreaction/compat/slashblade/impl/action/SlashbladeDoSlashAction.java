package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

/** 拔刀剑完整出刀 — AttackManager.doSlash() 全参数版。 */
@RuleAction
public final class SlashbladeDoSlashAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.SelectParam("knockback", "击退类型", "smash", List.of("smash", "toss", "cancel")),
        new TypedParam.DoubleParam("damage_mult", "伤害倍率", 1.0),
        new TypedParam.DoubleParam("roll", "旋转角度", 0.0),
        new TypedParam.BoolParam("critical", "暴击", false),
        new TypedParam.BoolParam("mute", "静音", false)
    );
    @Override public String id() { return "slashblade_do_slash"; }
    @Override public String displayName() { return "拔刀剑出刀"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> r) {
        if (!(ctx.maid().getMainHandItem().getItem() instanceof ItemSlashBlade)) return;
        KnockBacks kb = switch (r.getOrDefault("knockback", "smash")) {
            case "toss" -> KnockBacks.toss; case "cancel" -> KnockBacks.cancel; default -> KnockBacks.smash;
        };
        float mult = Float.parseFloat(r.getOrDefault("damage_mult", "1.0"));
        float roll = Float.parseFloat(r.getOrDefault("roll", "0.0"));
        boolean critical = "true".equals(r.getOrDefault("critical", "false"));
        boolean mute = "true".equals(r.getOrDefault("mute", "false"));
        ctx.maid().swing(InteractionHand.MAIN_HAND);
        AttackManager.doSlash(ctx.maid(), roll, Vec3.ZERO, mute, critical, (double) mult, kb);
    }
}
