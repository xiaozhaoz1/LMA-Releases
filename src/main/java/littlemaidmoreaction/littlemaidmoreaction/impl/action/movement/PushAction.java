package littlemaidmoreaction.littlemaidmoreaction.impl.action.movement;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.*;
@RuleAction
public final class PushAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.DoubleParam("distance","距离",3.0),new TypedParam.SelectParam("target","目标","target",List.of("target","owner")));
    @Override public String id(){return"push";}
    @Override public String displayName(){return"推开";}
    @Override public ActionCategory category(){return ActionCategory.MOVEMENT;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){var p=ParamExtractor.from(raw,PARAMS);var t=p.resolveTarget(ctx.maid(),ctx.target());if(t==null||!t.isAlive())return;float d=(float)p.getDouble("distance");CombatOutput.push(t,ctx.maid(),d);t.hurtMarked=true;}
}
