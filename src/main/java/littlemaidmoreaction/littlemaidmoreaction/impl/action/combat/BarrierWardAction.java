package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.world.WorldStateReader;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.effect.EffectOutput;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.visual.VisualOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import net.minecraft.world.entity.monster.Monster;

import java.util.List;
import java.util.Map;

/** #12 领域封锁 — 击退周围怪物 + 缓慢 */
@RuleAction
public final class BarrierWardAction implements IAction {

    @Override public String id() { return "barrier_ward"; }
    @Override public String displayName() { return "领域封锁"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        EntityMaid m = ctx.maid();

        // input: 读周围怪物
        var monsters = WorldStateReader.getEntitiesInRange(m.level(), m.blockPosition(),
            8.0, Monster.class, e -> e.isAlive());

        // output: 击退 + 缓慢
        for (var mon : monsters) {
            CombatOutput.knockback(mon, m, 2.0f);
            EffectOutput.apply(mon, "minecraft:slowness", 100, 2, false);
        }
        if (!monsters.isEmpty()) {
            VisualOutput.spawnParticle(m.level(), "minecraft:enchant", m.blockPosition().above(), 10, 1.5);
        }
    }
}
