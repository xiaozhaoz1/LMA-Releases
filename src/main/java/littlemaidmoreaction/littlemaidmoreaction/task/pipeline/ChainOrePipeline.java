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
import littlemaidmoreaction.littlemaidmoreaction.task.service.ToolJudge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 连锁挖矿管道 (v36) — 校验主手镐可用。
 * 挖掘等级判定（镐材质 vs 矿石门槛）在到达矿脉后由 ToolJudge.canPickaxeMine 逐块执行。
 */
public final class ChainOrePipeline implements TaskPipeline {

    @Override public String taskType() { return "collect_ore"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.is(net.minecraftforge.common.Tags.Blocks.ORES); }
    @Override public boolean needsGameTick() { return true; }
    @Override public void tick(ServerLevel world, EntityMaid maid) {
        if (littlemaidmoreaction.littlemaidmoreaction.task.data.TaskKeys.STATE_CANCELLED.equals(
            littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData.getState(maid))) return;
        ChainHarvestExecute.execute(world, maid, maid.blockPosition(), maid.getPersistentData(), ChainHarvestExecute.Mode.ORE);
    }
    @Override public boolean isLongRunning() { return true; }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        ItemStack tool = maid.getMainHandItem();
        if (!ToolStateReader.isPickaxe(tool)) {
            return PipelineResult.failed("需要主手持镐才能挖矿");
        }
        if (!ToolJudge.isToolUsable(tool, 1)) {
            return PipelineResult.failed("镐耐久不足");
        }
        return PipelineResult.ok("开始连锁挖矿");
    }

    public static IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                return ChainHarvestExecute.execute(w, m, p, d, ChainHarvestExecute.Mode.ORE);
            }
        };
    }

    @Override
    public List<TaskStep> steps() {
        return List.of(
                new TaskStep("search", "寻找矿石", StepType.COLLECT, List.of()),
                new TaskStep("mine", "连锁挖掘", StepType.INTERACT, List.of("search"))
        );
    }
}
