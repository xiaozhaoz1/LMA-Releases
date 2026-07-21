package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskDispatcher;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** v44: 委托任务系统 — 通过 TaskDispatcher.submit() 启动 furnace 任务 */
@RuleAction
public final class SmeltItemAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("item_id", "要烧炼的物品ID", "minecraft:iron_ore"),
        new TypedParam.StringParam("fuel_id", "燃料物品ID", "minecraft:coal"),
        new TypedParam.IntParam("range", "搜索范围", 16)
    );
    @Override public String id() { return "smelt_item"; }
    @Override public String displayName() { return "熔炉烧炼(v44→调度层)"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return;
        var p = ParamExtractor.from(raw, PARAMS);
        TaskDispatcher.submit(maid, TaskKeys.TASK_FURNACE, p.getString("item_id"), 0);
    }
}
