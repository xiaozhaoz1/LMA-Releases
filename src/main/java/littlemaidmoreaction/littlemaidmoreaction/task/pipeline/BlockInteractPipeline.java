package littlemaidmoreaction.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.data.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.gui.TaskConfigGui;
import littlemaidmoreaction.littlemaidmoreaction.task.service.BlockInteractService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

/**
 * 女仆右键交互管道 — 女仆对绑定方块执行右键交互。
 *
 * <p>无导航, 5格内直接右键。支持手动按键触发 + 定时器触发。
 *
 * <h3>配置 (pipelineConfig)</h3>
 * <ul>
 *   <li>{@value #KEY_POS} — 绑定方块坐标 (NbtUtils blockPos)</li>
 *   <li>{@value #KEY_TIMER_ENABLED} — 定时器开关 (false=关)</li>
 *   <li>{@value #KEY_TIMER_INTERVAL} — 定时器间隔 (tick, 默认200=10秒)</li>
 * </ul>
 */
public final class BlockInteractPipeline implements TaskPipeline {

    static final String CONFIG_KEY = "lma_cfg_block_interact";
    public static final String KEY_POS = "pos";
    public static final String KEY_TIMER_ENABLED = "timer";
    public static final String KEY_TIMER_INTERVAL = "interval";
    static final int DEFAULT_INTERVAL = 200; // 10秒

    @Override public String taskType() { return "block_interact"; }
    @Override public boolean isLongRunning() { return true; }
    @Override public boolean needsGameTick() { return true; }

    // ── 静态工具 — Service/SetupHandler 等静态上下文使用 ──

    /** 静态访问 pipelineConfig */
    public static CompoundTag config(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        if (!data.contains(CONFIG_KEY)) data.put(CONFIG_KEY, new CompoundTag());
        return data.getCompound(CONFIG_KEY);
    }

    // ── Pipeline 实现 ──

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        CompoundTag cfg = config(maid);
        if (!cfg.contains(KEY_POS))
            return PipelineResult.failed("未绑定交互方块");
        BlockPos pos = NbtUtils.readBlockPos(cfg.getCompound(KEY_POS));
        if (!pos.closerToCenterThan(maid.position(), 5.0))
            return PipelineResult.failed("绑定方块不在5格内");
        return PipelineResult.ok("");
    }

    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        CompoundTag cfg = config(maid);
        if (!cfg.getBoolean(KEY_TIMER_ENABLED)) return;
        int interval = cfg.getInt(KEY_TIMER_INTERVAL);
        if (interval <= 0) { cfg.putInt(KEY_TIMER_INTERVAL, DEFAULT_INTERVAL); interval = DEFAULT_INTERVAL; }
        if (world.getGameTime() % interval != 0) return;
        if (!cfg.contains(KEY_POS)) return;
        BlockPos pos = NbtUtils.readBlockPos(cfg.getCompound(KEY_POS));
        BlockInteractService.interact(world, maid, pos);
    }

    @Override
    public void interrupt(EntityMaid maid) {
        onCleanup(maid);
    }

    @Override @Nullable
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGui.blockInteractConfig(maid);
    }

    @Override
    public CompoundTag getConfigNbt(EntityMaid maid) {
        return config(maid);
    }

    // ── 执行器 ──

    /**
     * 执行器 — 仅心跳维持任务存活。
     * 实际交互由 {@link #tick} (定时器) 和 InteractTriggerPacket (手动按键) 触发。
     */
    public static IExecutor executor() {
        return (w, m, p, d) -> TaskResult.SUCCESS;
    }
}
