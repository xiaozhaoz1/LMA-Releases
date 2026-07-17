package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.*;
@RuleAction
public final class SetGlowingAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.BoolParam("glowing","发光",true));
    @Override public String id(){return"set_glowing";}
    @Override public String displayName(){return"设置发光";}
    @Override public ActionCategory category(){return ActionCategory.MAID;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){var p=ParamExtractor.from(raw,PARAMS);MaidStateWriter.setGlowing(ctx.maid(),p.getBool("glowing"));}
}
