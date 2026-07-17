package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.world.WorldOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.*;
@RuleAction
public final class SetTimeAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.IntParam("time","时间tick",0));
    @Override public String id(){return"set_time";}
    @Override public String displayName(){return"设置时间";}
    @Override public ActionCategory category(){return ActionCategory.WORLD;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){var p=ParamExtractor.from(raw,PARAMS);if(ctx.maid().level()instanceof net.minecraft.server.level.ServerLevel sl)WorldOutput.setTime(sl,p.getInt("time"));}
}
