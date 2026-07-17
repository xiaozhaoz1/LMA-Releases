package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 强制目标 — 委托给 {@link MaidStateWriter#forceTargetByMode}. */
@RuleAction
public final class ForceTargetAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.SelectParam("mode", "模式", "owner_attacker", List.of("owner_attacker", "owner_target", "nearest_hostile", "owner")));
    @Override public String id() { return "force_target"; }
    @Override public String displayName() { return "强制目标"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        MaidStateWriter.forceTargetByMode(ctx.maid(), p.getString("mode"));
    }
}

