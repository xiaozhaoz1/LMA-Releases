package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.world.WorldOutput;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

@RuleAction
public final class SpawnEntityAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.StringParam("entity_id","实体ID","minecraft:arrow"), new TypedParam.SelectParam("at","位置","self",List.of("self","target")), new TypedParam.IntParam("count","数量",1), new TypedParam.DoubleParam("spread","散布范围",0.5), new TypedParam.StringParam("nbt","NBT",""));
    @Override public String id() { return "spawn_entity"; }
    @Override public String displayName() { return "生成实体"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        LivingEntity at = "target".equals(p.getString("at")) && ctx.target() != null ? ctx.target() : ctx.maid();
        WorldOutput.spawnEntityAt(ctx.maid().level(), p.getString("entity_id"), at.blockPosition(), p.getInt("count"), p.getDouble("spread"), p.getString("nbt"));
    }
}

