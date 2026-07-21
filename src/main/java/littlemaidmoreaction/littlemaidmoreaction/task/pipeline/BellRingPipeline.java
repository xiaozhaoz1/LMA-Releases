package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import littlemaidmoreaction.littlemaidmoreaction.task.service.*;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.ProgressNotifier;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * 敲钟管道 — 处理女仆自动寻找并敲响钟的工作流。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>写入规则文件（触发后续行为）</li>
 *   <li>TODO: Phase 2 — 寻找钟 → 导航 → 敲钟</li>
 * </ol>
 *
 * <p>Phase 2 将实现完整的导航和敲钟循环。当前为骨架实现。</p>
 */
public final class BellRingPipeline implements TaskPipeline {

    @Override public String taskType() { return "bell_ring"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s) { return s.getBlock() instanceof net.minecraft.world.level.block.BellBlock; }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("ring", "敲响钟", StepType.INTERACT, List.of())); }

    /** v44: 纯验证 — 敲钟无前置条件，始终可用 */
    @Override
    public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) {
        return PipelineResult.ok("");
    }

}
