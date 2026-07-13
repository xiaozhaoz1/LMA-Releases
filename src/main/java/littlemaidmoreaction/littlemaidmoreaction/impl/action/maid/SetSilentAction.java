package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.*;
@RuleAction
public final class SetSilentAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.BoolParam("silent","静音",true));
    @Override public String id(){return"set_silent";}
    @Override public String displayName(){return"设置静音";}
    @Override public ActionCategory category(){return ActionCategory.MAID;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){var p=ParamExtractor.from(raw,PARAMS);MaidStateWriter.setSilent(ctx.maid(),p.getBool("silent"));}
}
