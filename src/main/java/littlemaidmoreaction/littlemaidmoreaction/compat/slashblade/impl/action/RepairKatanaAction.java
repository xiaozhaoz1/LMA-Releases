package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.output.SlashBladeWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import java.util.List;
import java.util.Map;

/** 经验修武器 — 委托给 {@link SlashBladeWriter#repairKatana}. */
@RuleAction
public final class RepairKatanaAction implements IAction {
    @Override public String id() { return "repair_katana"; }
    @Override public String displayName() { return "经验修武器"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        SlashBladeWriter.repairKatana(ctx.maid(), ctx.maid().getExperience());
    }
}
