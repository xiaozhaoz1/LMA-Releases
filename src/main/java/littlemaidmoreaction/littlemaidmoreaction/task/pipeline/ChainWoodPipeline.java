package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.item.ToolStateReader;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import littlemaidmoreaction.littlemaidmoreaction.task.service.ToolJudge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 连锁砍树管道 (v36) — 校验主手斧可用。
 * 实际搜索/导航/破坏由 LmaFlowCoordinationBehavior + ChainHarvestExecute 执行。
 */
public final class ChainWoodPipeline implements TaskPipeline {

    @Override public String taskType() { return "collect_wood"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s) { return s.is(net.minecraft.tags.BlockTags.LOGS); }
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        ItemStack tool = maid.getMainHandItem();
        // v36.1: 无斧不拦截 — 慢砍模式（斧影响速度而非可行性）
        if (!ToolStateReader.isAxe(tool)) {
            return PipelineResult.ok("无斧慢砍模式（持斧砍伐更快且更耐用）");
        }
        if (!ToolJudge.isToolUsable(tool, 1)) {
            return PipelineResult.ok("斧即将损坏，将以慢砍模式作业");
        }
        return PipelineResult.ok("开始连锁砍树");
    }


    @Override
    public List<TaskStep> steps() {
        return List.of(
                new TaskStep("search", "寻找树木", StepType.COLLECT, List.of()),
                new TaskStep("chop", "连锁砍伐", StepType.INTERACT, List.of("search"))
        );
    }
}
