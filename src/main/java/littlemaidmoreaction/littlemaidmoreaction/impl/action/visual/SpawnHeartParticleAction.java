package littlemaidmoreaction.littlemaidmoreaction.impl.action.visual;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.visual.VisualOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/** 爱心粒子 — 委托给 {@link VisualOutput#spawnHeartParticle}. */
@RuleAction
public final class SpawnHeartParticleAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("count", "粒子数量", 8)
    );

    @Override public String id() { return "spawn_heart_particle"; }
    @Override public String displayName() { return "爱心粒子"; }
    @Override public ActionCategory category() { return ActionCategory.VISUAL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return false; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        VisualOutput.spawnHeartParticle(ctx.maid(), p.getInt("count"));
    }
}
