package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.*;
@RuleAction
public final class DealTrueDamageAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.DoubleParam("amount","伤害量",10.0),new TypedParam.SelectParam("target","目标","target",List.of("self","target","owner")));
    @Override public String id(){return"deal_true_damage";}
    @Override public String displayName(){return"真实伤害";}
    @Override public ActionCategory category(){return ActionCategory.COMBAT;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){var p=ParamExtractor.from(raw,PARAMS);var t=p.resolveTarget(ctx.maid(),ctx.target());if(t==null||!t.isAlive())return;CombatOutput.magicDamage(t,ctx.maid(),(float)p.getDouble("amount"));}
}
