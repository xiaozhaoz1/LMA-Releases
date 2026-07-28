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
 * 女仆搅拌 — Basin MIXING 配方 (v62: pipelineData 管理私有状态).
 */
public final class MixPipeline extends TaskStateMachine<MixPipeline.State> {

    enum State { SEARCHING, NAVIGATING, WORKING }

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "mix"; }
    @Override public boolean needsGameTick() { return true; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
            State.SEARCHING,  Set.of(State.NAVIGATING),
            State.NAVIGATING, Set.of(State.WORKING, State.SEARCHING),
            State.WORKING,    Set.of(State.SEARCHING)
        );
    }

    @Override
    public List<TaskStep> steps() {
        return List.of(new TaskStep("mix", "搅拌混合", StepType.INTERACT, List.of()));
    }

    @Override
    protected void cleanup(EntityMaid maid) {
        super.cleanup(maid);
        NavigationMemory.clearAllNav(maid);
    }

    @Override
    protected void onEnter(State state, ServerLevel world, EntityMaid maid) {
        if (state == State.WORKING) {
            pipelineData(maid).putInt("timer", workTicks(maid));
        }
    }

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        return switch (s) {
            case SEARCHING -> {
                BlockPos target = MixService.findBasin(world, maid.blockPosition());
                if (target == null) yield null;
                pipelineData(maid).putString("target", target.toShortString());
                navigateTo(maid, target);
                yield State.NAVIGATING;
            }
            case NAVIGATING -> {
                BlockPos target = readPos(maid);
                if (target == null) yield State.SEARCHING;
                if (arrived(maid, target)) {
                    yield MixService.hasRecipe(world, target) ? State.WORKING : State.SEARCHING;
                }
                navigateTo(maid, target);
                yield null;
            }
            case WORKING -> {
                int timer = pipelineData(maid).getInt("timer");
                if (timer > 0) {
                    if (timer % 20 == 0) {
                        maid.swing(InteractionHand.MAIN_HAND);
                        BlockPos target = readPos(maid);
                        if (target != null) MixService.playMixSound(world, target);
                    }
                    pipelineData(maid).putInt("timer", timer - 1);
                    yield null;
                }
                BlockPos target = readPos(maid);
                if (target != null) MixService.executeMix(world, target);
                yield State.SEARCHING;
            }
        };
    }

    private int workTicks(EntityMaid maid) {
        return switch (maid.getFavorabilityManager().getLevel()) {
            case 3 -> 20; case 2 -> 40; case 1 -> 60; default -> 100;
        };
    }

    private BlockPos readPos(EntityMaid maid) {
        String s = pipelineData(maid).getString("target");
        if (s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception e) { return null; }
    }

    private static void navigateTo(EntityMaid maid, BlockPos target) {
        NavigationMemory.setNavTarget(maid, target);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, 1.0F, 2);
    }

    private static boolean arrived(EntityMaid m, BlockPos p) {
        return p.distToCenterSqr(m.position()) < 9.0;
    }
}
