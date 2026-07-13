package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;
@RuleAction
public final class SetInvulnerableAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.BoolParam("invulnerable", "无敌", true));
    @Override public String id() { return "set_invulnerable"; }
    @Override public String displayName() { return "设置无敌"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String,String> raw) { var p = ParamExtractor.from(raw, PARAMS); MaidStateWriter.setInvulnerable(ctx.maid(), p.getBool("invulnerable")); }
}
