package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/** 发射弹射物 — 委托给 {@link CombatOutput#launchProjectile}. */
@RuleAction
public final class LaunchProjectileAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("projectile_id", "弹射物ID", "minecraft:arrow"),
        new TypedParam.DoubleParam("speed", "速度", 1.5),
        new TypedParam.DoubleParam("inaccuracy", "偏差", 1.0)
    );
    @Override public String id() { return "launch_projectile"; }
    @Override public String displayName() { return "发射弹射物"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        if (ctx.target() == null) return;
        var p = ParamExtractor.from(raw, PARAMS);
        CombatOutput.launchProjectile(ctx.maid(), ctx.target(), p.getString("projectile_id"),
            (float) p.getDouble("speed"), (float) p.getDouble("inaccuracy"));
    }
}
