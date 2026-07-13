package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.world.WorldOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.Map;

/** 村民交易 — 委托给 {@link WorldOutput#tradeWithVillager}. */
@RuleAction
public final class TradeVillagerAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("profession", "村民职业", "any"),
        new TypedParam.IntParam("range", "搜索范围", 16)
    );
    @Override public String id() { return "trade_villager"; }
    @Override public String displayName() { return "村民交易"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        if (ctx.maid().level().isClientSide()) return;
        var p = ParamExtractor.from(raw, PARAMS);
        var owner = ctx.maid().getOwner();
        if (owner instanceof Player player) WorldOutput.tradeWithVillager(ctx.maid().level(),
            ctx.maid().blockPosition(), p.getString("profession"), p.getInt("range"), player);
    }
}
