package littlemaidmoreaction.littlemaidmoreaction.impl.action.movement;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.movement.MovementOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

@RuleAction
public final class SetMotionAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.SelectParam("target","目标","self",List.of("self","target","owner")), new TypedParam.DoubleParam("x","X",0.0), new TypedParam.DoubleParam("y","Y",0.0), new TypedParam.DoubleParam("z","Z",0.0), new TypedParam.BoolParam("additive","叠加模式",false));
    @Override public String id() { return "set_motion"; }
    @Override public String displayName() { return "设置运动"; }
    @Override public ActionCategory category() { return ActionCategory.MOVEMENT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        LivingEntity target = p.resolveTarget(ctx.maid(), ctx.target());
        if (target == null) return;
        double x = p.getDouble("x"), y = p.getDouble("y"), z = p.getDouble("z");
        if (p.getBool("additive")) target.setDeltaMovement(target.getDeltaMovement().add(x, y, z));
        else MovementOutput.setMotion(target, x, y, z);
        target.hurtMarked = true;
    }
}

