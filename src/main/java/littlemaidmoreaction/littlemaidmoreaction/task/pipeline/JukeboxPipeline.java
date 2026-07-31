package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.VanillaTasks;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskMetaData;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import littlemaidmoreaction.littlemaidmoreaction.task.service.*;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.ProgressNotifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 唱片机管道 (Phase 2) — 扫描唱片 → 写规则 → JukeboxInteractAction 处理导航+弹出入碟。
 */
public final class JukeboxPipeline implements TaskPipeline {

    @Override public String taskType() { return "jukebox"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.is(net.minecraft.world.level.block.Blocks.JUKEBOX); }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("play", "播放唱片", StepType.INTERACT, List.of())); }

    /** v44: 纯验证 — 仅扫描背包是否有唱片(读操作)，不写日志/通知 */
    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        String target = ctx.target();
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.is(ItemTags.MUSIC_DISCS)) {
                if (target.isEmpty()) return PipelineResult.ok("");
                if (s.getDescriptionId().contains(target) || s.getItem().toString().contains(target))
                    return PipelineResult.ok("");
            }
        }
        return PipelineResult.failed(ProgressNotifier.NO_DISC);
    }

    public static IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                VanillaTasks.jukebox(w, m, p, TaskMetaData.getTarget(m));
                return TaskResult.CONTINUE;
            }
        };
    }

}
