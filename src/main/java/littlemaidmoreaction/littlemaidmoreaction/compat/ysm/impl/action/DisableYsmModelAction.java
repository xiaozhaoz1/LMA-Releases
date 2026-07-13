package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.ysm.output.YsmWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import java.util.List;
import java.util.Map;

/** 禁用YSM模型 — 委托给 {@link YsmWriter#disableModel}. */
@RuleAction
public final class DisableYsmModelAction implements IAction {
    @Override public String id() { return "disable_ysm_model"; }
    @Override public String displayName() { return "禁用YSM模型"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) { YsmWriter.disableModel(ctx.maid()); }
}
