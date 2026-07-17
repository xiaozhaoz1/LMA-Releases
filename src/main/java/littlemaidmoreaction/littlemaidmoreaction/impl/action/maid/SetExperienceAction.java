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

/** 设置女仆经验值。支持 set（精确设置）和 add（增减）。 */
@RuleAction
public final class SetExperienceAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
            new TypedParam.IntParam("amount", "数值", 0),
            new TypedParam.SelectParam("mode", "模式", "set", List.of("set", "add")));
    @Override public String id() { return "set_experience"; }
    @Override public String displayName() { return "设置经验值"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        int amount = p.getInt("amount");
        if ("add".equals(p.getString("mode"))) MaidStateWriter.addExperience(ctx.maid(), amount);
        else MaidStateWriter.setExperience(ctx.maid(), amount);
    }
}
