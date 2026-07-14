package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.furnace.SmeltExecute;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.Map;

/** 熔炉烧炼 — 委托给 {@link SmeltExecute#execute}. */
@RuleAction
public final class SmeltItemAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("item_id", "要烧炼的物品ID", "minecraft:iron_ore"),
        new TypedParam.StringParam("fuel_id", "燃料物品ID", "minecraft:coal"),
        new TypedParam.IntParam("range", "搜索范围", 16)
    );
    @Override public String id() { return "smelt_item"; }
    @Override public String displayName() { return "熔炉烧炼"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        if (!(ctx.maid().level() instanceof ServerLevel sl)) return;
        var p = ParamExtractor.from(raw, PARAMS);
        SmeltExecute.execute(sl, ctx.maid(), p.getString("item_id"), p.getString("fuel_id"), p.getInt("range"),
            littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.FurnaceSlotMapping.VANILLA);
    }
}
