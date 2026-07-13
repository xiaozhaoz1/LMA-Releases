package littlemaidmoreaction.littlemaidmoreaction.impl.action.item;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.item.ItemOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 提取经验 — 委托给 {@link ItemOutput#extractXpAsBottles}. */
@RuleAction
public final class ExtractMaidXpAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
            new TypedParam.IntParam("ratio", "每瓶XP", 1),
            new TypedParam.IntParam("max_bottles", "最大瓶数", 64)
    );
    @Override public String id() { return "extract_maid_xp"; }
    @Override public String displayName() { return "提取经验"; }
    @Override public ActionCategory category() { return ActionCategory.ITEM; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        ItemOutput.extractXpAsBottles(ctx.maid(), p.getInt("ratio"), p.getInt("max_bottles"));
    }
}
