package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.adapter.tlm.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** v31: 委托任务系统 — 写 PersistentData 启动 craft_chain 任务 */
@RuleAction
public class CraftingTableInteractAction extends AbstractFunctionalBlockInteraction {

    @Override public String id() { return "craft_chain"; }
    @Override public String displayName() { return "工作台合成(v31→任务系统)"; }
    @Override protected String defaultBlockId() { return "minecraft:crafting_table"; }
    @Override protected List<String> validActions() { return List.of(); }

    @Override
    public List<TypedParam<?>> params() {
        List<TypedParam<?>> all = new ArrayList<>();
        all.addAll(super.params());
        return List.copyOf(all);
    }

    @Override
    protected void doInteract(BlockPos pos, BlockState state, EntityMaid maid,
                               Map<String, String> params, String action) {
        String target = params.getOrDefault("item_id", "");
        var data = maid.getPersistentData();
        data.putString("lma_flow_task", "craft_chain");
        if (!target.isEmpty()) data.putString("lma_task_target", target);
        data.putString("lma_flow_state", "in_progress");
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        maid.setTask(LmaTaskTypeRegistry.findByTaskType("craft_chain"));
    }
}
