package littlemaidmoreaction.littlemaidmoreaction.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * 通用任务提交动作 (v50: 合并4个旧类).
 * <p>参数: task_type (必填), target (可选), count (可选).
 */
@RuleAction
public class SubmitTaskAction extends AbstractBlockInteraction {

    @Override public String id() { return "submit_task"; }
    @Override public String displayName() { return "提交任务"; }
    @Override public ActionCategory category() { return ActionCategory.WORLD; }

    @Override
    protected void interact(BlockPos pos, BlockState state, EntityMaid maid,
                            Map<String, String> params) {
        String taskType = params.get("task_type");
        if (taskType == null || taskType.isEmpty()) return;

        if (!(maid.level() instanceof ServerLevel)) return;

        String target = params.getOrDefault("target", "");
        int count = params.containsKey("count")
            ? Integer.parseInt(params.get("count")) : 0;

        TaskDispatcher.submit(maid, taskType, target, count);
    }
}
