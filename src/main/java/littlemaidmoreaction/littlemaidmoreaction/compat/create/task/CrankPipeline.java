package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.navigation.NavigationMemory;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineContext;
import littlemaidmoreaction.littlemaidmoreaction.task.PipelineResult;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskStateMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 手摇曲柄 (v47 迁移至 TaskStateMachine).
 *
 * <p>三状态循环:
 * <pre>
 * SEARCHING → NAVIGATING → CRANKING → SEARCHING → ...
 * </pre>
 */
public final class CrankPipeline extends TaskStateMachine<CrankPipeline.State> {

    enum State { SEARCHING, NAVIGATING, CRANKING }

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "crank"; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
            State.SEARCHING,  Set.of(State.NAVIGATING),
            State.NAVIGATING, Set.of(State.CRANKING),
            State.CRANKING,   Set.of(State.SEARCHING)
        );
    }

    @Override
    public List<TaskStep> steps() {
        return List.of(new TaskStep("turn", "摇动曲柄", StepType.INTERACT, List.of()));
    }

    @Override
    public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) {
        return PipelineResult.ok("");
    }

    @Override
    protected void cleanup(EntityMaid maid) {
        super.cleanup(maid);
        NavigationMemory.clearAllNav(maid);
    }

    // ── 状态业务逻辑 ──

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        return switch (s) {
            case SEARCHING -> {
                BlockPos target = CrankService.findCrank(world, maid.blockPosition());
                if (target == null) yield null;
                navigateTo(maid, target);
                yield State.NAVIGATING;
            }
            case NAVIGATING -> {
                BlockPos target = CrankService.findCrank(world, maid.blockPosition());
                if (target == null) yield State.SEARCHING;
                if (arrived(maid, target)) yield State.CRANKING;
                navigateTo(maid, target);
                yield null;
            }
            case CRANKING -> {
                BlockPos target = CrankService.findCrank(world, maid.blockPosition());
                if (target == null) yield State.SEARCHING;
                if (!arrived(maid, target)) yield State.NAVIGATING;
                CrankService.crank(world, target);
                if (world.getGameTime() % 20 == 0) maid.swing(InteractionHand.MAIN_HAND);
                yield null;
            }
        };
    }

    // ── 辅助 ──

    private static void navigateTo(EntityMaid maid, BlockPos target) {
        NavigationMemory.setNavTarget(maid, target);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, 1.0F, 2);
    }

    private static boolean arrived(EntityMaid m, BlockPos p) {
        return p.distToCenterSqr(m.position()) < 9.0;
    }
}
