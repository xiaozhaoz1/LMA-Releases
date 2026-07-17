package littlemaidmoreaction.littlemaidmoreaction.impl.action.visual;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.visual.VisualOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

/** 生成粒子 — 委托给 {@link VisualOutput#spawnParticleServer}. */
@RuleAction
public final class SpawnParticleAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("particle_id","粒子ID","minecraft:crit"),
        new TypedParam.IntParam("count","数量",10),
        new TypedParam.DoubleParam("delta_x","X范围",0.5),
        new TypedParam.DoubleParam("delta_y","Y范围",0.5),
        new TypedParam.DoubleParam("delta_z","Z范围",0.5),
        new TypedParam.DoubleParam("speed","速度",0.1),
        new TypedParam.SelectParam("at","位置","self",List.of("self","target","owner")));
    @Override public String id() { return "spawn_particle"; }
    @Override public String displayName() { return "生成粒子"; }
    @Override public ActionCategory category() { return ActionCategory.VISUAL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        if (!(ctx.maid().level() instanceof ServerLevel lv)) return;
        var p = ParamExtractor.from(raw, PARAMS);
        VisualOutput.spawnParticleServer(lv, p.getString("particle_id"),
            ctx.maid().getX(), ctx.maid().getY()+1, ctx.maid().getZ(),
            p.getInt("count"), p.getDouble("delta_x"), p.getDouble("delta_y"), p.getDouble("delta_z"), p.getDouble("speed"));
    }
}

