package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.ysm.output.YsmWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import java.util.List;
import java.util.Map;

/** 重置YSM漫游变量 — 委托给 {@link YsmWriter#resetRoamingVars}. */
@RuleAction
public final class ResetYsmRoamingVarsAction implements IAction {
    @Override public String id() { return "reset_ysm_roaming_vars"; }
    @Override public String displayName() { return "重置YSM漫游变量"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) { YsmWriter.resetRoamingVars(ctx.maid()); }
}
