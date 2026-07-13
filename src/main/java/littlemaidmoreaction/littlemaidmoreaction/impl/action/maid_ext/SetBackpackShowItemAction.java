package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid_ext;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 设置女仆背包上展示的装饰物品。传入物品 ID 字符串。 */
@RuleAction
public final class SetBackpackShowItemAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
            new TypedParam.StringParam("item_id", "物品ID", "minecraft:diamond"));
    @Override public String id() { return "set_backpack_show_item"; }
    @Override public String displayName() { return "背包展示物品"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        MaidStateWriter.setBackpackShowItem(ctx.maid(), p.getString("item_id"));
    }
}
