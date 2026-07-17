package littlemaidmoreaction.littlemaidmoreaction.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/** v31: 委托任务系统 — 写 PersistentData 启动 bell_ring 任务 */
@RuleAction
public class BellRingAction extends AbstractFunctionalBlockInteraction {

    @Override public String id() { return "bell_ring"; }
    @Override public String displayName() { return "敲钟(v31→任务系统)"; }
    @Override protected String defaultBlockId() { return "minecraft:bell"; }
    @Override protected List<String> validActions() { return List.of("ring"); }

    @Override
    protected void doInteract(BlockPos pos, BlockState state, EntityMaid maid,
                               Map<String, String> params, String action) {
        var data = maid.getPersistentData();
        data.putString("lma_flow_task", "bell_ring");
        data.putString("lma_flow_state", "in_progress");
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        maid.setTask(LmaTaskTypeRegistry.findByTaskType("bell_ring"));
    }
}
