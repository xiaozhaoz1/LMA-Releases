package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import littlemaidmoreaction.littlemaidmoreaction.task.service.BlockInteractService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import littlemaidmoreaction.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * 女仆右键交互管道 — 女仆对绑定方块执行右键交互。
 *
 * <p>无导航, 配置距离内直接右键。支持手动按键触发 + 定时器触发。
 * 配置存 {@link #pipelineConfig} (键 lma_cfg_block_interact, 跨任务持久)。
 *
 * <h3>配置 (pipelineConfig)</h3>
 * <ul>
 *   <li>{@value #KEY_POS} — 绑定方块坐标 (NbtUtils blockPos)</li>
 *   <li>{@value #KEY_TIMER_ENABLED} — 定时器开关 (false=关)</li>
 *   <li>{@value #KEY_TIMER_INTERVAL} — 定时器间隔 (tick, 默认见 {@link MoreActionConfig#BI_TIMER_DEFAULT_INTERVAL})</li>
 * </ul>
 *
 * <h3>全局设置 (v67.2, Cloth Config)</h3>
 * 交互距离/触发范围/定时器默认间隔/标记物品/绑定物品 — 见 {@link MoreActionConfig} block_interact 段。
 */
public final class BlockInteractPipeline implements TaskPipeline {

    public static final String KEY_POS = "pos";
    public static final String KEY_TIMER_ENABLED = "timer";
    public static final String KEY_TIMER_INTERVAL = "interval";

    @Override public String taskType() { return "block_interact"; }
    @Override public boolean isLongRunning() { return true; }
    @Override public boolean needsGameTick() { return true; }

    // ── Pipeline 实现 ──

    /**
     * 玩家按键触发 (引擎分发: InteractTriggerPacket → TaskRegistry → 本方法)。
     * 立即执行一次右键交互。
     */
    @Override
    public void onPlayerTrigger(EntityMaid maid, ServerPlayer player) {
        CompoundTag cfg = pipelineConfig(maid);
        if (!cfg.contains(KEY_POS)) return;
        BlockPos pos = NbtUtils.readBlockPos(cfg.getCompound(KEY_POS));
        if (maid.level() instanceof ServerLevel world) {
            BlockInteractService.interact(world, maid, pos);
        }
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        CompoundTag cfg = pipelineConfig(maid);
        if (!cfg.contains(KEY_POS))
            return PipelineResult.failed("未绑定交互方块");
        BlockPos pos = NbtUtils.readBlockPos(cfg.getCompound(KEY_POS));
        if (!pos.closerToCenterThan(maid.position(), ActiveTaskConfig.BI_INTERACT_DISTANCE.get()))
            return PipelineResult.failed("绑定方块不在交互距离内");
        return PipelineResult.ok("");
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        CompoundTag cfg = pipelineConfig(maid);
        if (!cfg.getBoolean(KEY_TIMER_ENABLED)) return;
        int interval = cfg.getInt(KEY_TIMER_INTERVAL);
        if (interval <= 0) {
            interval = ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.get();
            cfg.putInt(KEY_TIMER_INTERVAL, interval);
        }
        if (world.getGameTime() % interval != 0) return;
        if (!cfg.contains(KEY_POS)) return;
        BlockPos pos = NbtUtils.readBlockPos(cfg.getCompound(KEY_POS));
        BlockInteractService.interact(world, maid, pos);
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
    public static IExecutor executor() {
        return (w, m, p, d) -> TaskResult.SUCCESS;
    }
}
