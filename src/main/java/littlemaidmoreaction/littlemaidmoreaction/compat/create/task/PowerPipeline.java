package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.navigation.NavigationMemory;
import littlemaidmoreaction.littlemaidmoreaction.task.runtime.TaskStateMachine;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动力齿轮 (v62: pipelineData 管理私有状态).
 */
public final class PowerPipeline extends TaskStateMachine<PowerPipeline.State> {

    enum State { SEARCHING, NAVIGATING, POWERING }

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "power"; }
    @Override public boolean needsGameTick() { return true; }

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
    protected void cleanup(EntityMaid maid) {
        stopPower(maid);
        super.cleanup(maid);
        NavigationMemory.clearAllNav(maid);
    }

    @Override
    protected void onExit(State state, EntityMaid maid) {
        if (state == State.POWERING) {
            stopPower(maid);
        }
    }

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        return switch (s) {
            case SEARCHING -> {
                BlockPos target = PowerService.findTarget(world, maid.blockPosition());
                if (target == null) yield null;
                pipelineData(maid).putString("pos", target.toShortString());
                navigateTo(maid, target);
                yield State.NAVIGATING;
            }
            case NAVIGATING -> {
                BlockPos target = readPos(maid);
                if (target == null) yield State.SEARCHING;
                if (!PowerService.isTargetBlock(world.getBlockState(target).getBlock()))
                    yield State.SEARCHING;
                if (arrived(maid, target)) {
                    PowerService.providePower(world, target, getRpm(maid));
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
                PowerService.providePower(world, target, getRpm(maid));
                if (world.getGameTime() % 20 == 0) maid.swing(InteractionHand.MAIN_HAND);
                yield null;
            }
        };
    }

    private float getRpm(EntityMaid maid) {
        return pipelineData(maid).contains("rpm")
            ? pipelineData(maid).getFloat("rpm") : PowerService.DEFAULT_RPM;
    }

    private BlockPos readPos(EntityMaid maid) {
        String s = pipelineData(maid).getString("pos");
        if (s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            return new BlockPos(
                Integer.parseInt(p[0].trim()),
                Integer.parseInt(p[1].trim()),
                Integer.parseInt(p[2].trim()));
        } catch (Exception e) { return null; }
    }

    private void stopPower(EntityMaid maid) {
        BlockPos pos = readPos(maid);
        if (pos != null) PowerService.stopPower((ServerLevel) maid.level(), pos);
    }

    private static void navigateTo(EntityMaid maid, BlockPos target) {
        NavigationMemory.setNavTarget(maid, target);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, 1.0F, 2);
    }

    private static boolean arrived(EntityMaid m, BlockPos p) {
        return p.distToCenterSqr(m.position()) < 9.0;
    }
}
