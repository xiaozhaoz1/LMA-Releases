package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.item.ToolStateReader;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.service.ToolJudge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 连锁砍树管道 (v36) — 校验主手斧可用。
 * 实际搜索/导航/破坏由 LmaFlowCoordinationBehavior + ChainHarvestExecute 执行。
 */
public final class ChainWoodPipeline implements TaskPipeline {

    @Override
    public String taskType() {
        return "collect_wood";
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        ItemStack tool = maid.getMainHandItem();
        if (!ToolStateReader.isAxe(tool)) {
            return PipelineResult.failed("需要主手持斧才能砍树");
        }
        if (!ToolJudge.isToolUsable(tool, 1)) {
            return PipelineResult.failed("斧耐久不足");
        }
        return PipelineResult.ok("开始连锁砍树");
    }

    @Override
    @Deprecated
    public PipelineResult execute(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        return validate(level, maid, ctx);
    }

    @Override
    public List<TaskStep> steps() {
        return List.of(
                new TaskStep("search", "寻找树木", StepType.COLLECT, List.of()),
                new TaskStep("chop", "连锁砍伐", StepType.INTERACT, List.of("search"))
        );
    }
}
