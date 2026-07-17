package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid_ext;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

@RuleAction
public final class SetAimingAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.BoolParam("aiming", "瞄准", true));
    @Override public String id() { return "set_aiming"; }
    @Override public String displayName() { return "瞄准姿态"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) { var p = ParamExtractor.from(raw, PARAMS); MaidStateWriter.setAiming(ctx.maid(), p.getBool("aiming")); }
}

