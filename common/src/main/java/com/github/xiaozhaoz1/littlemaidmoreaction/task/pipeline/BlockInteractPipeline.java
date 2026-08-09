package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.io.IExecutor;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateMachine;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.BlockInteractService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 女仆右键交互管道 — 女仆对绑定方块执行右键交互。
 *
 * <p>无导航, 配置距离内直接右键。支持手动按键触发 + 定时器触发。
 * 配置存 {@link #pipelineConfig} (键 lma_cfg_block_interact, 跨任务持久)。
 *
 * <p>v79.7 状态机化: WAITING 单态 (定时器态) — 状态处理器表 + StateCtx 示范模式;
 * onPlayerTrigger (手动按键) 不进状态机 (引擎直调)。行为零变化。
 *
 * <h3>配置 (pipelineConfig)</h3>
 * <ul>
 *   <li>{@value #KEY_POS} — 绑定方块坐标 (NbtUtils blockPos)</li>
 *   <li>{@value #KEY_TIMER_ENABLED} — 定时器开关 (false=关)</li>
 *   <li>{@value #KEY_TIMER_INTERVAL} — 定时器间隔 (tick, 默认见 {@link MoreActionConfig#BI_TIMER_DEFAULT_INTERVAL})</li>
 * </ul>
 */
public final class BlockInteractPipeline extends TaskStateMachine<BlockInteractPipeline.State> {

    enum State { WAITING }

    /** 状态处理器表 (v79.7 示范模式) */
    private static final Map<State, Function<StateCtx, State>> HANDLERS = Map.of(
            State.WAITING, BlockInteractPipeline::handleWaiting);

    private record StateCtx(EntityMaid maid, ServerLevel world) {}

    public static final String KEY_POS = "pos";
    public static final String KEY_TIMER_ENABLED = "timer";
    public static final String KEY_TIMER_INTERVAL = "interval";

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.WAITING; }
    @Override public String taskType() { return "block_interact"; }
    @Override public boolean isLongRunning() { return true; }
    @Override public boolean needsGameTick() { return true; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(State.WAITING, Set.of(State.WAITING));
    }

    // ── Pipeline 实现 ──

    /**
     * 玩家按键触发 (引擎分发: InteractTriggerPacket → TaskRegistry → 本方法)。
     * 立即执行一次右键交互。
     */
    @Override
    public void onPlayerTrigger(EntityMaid maid, ServerPlayer player) {
        CompoundTag cfg = pipelineConfig(maid);
        if (!cfg.contains(KEY_POS)) return;
        // v79.5: 双平台编解码样板收敛 (api/nbt/NbtCodecs)
        BlockPos pos = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(cfg, KEY_POS);
        if (pos != null && maid.level() instanceof ServerLevel world) {
            BlockInteractService.interact(world, maid, pos);
        }
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        CompoundTag cfg = pipelineConfig(maid);
        if (!cfg.contains(KEY_POS))
            return PipelineResult.failed("未绑定交互方块");
        BlockPos pos = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(cfg, KEY_POS);
        if (pos != null && !pos.closerToCenterThan(maid.position(), ActiveTaskConfig.BI_INTERACT_DISTANCE.get()))
            return PipelineResult.failed("绑定方块不在交互距离内");
        return PipelineResult.ok("");
    }

    /** 状态机驱动 — 表分发 (每次 tick 定时器判定 + 交互) */
    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        return HANDLERS.get(s).apply(new StateCtx(maid, world));
    }

    /** WAITING — 定时器到点 → 交互 (自环停留) */
    private static State handleWaiting(StateCtx ctx) {
        CompoundTag cfg = pipelineConfigOf(ctx.maid());
        if (!cfg.getBoolean(KEY_TIMER_ENABLED)) return null;
        int interval = cfg.getInt(KEY_TIMER_INTERVAL);
        if (interval <= 0) {
            interval = ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.get();
            cfg.putInt(KEY_TIMER_INTERVAL, interval);
        }
        if (ctx.world().getGameTime() % interval != 0) return null;
        if (!cfg.contains(KEY_POS)) return null;
        BlockPos pos = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(cfg, KEY_POS);
        if (pos != null) {
            BlockInteractService.interact(ctx.world(), ctx.maid(), pos);
        }
        return null;
    }

    /** 管线配置读取 (静态处理器经 maid 取当前管线) */
    private static CompoundTag pipelineConfigOf(EntityMaid maid) {
        return com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry
                .get(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(maid))
                .pipeline().pipelineConfig(maid);
    }

    @Override @Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.blockInteractConfig(maid);
    }
    // interrupt / getConfigNbt / handleConfigAction 用接口 default (onCleanup / pipelineConfig / 引擎通用动作)

    // ── 执行器 ──

    /**
     * 执行器 — 仅心跳维持任务存活。
     * 实际交互由 {@link #tick} (定时器) 和 {@link #onPlayerTrigger} (手动按键) 触发。
     */
    @Override
    public IExecutor executor() {
        return (w, m, p, d) -> TaskResult.SUCCESS;
    }
}
