package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid_ext;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

@RuleAction
public final class SetRideableAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.BoolParam("rideable", "可骑乘", true));
    @Override public String id() { return "set_rideable"; }
    @Override public String displayName() { return "可骑乘"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) { var p = ParamExtractor.from(raw, PARAMS); MaidStateWriter.setRideable(ctx.maid(), p.getBool("rideable")); }
}

