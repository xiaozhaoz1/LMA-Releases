package littlemaidmoreaction.littlemaidmoreaction.impl.action.effect;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.effect.EffectOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

@RuleAction
public final class ClearEffectsAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.SelectParam("target", "目标", "target", List.of("self", "target", "owner")), new TypedParam.StringParam("effect_id", "效果ID(留空清除全部)", ""));
    @Override public String id() { return "clear_effects"; }
    @Override public String displayName() { return "清除效果"; }
    @Override public ActionCategory category() { return ActionCategory.EFFECT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        var e = p.resolveTarget(ctx.maid(), ctx.target());
        if (e == null) return;
        String id = p.getString("effect_id");
        if (id.isEmpty()) EffectOutput.clearAll(e);
        else EffectOutput.clearEffect(e, id);
    }
}
