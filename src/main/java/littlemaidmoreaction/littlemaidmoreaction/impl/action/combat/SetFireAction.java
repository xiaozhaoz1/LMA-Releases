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
public final class SetFireAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.SelectParam("target", "目标", "target",
            List.of("self", "target", "owner")),
        new TypedParam.IntParam("seconds", "燃烧秒数", 5)
    );
    @Override public String id() { return "set_fire"; }
    @Override public String displayName() { return "点燃"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        var t = p.resolveTarget(ctx.maid(), ctx.target());
        if (t == null) return;
        CombatOutput.setFire(t, p.getInt("seconds"));
    }
}
