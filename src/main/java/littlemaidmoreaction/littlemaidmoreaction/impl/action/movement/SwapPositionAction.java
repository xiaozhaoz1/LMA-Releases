package littlemaidmoreaction.littlemaidmoreaction.impl.action.movement;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.movement.MovementOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
@RuleAction
public final class SwapPositionAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.SelectParam("target","目标","target",List.of("target","owner")));
    @Override public String id(){return"swap_position";}
    @Override public String displayName(){return"交换位置";}
    @Override public ActionCategory category(){return ActionCategory.MOVEMENT;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> p){LivingEntity t="owner".equals(p.getOrDefault("target","target"))?ctx.maid().getOwner():ctx.target();if(t==null||!t.isAlive())return;MovementOutput.swapPosition(ctx.maid(),t);}
}
