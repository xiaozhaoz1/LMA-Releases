package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

@RuleAction
public final class KnockbackAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.SelectParam("target", "目标", "target",
            List.of("self", "target", "owner")),
        new TypedParam.DoubleParam("strength", "击退强度", 1.5),
        new TypedParam.DoubleParam("vertical", "垂直击退", 0.4)
    );
    @Override public String id() { return "knockback"; }
    @Override public String displayName() { return "击退"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        var t = p.resolveTarget(ctx.maid(), ctx.target());
        if (t == null) return;
        CombatOutput.knockbackWithVertical(t, ctx.maid(), (float) p.getDouble("strength"), (float) p.getDouble("vertical"));
    }
}
