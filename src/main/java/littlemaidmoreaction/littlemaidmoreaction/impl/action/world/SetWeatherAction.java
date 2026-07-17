package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.world.WorldOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
@RuleAction
public final class SetWeatherAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.SelectParam("weather","天气","clear",List.of("clear","rain","thunder")),new TypedParam.IntParam("duration","持续tick",6000));
    @Override public String id(){return"set_weather";}
    @Override public String displayName(){return"设置天气";}
    @Override public ActionCategory category(){return ActionCategory.WORLD;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){
        if(ctx.maid().level()instanceof ServerLevel sl){
            var p=ParamExtractor.from(raw,PARAMS);
            WorldOutput.setWeather(sl,p.getString("weather"),p.getInt("duration"));
        }
    }
}
