package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.ysm.output.YsmWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import java.util.List;
import java.util.Map;

/** 停止YSM轮盘 — 委托给 {@link YsmWriter#stopRoulette}. */
@RuleAction
public final class StopYsmRouletteAction implements IAction {
    @Override public String id() { return "stop_ysm_roulette"; }
    @Override public String displayName() { return "停止YSM轮盘动画"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) { YsmWriter.stopRoulette(ctx.maid()); }
}
