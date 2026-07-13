package littlemaidmoreaction.littlemaidmoreaction.impl.action.movement;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import static littlemaidmoreaction.littlemaidmoreaction.engine.EngineUtils.*;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

@RuleAction
public final class LeapAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.DoubleParam("horizontal","水平",1.0), new TypedParam.DoubleParam("vertical","垂直",0.5), new TypedParam.BoolParam("toward_target","朝向目标",true));
    @Override public String id() { return "leap"; }
    @Override public String displayName() { return "跳跃冲刺"; }
    @Override public ActionCategory category() { return ActionCategory.MOVEMENT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> params) {
        double h = parseDouble(params.getOrDefault("horizontal","1.0"),1.0), v = parseDouble(params.getOrDefault("vertical","0.5"),0.5);
        boolean toward = Boolean.parseBoolean(params.getOrDefault("toward_target","true"));
        Vec3 d; if (toward && ctx.target() != null) { d = ctx.target().position().subtract(ctx.maid().position()); double dist = d.horizontalDistance(); d = dist > 0.001 ? new Vec3(d.x/dist, 0, d.z/dist) : ctx.maid().getLookAngle(); }
        else d = ctx.maid().getLookAngle().multiply(1,0,1).normalize();
        ctx.maid().setDeltaMovement(d.x*h, v, d.z*h); ctx.maid().hurtMarked = true;
    }
}

