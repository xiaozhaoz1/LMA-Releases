package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.container.ContainerExecute;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.Map;

/** 容器取物 — 委托给 {@link ContainerExecute#withdraw}. */
@RuleAction
public final class TakeFromContainerAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("item_id", "物品ID", "minecraft:coal"),
        new TypedParam.IntParam("count", "数量", 1),
        new TypedParam.IntParam("range", "搜索范围", 16)
    );
    @Override public String id() { return "take_from_container"; }
    @Override public String displayName() { return "容器取物"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        if (!(ctx.maid().level() instanceof ServerLevel sl)) return;
        var p = ParamExtractor.from(raw, PARAMS);
        ContainerExecute.withdraw(sl, ctx.maid(), p.getString("item_id"), p.getInt("count"), p.getInt("range"));
    }
}
