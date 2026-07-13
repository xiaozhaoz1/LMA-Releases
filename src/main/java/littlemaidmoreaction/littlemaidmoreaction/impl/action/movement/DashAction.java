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

/** 冲刺 — 沿面朝方向快速位移。参数: distance/speed_mult/toward_target */
@RuleAction
public final class DashAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.DoubleParam("distance","距离",3.0), new TypedParam.DoubleParam("speed_mult","速度倍率",1.5), new TypedParam.BoolParam("toward_target","朝向目标",true));
    @Override public String id() { return "dash"; }
    @Override public String displayName() { return "冲刺"; }
    @Override public ActionCategory category() { return ActionCategory.MOVEMENT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> params) {
        double dist = parseDouble(params.getOrDefault("distance","3.0"), 3.0), mult = parseDouble(params.getOrDefault("speed_mult","1.5"), 1.5);
        boolean toward = Boolean.parseBoolean(params.getOrDefault("toward_target","true"));
        Vec3 dir; if (toward && ctx.target() != null) { dir = ctx.target().position().subtract(ctx.maid().position()); double h = dir.horizontalDistance(); dir = h > 0.001 ? new Vec3(dir.x/h, 0, dir.z/h) : ctx.maid().getLookAngle(); }
        else dir = ctx.maid().getLookAngle().multiply(1,0,1).normalize();
        ctx.maid().setDeltaMovement(dir.x * dist * mult, 0.2, dir.z * dist * mult); ctx.maid().hurtMarked = true;
    }
}

