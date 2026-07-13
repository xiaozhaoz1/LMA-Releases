package littlemaidmoreaction.littlemaidmoreaction.impl.action.movement;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.movement.MovementOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.*;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.*;
@RuleAction
public final class FaceTargetAction implements IAction {
    private static final List<TypedParam<?>> PARAMS=List.of(new TypedParam.SelectParam("target","目标","target",List.of("target","owner")),new TypedParam.BoolParam("instant","立刻",true));
    @Override public String id(){return"face_target";}
    @Override public String displayName(){return"面向目标";}
    @Override public ActionCategory category(){return ActionCategory.MOVEMENT;}
    @Override public List<TypedParam<?>> params(){return PARAMS;}
    @Override public void execute(RuleContext ctx,Map<String,String> raw){var p=ParamExtractor.from(raw,PARAMS);var t=p.resolveTarget(ctx.maid(),ctx.target());if(t==null)return;MovementOutput.faceTarget(ctx.maid(),t);ctx.maid().yBodyRot=ctx.maid().getYRot();}
}
