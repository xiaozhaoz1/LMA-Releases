package littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.slashblade.output.SlashBladeWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 蓄力SA — 委托给 {@link SlashBladeWriter#chargeSA}. */
@RuleAction
public final class SlashbladeChargeSAAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.IntParam("elapsed", "蓄力tick", 20));
    @Override public String id() { return "slashblade_charge_sa"; }
    @Override public String displayName() { return "拔刀剑蓄力SA"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> r) {
        SlashBladeWriter.chargeSA(ctx.maid(), Integer.parseInt(r.getOrDefault("elapsed", "20")));
    }
}
