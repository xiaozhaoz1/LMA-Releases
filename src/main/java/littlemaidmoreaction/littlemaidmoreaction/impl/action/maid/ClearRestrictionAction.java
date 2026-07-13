package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;
@RuleAction
public final class ClearRestrictionAction implements IAction {
    @Override public String id() { return "clear_restriction"; }
    @Override public String displayName() { return "清除活动范围"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String,String> raw) { MaidStateWriter.clearRestriction(ctx.maid()); }
}
