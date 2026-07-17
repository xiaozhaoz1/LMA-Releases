package littlemaidmoreaction.littlemaidmoreaction.impl.action.item;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

/** 给予物品 — 委托给 {@link MaidStateWriter#giveItem}. */
@RuleAction
public final class GiveItemAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.StringParam("item_id", "物品ID", "minecraft:diamond"), new TypedParam.IntParam("count", "数量", 1), new TypedParam.StringParam("nbt", "NBT", ""), new TypedParam.SelectParam("target", "目标", "self", List.of("self", "target", "owner")));
    @Override public String id() { return "give_item"; }
    @Override public String displayName() { return "给予物品"; }
    @Override public ActionCategory category() { return ActionCategory.ITEM; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        MaidStateWriter.giveItem(ctx.maid(), p.getString("item_id"), p.getInt("count"), p.getString("nbt"), p.getString("target"));
    }
}
