package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;

/** 主人杀敌加成 — 主人杀此怪≥1000→额外造成等量伤害(不取消原伤害) */
@RuleAction
public final class KillBonusDamageAction implements IAction {
    @Override public String id() { return "kill_bonus_damage"; }
    @Override public String displayName() { return "杀敌加成伤害"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() {
        return List.of(new littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam.IntParam("amount", "伤害值", 5));
    }
    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        LivingEntity target = ctx.target();
        if (target != null)
            CombatOutput.ownerKillBonusDamage(target, ctx.maid(),
                parseInt(raw.getOrDefault("amount", "5"), 5));
    }
    private static int parseInt(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
}
