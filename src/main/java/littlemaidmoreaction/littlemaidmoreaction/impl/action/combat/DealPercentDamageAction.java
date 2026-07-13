package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.*;
@RuleAction
public final class DealPercentDamageAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.DoubleParam("percent","百分比",0.5),new TypedParam.SelectParam("target","目标","target",List.of("self","target","owner")));
    @Override public String id(){return"deal_percent_damage";}
    @Override public String displayName(){return"百分比伤害";}
    @Override public ActionCategory category(){return ActionCategory.COMBAT;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){var p=ParamExtractor.from(raw,PARAMS);var t=p.resolveTarget(ctx.maid(),ctx.target());if(t==null||!t.isAlive())return;CombatOutput.damageByRatio(t,ctx.maid(),(float)p.getDouble("percent"));}
}
