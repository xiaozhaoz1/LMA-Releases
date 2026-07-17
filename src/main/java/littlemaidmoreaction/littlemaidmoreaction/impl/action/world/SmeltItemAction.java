package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** v31: 委托任务系统 — 写 PersistentData 启动 furnace 任务 */
@RuleAction
public final class SmeltItemAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("item_id", "要烧炼的物品ID", "minecraft:iron_ore"),
        new TypedParam.StringParam("fuel_id", "燃料物品ID", "minecraft:coal"),
        new TypedParam.IntParam("range", "搜索范围", 16)
    );
    @Override public String id() { return "smelt_item"; }
    @Override public String displayName() { return "熔炉烧炼(v31→任务系统)"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return;
        var p = ParamExtractor.from(raw, PARAMS);
        startFurnaceTask(maid, p.getString("item_id"));
    }

    static void startFurnaceTask(EntityMaid maid, String input) {
        var data = maid.getPersistentData();
        data.putString("lma_flow_task", "furnace");
        data.putString("lma_task_input", input);
        data.putString("lma_flow_state", "in_progress");
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        maid.setTask(LmaTaskTypeRegistry.findByTaskType("furnace"));
    }
}
