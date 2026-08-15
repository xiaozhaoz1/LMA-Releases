package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskSignalListener;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
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
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 女仆右键交互管道 — 女仆对绑定方块执行右键交互。
 *
 * <p>无导航, 配置距离内直接右键。支持手动按键触发 + 定时器触发。
 * 配置存 {@link #pipelineConfig} (键 lma_cfg_block_interact, 跨任务持久)。
 *
 * <p>FSM 写法 (v79.61x 收敛): WAITING 单态自环, tick 内联 (无处理器表/上下文间接);
 * onPlayerTrigger (手动按键) 不进状态机 (引擎直调)。
 *
 * <h3>配置 (pipelineConfig)</h3>
 * <ul>
 *   <li>{@value #KEY_POS} — 绑定方块坐标 (NbtUtils blockPos)</li>
 *   <li>{@value #KEY_TIMER_ENABLED} — 定时器开关 (false=关)</li>
 *   <li>{@value #KEY_TIMER_INTERVAL} — 定时器间隔 (tick, 默认见 {@link ActiveTaskConfig#BI_TIMER_DEFAULT_INTERVAL})</li>
 * </ul>
 */
public final class BlockInteractPipeline extends TaskStateMachine<BlockInteractPipeline.State> implements TaskConfigurable, TaskSignalListener {

    enum State { WAITING }

    public static final String KEY_POS = "pos";
    public static final String KEY_TIMER_ENABLED = "timer";
    public static final String KEY_TIMER_INTERVAL = "interval";

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.WAITING; }
    @Override public String taskType() { return "block_interact"; }
    /** 固定工作点任务 (工作站交互) — TLM 骑乘中不脱离坐骑 */
    @Override public boolean workPointTask() { return true; }
    @Override public boolean isLongRunning() { return true; }

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
        // 双平台编解码样板收敛 (api/nbt/NbtCodecs)
        BlockPos pos = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(cfg, KEY_POS);
        if (pos != null && maid.level() instanceof ServerLevel world) {
            interactBound(world, maid, pos);
        }
    }

    /** 绑定方块交互 — 存在检查 (任务语义) + 全局右键门面 (距离+交互) */
    private static void interactBound(ServerLevel world, EntityMaid maid, BlockPos pos) {
        // 2026-08-11c 互斥 (全景 #8): 按键 (onPlayerTrigger) 与定时器 (handleWaiting)
        // 同 tick 双触发去重 — 1t 间隔 = 同 tick 第二次跳过, 下一 tick 放行
        if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid.ThrottleUtil
                .shouldFire(maid, "block_interact_interact", 1)) {
            return;
        }
        if (world.getBlockState(pos).isAir()) {
            // 被破坏 → 气泡提示 + 清除绑定 (原 BlockInteractService 内, 任务语义回归管线)
            com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi.showFail(maid, "绑定方块已丢失");
            com.github.xiaozhaoz1.littlemaidmoreaction.task.service.TaskConfigs.get(maid, "block_interact").remove(KEY_POS);
            return;
        }
        BlockInteractService.interact(world, maid, pos);
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        CompoundTag cfg = pipelineConfig(maid);
        if (!cfg.contains(KEY_POS))
            return PipelineResult.failed("未绑定交互方块");
        BlockPos pos = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(cfg, KEY_POS);
        // v79.48 修复 #10.4: pos 读取 null (NBT 损坏) → 显式 failed (原 null 通过 = 无目标空转)
        if (pos == null) return PipelineResult.failed("绑定方块位置无效");
        if (!pos.closerToCenterThan(maid.position(), ActiveTaskConfig.BI_INTERACT_DISTANCE.get()))
            return PipelineResult.failed("绑定方块不在交互距离内");
        return PipelineResult.ok("");
    }

    /** 状态机驱动 — 单状态自环, 每 tick 定时器判定 + 交互 */
    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        CompoundTag cfg = com.github.xiaozhaoz1.littlemaidmoreaction.task.service.TaskConfigs.get(maid, "block_interact");
        if (!cfg.getBoolean(KEY_TIMER_ENABLED)) return null;
        int interval = cfg.getInt(KEY_TIMER_INTERVAL);
        if (interval <= 0) {
            interval = ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.get();
            cfg.putInt(KEY_TIMER_INTERVAL, interval);
        }
        if (world.getGameTime() % interval != 0) return null;
        if (!cfg.contains(KEY_POS)) return null;
        BlockPos pos = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs.readBlockPos(cfg, KEY_POS);
        if (pos != null) {
            interactBound(world, maid, pos);
        }
        return null;
    }


    @Override @Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.blockInteractConfig(maid);
    }
    // interrupt / getConfigNbt / handleConfigAction 用接口 default (onCleanup / pipelineConfig / 引擎通用动作)

    // ── 执行器 ──

}
