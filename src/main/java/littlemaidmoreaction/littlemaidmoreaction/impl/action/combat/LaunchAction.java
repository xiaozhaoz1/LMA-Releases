package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** 击飞 — 委托给 {@link CombatOutput#launch}. */
@RuleAction
public final class LaunchAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.DoubleParam("horizontal","水平力",1.5),new TypedParam.DoubleParam("vertical","垂直力",0.6),new TypedParam.SelectParam("target","目标","target",List.of("self","target","owner")));
    @Override public String id(){return"launch";}
    @Override public String displayName(){return"击飞";}
    @Override public ActionCategory category(){return ActionCategory.COMBAT;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){
        var p=ParamExtractor.from(raw,PARAMS);
        LivingEntity t=p.resolveTarget(ctx.maid(),ctx.target());
        if(t==null||!t.isAlive())return;
        CombatOutput.launch(t,ctx.maid(),(float)p.getDouble("horizontal"),(float)p.getDouble("vertical"));
    }
}
