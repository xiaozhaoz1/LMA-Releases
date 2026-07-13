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

@RuleAction
public final class HealAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.SelectParam("target", "目标", "self",
            List.of("self", "target", "owner")),
        new TypedParam.DoubleParam("amount", "治疗量", 5.0)
    );
    @Override public String id() { return "heal"; }
    @Override public String displayName() { return "治疗"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        var t = p.resolveTarget(ctx.maid(), ctx.target());
        if (t == null) return;
        CombatOutput.heal(t, (float) p.getDouble("amount"));
    }
}
