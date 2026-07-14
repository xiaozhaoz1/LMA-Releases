package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.adapter.tlm.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** v31: 委托任务系统 — 写 PersistentData 启动 altar_craft 任务 */
@RuleAction
public final class PlaceAltarItemAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("item_id", "物品ID", "minecraft:coal"),
        new TypedParam.IntParam("range", "搜索范围", 10)
    );
    @Override public String id() { return "place_altar_item"; }
    @Override public String displayName() { return "放置祭坛物品(v31→任务系统)"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        EntityMaid maid = ctx.maid();
        if (maid.level().isClientSide()) return;
        var p = ParamExtractor.from(raw, PARAMS);
        String target = p.getString("item_id");
        var data = maid.getPersistentData();
        data.putString("lma_flow_task", "altar_craft");
        data.putString("lma_task_target", target);
        data.putString("lma_flow_state", "in_progress");
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        maid.setTask(LmaTaskTypeRegistry.findByTaskType("altar_craft"));
    }
}
