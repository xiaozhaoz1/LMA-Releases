package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.effect.EffectOutput;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.visual.VisualOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;

/** #9 战吼激励 — 给周围主人加力量Buff */
@RuleAction
public final class BattleCryAction implements IAction {

    @Override public String id() { return "battle_cry"; }
    @Override public String displayName() { return "战吼激励"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        EntityMaid m = ctx.maid();
        Player owner = m.level().getPlayerByUUID(m.getOwnerUUID());
        if (owner == null || owner.distanceTo(m) > 16) return;

        // output: 给主人力量II, 10秒
        EffectOutput.apply(owner, "minecraft:strength", 200, 1, false);
        // 视觉反馈
        VisualOutput.spawnParticle(m.level(), "minecraft:angry_villager", m.blockPosition().above(), 5, 0.5);
    }
}
