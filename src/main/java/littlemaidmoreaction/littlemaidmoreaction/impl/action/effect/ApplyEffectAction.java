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
public final class ApplyEffectAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.StringParam("effect_id", "效果ID", "minecraft:slowness"), new TypedParam.IntParam("duration", "持续刻数", 100), new TypedParam.IntParam("amplifier", "等级", 0), new TypedParam.BoolParam("show_particles", "显示粒子", true), new TypedParam.SelectParam("target", "目标", "target", List.of("self", "target", "owner")));
    @Override public String id() { return "apply_effect"; }
    @Override public String displayName() { return "施加效果"; }
    @Override public ActionCategory category() { return ActionCategory.EFFECT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        var rec = p.resolveTarget(ctx.maid(), ctx.target());
        if (rec == null) return;
        EffectOutput.apply(rec, p.getString("effect_id"), p.getInt("duration"), p.getInt("amplifier"), p.getBool("show_particles"));
    }
}
