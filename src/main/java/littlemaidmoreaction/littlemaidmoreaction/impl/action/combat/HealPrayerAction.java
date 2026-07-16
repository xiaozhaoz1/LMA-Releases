package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.visual.VisualOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;

import java.util.List;
import java.util.Map;

/** #10 治疗祷言 — 战斗中缓慢自回血 */
@RuleAction
public final class HealPrayerAction implements IAction {

    @Override public String id() { return "heal_prayer"; }
    @Override public String displayName() { return "治疗祷言"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        EntityMaid m = ctx.maid();
        // 回 5% 血
        CombatOutput.healByRatio(m, 0.05f);
        VisualOutput.spawnParticle(m.level(), "minecraft:heart", m.blockPosition().above(), 2, 0.3);
    }
}
