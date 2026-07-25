package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * 祭坛合成管道 (v44) — 骨架实现，委派规则引擎处理实际合成。
 */
public final class AltarCraftPipeline implements TaskPipeline {

    @Override public String taskType() { return "altar_craft"; }

    @Override
    public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) {
        return PipelineResult.ok("");
    }

    public static IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                return TaskResult.SUCCESS;
            }
            @Override public void onStop(EntityMaid maid) {}
        };
    }
}
