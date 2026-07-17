package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

@RuleAction
public final class SetHungerAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.IntParam("value", "饱食度", 20));
    @Override public String id() { return "set_hunger"; }
    @Override public String displayName() { return "设置饥饿值"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) { var p = ParamExtractor.from(raw, PARAMS); MaidStateWriter.setHunger(ctx.maid(), p.getInt("value")); }
}

