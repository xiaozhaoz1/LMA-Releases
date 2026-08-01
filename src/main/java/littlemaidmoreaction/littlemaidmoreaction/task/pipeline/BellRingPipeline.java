package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.VanillaTasks;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import littlemaidmoreaction.littlemaidmoreaction.task.service.*;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.ProgressNotifier;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * 敲钟管道 — 处理女仆自动寻找并敲响钟的工作流。
 *
 * <p>v52: isTargetBlock(BellBlock) → LmaFlowCoordinationBehavior 导航 → IExecutor 执行敲钟。</p>
 */
public final class BellRingPipeline implements TaskPipeline {

    @Override public String taskType() { return "bell_ring"; }
    @Override public boolean isLongRunning() { return true; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.getBlock() instanceof net.minecraft.world.level.block.BellBlock; }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("ring", "敲响钟", StepType.INTERACT, List.of())); }

    /** v44: 纯验证 — 敲钟无前置条件，始终可用 */
    @Override
    public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) {
        return PipelineResult.ok("");
    }

    public IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                // v67.5: executor 内节流 — 敲钟专属间隔 (行为层 30tick 地板之上再节流)
                CompoundTag pd = pipelineData(m);
                long now = w.getGameTime();
                long last = pd.getLong(KEY_LAST_RING);
                // v67.13: 单女仆间隔 (pipelineConfig) 非空覆盖全局
                CompoundTag cfg = pipelineConfig(m);
                int interval = cfg.contains(KEY_RING_INTERVAL)
                        ? cfg.getInt(KEY_RING_INTERVAL)
                        : littlemaidmoreaction.littlemaidmoreaction.config.ActiveTaskConfig.BELL_RING_INTERVAL.get();
                if (last != 0 && now - last < interval) return TaskResult.CONTINUE;
                pd.putLong(KEY_LAST_RING, now);
                VanillaTasks.bell(w, m, p);
                return TaskResult.SUCCESS;
            }
        };
    }

    /** v67.5: 上次敲钟 tick (pipelineData, 任务结束自动清除) */
    public static final String KEY_LAST_RING = "last_ring";

    /** v67.13: 单女仆敲钟间隔 (pipelineConfig, 空则用全局 BELL_RING_INTERVAL) */
    public static final String KEY_RING_INTERVAL = "ring_interval";

    /** v67.13: 单女仆敲钟间隔配置 (TLM 任务设置标签页) */
    @Override
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return littlemaidmoreaction.littlemaidmoreaction.task.api.TaskConfigGuiFactory.bellRingConfig(maid);
    }

}
