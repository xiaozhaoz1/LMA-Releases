package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;

/** 范围伤害 — 伤害委托 CombatOutput，粒子保留 */
@RuleAction
public final class DamageNearbyAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.DoubleParam("damage", "伤害值", 4.0),
        new TypedParam.IntParam("range", "范围", 5),
        new TypedParam.BoolParam("hostile_only", "仅敌对", true),
        new TypedParam.BoolParam("spawn_particles", "粒子效果", true)
    );

    @Override public String id() { return "damage_nearby"; }
    @Override public String displayName() { return "范围伤害"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        if (!(ctx.maid().level() instanceof ServerLevel sl)) return;
        var p = ParamExtractor.from(raw, PARAMS);
        int range = p.getInt("range");
        CombatOutput.damageNearby(ctx.maid(), range, (float) p.getDouble("damage"), p.getBool("hostile_only"));
        // 粒子环效果 (保留在 impl — 纯视觉)
        if (p.getBool("spawn_particles")) {
            var center = ctx.maid().blockPosition();
            for (int i = 0; i < 36; i++) {
                double angle = i * Math.PI * 2 / 36;
                sl.sendParticles(ParticleTypes.FLAME,
                    center.getX() + 0.5 + Math.cos(angle) * range,
                    center.getY() + 1.0,
                    center.getZ() + 0.5 + Math.sin(angle) * range,
                    1, 0, 0.1, 0, 0.02);
            }
        }
    }
}
