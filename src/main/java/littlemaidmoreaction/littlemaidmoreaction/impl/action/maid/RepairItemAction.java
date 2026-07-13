package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import java.util.List;
import java.util.Map;

/** 经验修武器 — 委托给 {@link MaidStateWriter#repairHandItemWithXp}. */
@RuleAction
public final class RepairItemAction implements IAction {
    @Override public String id() { return "repair_item"; }
    @Override public String displayName() { return "经验修武器"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) { MaidStateWriter.repairHandItemWithXp(ctx.maid()); }
}
