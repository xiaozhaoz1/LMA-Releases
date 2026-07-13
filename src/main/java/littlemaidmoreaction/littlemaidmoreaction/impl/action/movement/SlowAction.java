package littlemaidmoreaction.littlemaidmoreaction.impl.action.movement;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.engine.EngineUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.*;
import static littlemaidmoreaction.littlemaidmoreaction.engine.EngineUtils.*;
@RuleAction
public final class SlowAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.DoubleParam("multiplier","倍率",0.5),new TypedParam.IntParam("duration","持续tick",60),new TypedParam.SelectParam("target","目标","target",List.of("self","target","owner")));
    @Override public String id(){return"slow";}
    @Override public String displayName(){return"减速";}
    @Override public ActionCategory category(){return ActionCategory.MOVEMENT;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public boolean isAsync(){return true;}
    @Override public void execute(RuleContext ctx,Map<String,String> p){LivingEntity t=EngineUtils.resolveTarget(ctx.maid(),ctx.target(),p.getOrDefault("target","target"));if(t==null||!t.isAlive())return;double m=parseDouble(p.getOrDefault("multiplier","0.5"),0.5);int d=parseInt(p.getOrDefault("duration","60"),60);var attr=t.getAttribute(Attributes.MOVEMENT_SPEED);if(attr==null)return;var pd=t.getPersistentData();pd.putDouble("lma_slow_mult",m);pd.putInt("lma_slow_ticks",d);attr.setBaseValue(attr.getBaseValue()*m);}
}
