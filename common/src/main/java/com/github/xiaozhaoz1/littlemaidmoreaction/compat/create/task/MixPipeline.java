package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.MoveToBlockStateMachine;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 女仆搅拌 — Basin MIXING 配方 (v62: pipelineData 管理私有状态).
 */
public final class MixPipeline extends MoveToBlockStateMachine<MixPipeline.State> implements TaskConfigurable {

    enum State { SEARCHING, NAVIGATING, WORKING }

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "mix"; }

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
    protected void onEnter(State state, ServerLevel world, EntityMaid maid) {
        if (state == State.WORKING) {
            // 好感度效率乘区 (原私有 workTicks 收编)
            pipelineData(maid).putInt("timer",
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.service.MaidFavorability.workTicks(maid, 100));
        }
    }

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        return switch (s) {
            case SEARCHING -> {
                BlockPos target = MixService.findBasin(world, maid.blockPosition());
                if (target == null) yield null;
                writeTarget(maid, target);
                navigateTo(maid, target);
                yield State.NAVIGATING;
            }
            case NAVIGATING -> {
                BlockPos target = readTarget(maid);
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
                        BlockPos target = readTarget(maid);
                        if (target != null) MixService.playMixSound(world, target);
                    }
                    pipelineData(maid).putInt("timer", timer - 1);
                    yield null;
                }
                BlockPos target = readTarget(maid);
                if (target != null) MixService.executeMix(world, target);
                yield State.SEARCHING;
            }
        };
    }
}
