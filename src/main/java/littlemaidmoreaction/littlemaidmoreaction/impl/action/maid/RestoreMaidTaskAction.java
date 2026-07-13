package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import java.util.List;
import java.util.Map;

/** 恢复任务 — 委托给 {@link MaidStateWriter#restorePreviousTask}. */
@RuleAction
public final class RestoreMaidTaskAction implements IAction {
    @Override public String id() { return "restore_maid_task"; }
    @Override public String displayName() { return "恢复任务"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) { MaidStateWriter.restorePreviousTask(ctx.maid()); }
}
