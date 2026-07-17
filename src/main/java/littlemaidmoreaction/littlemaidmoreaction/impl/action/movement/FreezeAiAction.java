package littlemaidmoreaction.littlemaidmoreaction.impl.action.movement;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.movement.MovementOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** 冻结AI — 委托给 {@link MovementOutput#freezeAi(LivingEntity, int)}. */
@RuleAction
public final class FreezeAiAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.SelectParam("target","目标","self",List.of("self","target")),new TypedParam.IntParam("duration","持续tick",40));
    @Override public String id(){return"freeze_ai";}
    @Override public String displayName(){return"冻结AI";}
    @Override public ActionCategory category(){return ActionCategory.MOVEMENT;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public boolean isAsync(){return true;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){
        var p=ParamExtractor.from(raw,PARAMS);
        LivingEntity t="target".equals(p.getString("target"))?ctx.target():ctx.maid();
        if(t==null)return;
        MovementOutput.freezeAi(t,p.getInt("duration"));
    }
}
