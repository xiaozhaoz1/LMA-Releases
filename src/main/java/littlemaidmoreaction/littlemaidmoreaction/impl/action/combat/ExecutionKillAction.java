package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.*;
@RuleAction
public final class ExecutionKillAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.SelectParam("target","目标","target",List.of("self","target","owner")));
    @Override public String id(){return"execution_kill";}
    @Override public String displayName(){return"斩杀";}
    @Override public ActionCategory category(){return ActionCategory.COMBAT;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){
        var p=ParamExtractor.from(raw,PARAMS);
        var t=p.resolveTarget(ctx.maid(),ctx.target());
        if(t==null||!t.isAlive()){
            LittleMaidMoreAction.LOGGER.warn("[LMA/ExecutionKill] 目标无效或已死亡");
            return;
        }
        LittleMaidMoreAction.LOGGER.info("[LMA/ExecutionKill] 斩杀 target={} hp={}",t.getName().getString(),t.getHealth());
        CombatOutput.executionKill(t,ctx.maid());
    }
}
