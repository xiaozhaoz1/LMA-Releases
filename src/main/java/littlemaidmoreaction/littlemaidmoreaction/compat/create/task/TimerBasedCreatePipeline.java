package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.navigation.NavigationMemory;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.io.IExecutor;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskPipeline;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;

import java.util.List;

/**
 * Create 计时器驱动 Pipeline 抽象基类 (v42)。
 *
 * <p>提取 PressPipeline/MixPipeline 共享的 tick 状态机骨架:
 * timer &gt; 0 = 工作中, timer &lt; 0 = 摸鱼, timer == 0 = 搜索+导航+配方检查。
 *
 * <p>子类需实现:
 * <ul>
 *   <li>{@link #taskType()} / {@link #steps()} — 任务标识</li>
 *   <li>{@link #findTarget(ServerLevel, BlockPos)} — 搜索目标方块</li>
 *   <li>{@link #hasRecipe(ServerLevel, BlockPos)} — 配方检查</li>
 *   <li>{@link #tickWorking(ServerLevel, EntityMaid, CompoundTag, int)} — 工作tick逻辑</li>
 * </ul>
 *
 * <p>子类可直接使用继承的方法: {@link #navigateTo}, {@link #arrived}, {@link #readPos},
 * {@link #cleanup}, {@link #workTicks}, {@link #tickIdling}, {@link #executor}.
 */
public abstract class TimerBasedCreatePipeline implements TaskPipeline {

    @Override public boolean isLongRunning() { return true; }
    @Override public void onCleanup(EntityMaid maid) { cleanup(maid); }
    @Override public void interrupt(EntityMaid maid) { NavigationMemory.clearAllNav(maid); }

    protected final String keyTimer;
    protected final String keyTarget;
    protected static final int IDLE_TICKS = -60;

    protected TimerBasedCreatePipeline(String keyTimer, String keyTarget) {
        this.keyTimer = keyTimer;
        this.keyTarget = keyTarget;
    }

    // ── 子类必须实现 ──

    @Override public abstract String taskType();
    @Override public abstract List<TaskStep> steps();

    /** 搜索目标方块位置 */
    protected abstract BlockPos findTarget(ServerLevel world, BlockPos maidPos);

    /** 检查目标位置是否有可处理配方 */
    protected abstract boolean hasRecipe(ServerLevel world, BlockPos target);

    /** tickWorking — 子类各自实现（Press/Mix 逻辑差异较大，不强行统一） */
    protected abstract void tickWorking(ServerLevel world, EntityMaid maid, CompoundTag d, int timer);

    // ── 共享实现 ──


    @Override public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) { return PipelineResult.ok(""); }

    /** 工作时间按好感等级: Lv3=1s Lv2=2s Lv1=3s Lv0=5s */
    protected int workTicks(EntityMaid maid) {
        return switch (maid.getFavorabilityManager().getLevel()) {
            case 3 -> 20;
            case 2 -> 40;
            case 1 -> 60;
            default -> 100;
        };
    }

    public IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) { tick(w, m); return TaskResult.CONTINUE; }
            @Override public void onStop(EntityMaid maid) { cleanup(maid); }
        };
    }

    /** 主 tick 状态机 */
    public void tick(ServerLevel world, EntityMaid maid) {
        var d = maid.getPersistentData();
        // v44: 取消检测
        if (TaskKeys.STATE_CANCELLED.equals(d.getString(TaskKeys.FLOW_STATE))) { cleanup(maid); return; }
        int timer = d.getInt(keyTimer);

        if (timer > 0) { tickWorking(world, maid, d, timer); return; }
        if (timer < 0) { tickIdling(d, timer); return; }

        // timer == 0: 搜索 + 决策
        BlockPos target = findTarget(world, maid.blockPosition());
        if (target == null) return;
        if (!arrived(maid, target)) { navigateTo(maid, target); return; }

        boolean has = hasRecipe(world, target);
        if (has) d.putString(keyTarget, target.toShortString());
        d.putInt(keyTimer, has ? workTicks(maid) : IDLE_TICKS);
    }

    /** 摸鱼倒计时: 负数递增到 0 */
    protected void tickIdling(CompoundTag d, int timer) {
        timer++;
        d.putInt(keyTimer, timer);
    }

    // ── 工具方法 ──

    protected void navigateTo(EntityMaid maid, BlockPos target) {
        NavigationMemory.setNavTarget(maid, target);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, 1.0F, 2);
    }

    protected boolean arrived(EntityMaid m, BlockPos p) { return p.distToCenterSqr(m.position()) < 9.0; }

    protected BlockPos readPos(CompoundTag d, String key) {
        String s = d.getString(key);
        if (s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception e) { return null; }
    }

    public void cleanup(EntityMaid maid) {
        var d = maid.getPersistentData();
        d.remove(keyTimer); d.remove(keyTarget);
        NavigationMemory.clearAllNav(maid);
    }
}
