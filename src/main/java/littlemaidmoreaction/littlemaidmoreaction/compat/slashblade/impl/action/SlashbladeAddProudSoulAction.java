package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.output.SlashBladeWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 添加耀魂 — 委托给 {@link SlashBladeWriter#addProudSoul}. */
@RuleAction
public final class SlashbladeAddProudSoulAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.IntParam("amount", "耀魂数量", 100));
    @Override public String id() { return "slashblade_add_proud_soul"; }
    @Override public String displayName() { return "添加耀魂"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> r) {
        SlashBladeWriter.addProudSoul(ctx.maid(), Integer.parseInt(r.getOrDefault("amount", "100")));
    }
}
