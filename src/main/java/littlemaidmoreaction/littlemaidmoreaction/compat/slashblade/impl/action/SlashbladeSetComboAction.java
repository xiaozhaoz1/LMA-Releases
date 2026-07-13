package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.output.SlashBladeWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 设置连段 — 委托给 {@link SlashBladeWriter#setCombo}. */
@RuleAction
public final class SlashbladeSetComboAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.StringParam("combo_id","连段ID","slashblade:standby"));
    @Override public String id() { return "slashblade_set_combo"; }
    @Override public String displayName() { return "设置拔刀连段"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> r) {
        String id = r.getOrDefault("combo_id", "");
        if (!id.isEmpty()) SlashBladeWriter.setCombo(ctx.maid(), id);
    }
}
