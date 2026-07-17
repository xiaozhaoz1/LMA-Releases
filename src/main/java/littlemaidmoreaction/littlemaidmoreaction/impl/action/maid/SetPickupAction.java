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
public final class SetPickupAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.BoolParam("enabled", "启用", true), new TypedParam.SelectParam("type", "拾取类型", "all", List.of("all", "only_item", "only_xp")));
    @Override public String id() { return "set_pickup"; }
    @Override public String displayName() { return "切换拾取"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        MaidStateWriter.setPickup(ctx.maid(), p.getBool("enabled"), p.getString("type"));
    }
}

