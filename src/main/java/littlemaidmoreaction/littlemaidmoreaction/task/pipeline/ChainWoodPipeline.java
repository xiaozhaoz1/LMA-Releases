package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.input.item.ToolStateReader;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
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
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.is(net.minecraft.tags.BlockTags.LOGS); }
    @Override public boolean needsGameTick() { return true; }
    @Override public void tick(ServerLevel world, EntityMaid maid) {
        if (littlemaidmoreaction.littlemaidmoreaction.task.data.TaskKeys.STATE_CANCELLED.equals(
            littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData.getState(maid))) return;
        ChainHarvestExecute.execute(world, maid, maid.blockPosition(), maid.getPersistentData(), ChainHarvestExecute.Mode.WOOD);
    }
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

    public static IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                return ChainHarvestExecute.execute(w, m, p, d, ChainHarvestExecute.Mode.WOOD);
            }
        };
    }

    /** v67.8: 单女仆采集名单配置 (TLM 任务设置标签页) */
    @Override
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, "collect_wood");
    }

    @Override
    public List<TaskStep> steps() {
        return List.of(
                new TaskStep("search", "寻找树木", StepType.COLLECT, List.of()),
                new TaskStep("chop", "连锁砍伐", StepType.INTERACT, List.of("search"))
        );
    }
}
