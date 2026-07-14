package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.adapter.tlm.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/** v31: 委托任务系统 — 写 PersistentData 启动 jukebox 任务 */
@RuleAction
public class JukeboxInteractAction extends AbstractFunctionalBlockInteraction {

    @Override public String id() { return "jukebox_interact"; }
    @Override public String displayName() { return "唱片机互动(v31→任务系统)"; }
    @Override protected String defaultBlockId() { return "minecraft:jukebox"; }
    @Override protected List<String> validActions() { return List.of(); }

    @Override
    protected void doInteract(BlockPos pos, BlockState state, EntityMaid maid,
                               Map<String, String> params, String action) {
        String wantMusic = params.getOrDefault("music_name", "");
        var data = maid.getPersistentData();
        data.putString("lma_flow_task", "jukebox");
        if (!wantMusic.isEmpty()) data.putString("lma_task_target", wantMusic);
        data.putString("lma_flow_state", "in_progress");
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        maid.setTask(LmaTaskTypeRegistry.findByTaskType("jukebox"));
    }
}
