package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.*;

import java.util.List;

/**
 * 敲钟管道 — 处理女仆自动寻找并敲响钟的工作流。
 *
 * <p>v52: isTargetBlock(BellBlock) → LmaFlowCoordinationBehavior 导航 → IExecutor 执行敲钟。
 * v79.45: 工作站基类 — GMPM 驱动, 节拍/计数/完成归 WorkStationPipeline (节流仍内部)。
 */
public final class BellRingPipeline extends WorkStationPipeline implements TaskConfigurable {

    @Override public String taskType() { return "bell_ring"; }
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.getBlock() instanceof net.minecraft.world.level.block.BellBlock; }
    @Override public List<TaskStep> steps() { return List.of(new TaskStep("ring", "敲响钟", StepType.INTERACT, List.of())); }

    /** 纯验证 — 敲钟无前置条件，始终可用 */
    @Override
    public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) {
        return PipelineResult.ok("");
    }

    /** 一次工作单元 (原 execute 迁入; 节流 + 敲钟, SUCCESS 计数链由基类 countSuccess 处理) */
    @Override
    protected TaskResult executeOne(ServerLevel w, EntityMaid m, BlockPos p) {
        // 节流 — 敲钟专属间隔 (行为层 30tick 地板之上再节流), 统一节流工具,
        // 单女仆间隔 (pipelineConfig) 非空覆盖全局 (演化史见 changelog)
        CompoundTag cfg = pipelineConfig(m);
        int interval = cfg.contains(KEY_RING_INTERVAL)
                ? cfg.getInt(KEY_RING_INTERVAL)
                : com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.BELL_RING_INTERVAL.get();
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                .shouldFire(m, "bell_ring", interval)) {
            return TaskResult.CONTINUE;
        }
        // v79.61x execute 瘦身: 原 BellExecute 内联 (敲钟 = BellBlock 原语调用)
        BlockState state = w.getBlockState(p);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.BellBlock bell)) {
            return TaskResult.SUCCESS; // 原语义: 非钟时 execute false 被忽略, 仍计数
        }
        bell.attemptToRing(m, w, p, null);
        w.playSound(null, p, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS,
                com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.BELL_VOLUME.get().floatValue(),
                com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.BELL_PITCH.get().floatValue());
        return TaskResult.SUCCESS;
    }

    /** 单女仆敲钟间隔 (pipelineConfig, 空则用全局 BELL_RING_INTERVAL) */
    public static final String KEY_RING_INTERVAL = "ring_interval";

    /** 单女仆敲钟间隔配置 (TLM 任务设置标签页) */
    @Override
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory.bellRingConfig(maid);
    }

}
