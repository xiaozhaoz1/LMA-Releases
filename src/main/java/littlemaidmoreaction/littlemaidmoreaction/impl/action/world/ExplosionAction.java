package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.world.WorldOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

@RuleAction
public final class ExplosionAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.DoubleParam("power","威力",2.0), new TypedParam.BoolParam("destroy_blocks","破坏方块",false), new TypedParam.BoolParam("set_fire","点燃",false), new TypedParam.SelectParam("at","位置","target",List.of("self","target")));
    @Override public String id() { return "explosion"; }
    @Override public String displayName() { return "爆炸"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        float power = (float) p.getDouble("power");
        boolean destroy = p.getBool("destroy_blocks");
        boolean fire = p.getBool("set_fire");
        LivingEntity at = "self".equals(p.getString("at")) ? ctx.maid() : ctx.target();
        if (at == null) at = ctx.maid();
        WorldOutput.createExplosion(ctx.maid().level(), ctx.maid(), at.blockPosition(), power, fire, destroy);
    }
}

