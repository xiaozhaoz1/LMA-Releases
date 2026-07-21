package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.navigation.NavigationMemory;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskStateMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动力齿轮 (v47 迁移至 TaskStateMachine).
 *
 * <p>三状态循环 + v43 static map 缺陷修复:
 * <pre>
 * SEARCHING → NAVIGATING → POWERING → SEARCHING → ...
 * </pre>
 *
 * <p>v47: 消除 static ConcurrentHashMap，改用 NBT 持久化目标位置。
 */
public final class PowerPipeline extends TaskStateMachine<PowerPipeline.State> {

    enum State { SEARCHING, NAVIGATING, POWERING }

    static final String KEY_RPM = "lma_power_rpm";
    /** v47: 目标位置存 NBT 替代 v43 的 static ConcurrentHashMap */
    static final String KEY_POS = "lma_power_target";

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "power"; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
            State.SEARCHING,  Set.of(State.NAVIGATING),
            State.NAVIGATING, Set.of(State.POWERING),
            State.POWERING,   Set.of(State.SEARCHING, State.NAVIGATING)
        );
    }

    @Override
    public List<TaskStep> steps() {
        return List.of(new TaskStep("power", "提供动力", StepType.INTERACT, List.of()));
    }

    @Override
    public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) {
        return PipelineResult.ok("");
    }

    @Override
    protected void cleanup(EntityMaid maid) {
        stopPower(maid);
        super.cleanup(maid);
        maid.getPersistentData().remove(KEY_RPM);
        maid.getPersistentData().remove(KEY_POS);
        NavigationMemory.clearAllNav(maid);
    }

    @Override
    protected void onExit(State state, EntityMaid maid) {
        if (state == State.POWERING) {
            stopPower(maid);
        }
    }

    // ── 状态业务逻辑 ──

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        return switch (s) {
            case SEARCHING -> {
                BlockPos target = PowerService.findTarget(world, maid.blockPosition());
                if (target == null) yield null;
                writePos(maid, target);
                navigateTo(maid, target);
                yield State.NAVIGATING;
            }
            case NAVIGATING -> {
                BlockPos target = readPos(maid);
                if (target == null) yield State.SEARCHING;
                if (!PowerService.isTargetBlock(world.getBlockState(target).getBlock())) yield State.SEARCHING;
                if (arrived(maid, target)) {
                    float rpm = readRpm(maid);
                    PowerService.providePower(world, target, rpm);
                    yield State.POWERING;
                }
                navigateTo(maid, target);
                yield null;
            }
            case POWERING -> {
                BlockPos target = readPos(maid);
                if (target == null) { stopPower(maid); yield State.SEARCHING; }
                if (!PowerService.isTargetBlock(world.getBlockState(target).getBlock())) {
                    stopPower(maid); yield State.SEARCHING;
                }
                if (!arrived(maid, target)) { stopPower(maid); yield State.NAVIGATING; }
                float rpm = readRpm(maid);
                PowerService.providePower(world, target, rpm);
                if (world.getGameTime() % 20 == 0) maid.swing(InteractionHand.MAIN_HAND);
                yield null;
            }
        };
    }

    // ── 辅助 ──

    private float readRpm(EntityMaid maid) {
        var d = maid.getPersistentData();
        return d.contains(KEY_RPM) ? d.getFloat(KEY_RPM) : PowerService.DEFAULT_RPM;
    }

    private void writePos(EntityMaid maid, BlockPos pos) {
        maid.getPersistentData().putString(KEY_POS, pos.toShortString());
    }

    private BlockPos readPos(EntityMaid maid) {
        return readPos(maid.getPersistentData());
    }

    private BlockPos readPos(CompoundTag d) {
        String s = d.getString(KEY_POS);
        if (s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception e) { return null; }
    }

    private void stopPower(EntityMaid maid) {
        BlockPos pos = readPos(maid);
        ServerLevel level = (ServerLevel) maid.level();
        if (pos != null) PowerService.stopPower(level, pos);
    }

    private static void navigateTo(EntityMaid maid, BlockPos target) {
        NavigationMemory.setNavTarget(maid, target);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, 1.0F, 2);
    }

    private static boolean arrived(EntityMaid m, BlockPos p) {
        return p.distToCenterSqr(m.position()) < 9.0;
    }
}
