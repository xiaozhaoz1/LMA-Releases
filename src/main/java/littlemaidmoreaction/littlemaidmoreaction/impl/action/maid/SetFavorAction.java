package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.maid.MaidStateReader;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

@RuleAction
public final class SetFavorAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.IntParam("amount", "数值", 0), new TypedParam.SelectParam("mode", "模式", "add", List.of("add", "set", "max")));
    @Override public String id() { return "set_favor"; }
    @Override public String displayName() { return "修改好感度"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        switch (p.getString("mode")) {
            case "set" -> MaidStateWriter.setFavor(ctx.maid(), p.getInt("amount"));
            case "max" -> MaidStateWriter.setFavorMax(ctx.maid());
            default -> MaidStateWriter.setFavor(ctx.maid(), MaidStateReader.getFavorability(ctx.maid()) + p.getInt("amount"));
        }
    }
}

