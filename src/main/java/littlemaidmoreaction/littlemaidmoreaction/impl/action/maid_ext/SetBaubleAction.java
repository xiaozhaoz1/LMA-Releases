package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid_ext;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 给女仆饰品栏添加物品 — 委托给 {@link MaidStateWriter#setBauble}. */
@RuleAction
public final class SetBaubleAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
            new TypedParam.StringParam("item_id", "物品ID", "touhou_little_maid:ultramarine_orb_elixir"),
            new TypedParam.IntParam("slot", "槽位(-1=自动)", -1));

    @Override public String id() { return "set_bauble"; }
    @Override public String displayName() { return "添加饰品"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        MaidStateWriter.setBauble(ctx.maid(), p.getString("item_id"), p.getInt("slot"));
    }
}
