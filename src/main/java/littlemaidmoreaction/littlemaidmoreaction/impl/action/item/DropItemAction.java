package littlemaidmoreaction.littlemaidmoreaction.impl.action.item;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 丢弃物品 — 委托给 {@link MaidStateWriter#dropFromSlot}. */
@RuleAction
public final class DropItemAction implements IAction {
    private static final List<String> SOURCE_OPTIONS;
    static {
        var list = new java.util.ArrayList<>(List.of("mainhand", "offhand"));
        for (int i = 0; i <= 9; i++) list.add("inv_" + i);
        SOURCE_OPTIONS = List.copyOf(list);
    }
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.SelectParam("source", "来源栏位", "mainhand", SOURCE_OPTIONS),
        new TypedParam.IntParam("count", "丢弃数量(0=全部)", 1)
    );
    @Override public String id() { return "drop_item"; }
    @Override public String displayName() { return "丢弃物品"; }
    @Override public ActionCategory category() { return ActionCategory.ITEM; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        MaidStateWriter.dropFromSlot(ctx.maid(), p.getString("source"), p.getInt("count"));
    }
}
