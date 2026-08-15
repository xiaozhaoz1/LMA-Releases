package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
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
 * 女仆冲压 — Depot/Basin 双路 (v62: pipelineData 管理私有状态).
 */
public final class PressPipeline extends MoveToBlockStateMachine<PressPipeline.State> implements TaskConfigurable {

    enum State { SEARCHING, NAVIGATING, WORKING }

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "press"; }

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
        return List.of(new TaskStep("press", "冲压塑形", StepType.INTERACT, List.of()));
    }

    @Override
    protected void onEnter(State state, ServerLevel world, EntityMaid maid) {
        if (state == State.WORKING) {
            // 好感度效率乘区 (原私有 workTicks 收编)
            pipelineData(maid).putInt("timer", (int) (100 / com.github.xiaozhaoz1.littlemaidmoreaction.task.service.MaidFavorability.workSpeedMultiplier(maid)));
        }
    }

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        return switch (s) {
            case SEARCHING -> {
                BlockPos target = PressService.findTarget(world, maid.blockPosition());
                if (target == null) yield null;
                writeTarget(maid, target);
                navigateTo(maid, target);
                yield State.NAVIGATING;
            }
            case NAVIGATING -> {
                BlockPos target = readTarget(maid);
                if (target == null) yield State.SEARCHING;
                if (arrived(maid, target)) {
                    boolean depot = world.getBlockEntity(target) instanceof DepotBlockEntity;
                    yield (depot ? PressService.hasDepotRecipe(world, target)
                                 : PressService.hasBasinRecipe(world, target))
                        ? State.WORKING : State.SEARCHING;
                }
                navigateTo(maid, target);
                yield null;
            }
            case WORKING -> {
                int timer = pipelineData(maid).getInt("timer");
                if (timer > 0) {
                    if (timer % 20 == 0) maid.swing(InteractionHand.MAIN_HAND);
                    pipelineData(maid).putInt("timer", timer - 1);
                    yield null;
                }
                BlockPos target = readTarget(maid);
                if (target != null) {
                    boolean depot = world.getBlockEntity(target) instanceof DepotBlockEntity;
                    if (depot) {
                        var h = PressService.readHeldItem(world, target);
                        PressService.findPressingRecipe(world, h)
                            .ifPresent(r -> PressService.executeDepotPress(world, target, r));
                    } else {
                        PressService.executeBasinPress(world, target);
                    }
                    PressService.playPressSound(world, target);
                }
                yield State.SEARCHING;
            }
        };
    }
}
