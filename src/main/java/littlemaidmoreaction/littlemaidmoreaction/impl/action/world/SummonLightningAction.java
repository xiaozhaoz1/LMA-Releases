package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.world.WorldOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Map;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;

@RuleAction
public final class SummonLightningAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(new TypedParam.SelectParam("at","位置","target",List.of("self","target")), new TypedParam.BoolParam("cosmetic","仅视觉效果",false));
    @Override public String id() { return "summon_lightning"; }
    @Override public String displayName() { return "召唤闪电"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        LivingEntity at = "self".equals(p.getString("at")) ? ctx.maid() : ctx.target();
        if (at == null) return;
        WorldOutput.summonLightning(ctx.maid().level(), at.blockPosition(), p.getBool("cosmetic"));
    }
}

