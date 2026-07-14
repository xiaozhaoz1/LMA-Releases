package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.adapter.tlm.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.SlotLayout;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/** v31: 委托任务系统 — 写 PersistentData 启动 furnace 任务 */
@RuleAction
public class FurnaceInteractAction extends AbstractFunctionalBlockInteraction {

    @Override public String id() { return "furnace_interact"; }
    @Override public String displayName() { return "熔炉互动(v31→任务系统)"; }
    @Override protected String defaultBlockId() { return "minecraft:furnace"; }

    @Override
    public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() {
        var list = new java.util.ArrayList<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>>();
        list.addAll(super.params());
        list.add(new littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam.StringParam("fuel_item", "燃料ID(可选)", ""));
        return List.copyOf(list);
    }

    @Override
    protected List<String> validActions() { return List.of(); }

    @Override
    protected void doInteract(BlockPos pos, BlockState state, EntityMaid maid,
                               Map<String, String> params, String action) {
        String input = params.getOrDefault("item_id", "");
        var data = maid.getPersistentData();
        data.putString("lma_flow_task", "furnace");
        data.putString("lma_task_input", input);
        data.putString("lma_flow_state", "in_progress");
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        maid.setTask(LmaTaskTypeRegistry.findByTaskType("furnace"));
    }
}
