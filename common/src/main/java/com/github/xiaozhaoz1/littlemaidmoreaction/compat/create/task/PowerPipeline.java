package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.MoveToBlockStateMachine;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动力齿轮 (v62: pipelineData 管理私有状态).
 */
public final class PowerPipeline extends MoveToBlockStateMachine<PowerPipeline.State> implements TaskConfigurable {

    enum State { SEARCHING, NAVIGATING, POWERING }

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "power"; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
            State.SEARCHING,  Set.of(State.NAVIGATING),
            State.NAVIGATING, Set.of(State.POWERING, State.SEARCHING),
            State.POWERING,   Set.of(State.SEARCHING, State.NAVIGATING)
        );
    }

    @Override
    public List<TaskStep> steps() {
    // ⚠ 改相位/状态时必须同步本步骤声明 — steps 是**用户可见的粗粒度语义**, 与内部状态枚举**不同层**;
    //    二者无自动校验 (6 态→4 步这类多对一是正常的), 详见错题 #291。
            
        return List.of(new TaskStep("power", "提供动力", StepType.INTERACT, List.of()));
    }

    @Override
    protected void cleanup(EntityMaid maid) {
        stopPower(maid);
        super.cleanup(maid);   // 基类 cleanup 含 NavigationMemory.clearAllNav
    }

    @Override
    public String targetKey() { return "pos"; }   // Power 历史键名 "pos" (非 "target")

    @Override
    protected void onExit(State state, EntityMaid maid) {
        if (state == State.POWERING) {
            stopPower(maid);
        }
    }

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        long now = world.getGameTime();
        return switch (s) {
            case SEARCHING -> {
                // v79.61x: 多目标收集 + 跳过集过滤 (卡死目标 60t 不重选) + 无目标气泡
                BlockPos target = PowerService.findTargets(world, maid.blockPosition())
                        .stream().filter(p -> !isSkipped(maid, p, now)).findFirst().orElse(null);
                if (target == null) {
                    if (com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.ThrottleUtil
                            .shouldFire(maid, "power_no_target", 600)) {
                        com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi
                                .showFail(maid, "附近没有可提供动力的机器");
                    }
                    yield null;
                }
                com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
                writeTarget(maid, target);
                navigateTo(maid, target);
                yield State.NAVIGATING;
            }
            case NAVIGATING -> {
                BlockPos target = readTarget(maid);
                if (target == null) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
                    yield State.SEARCHING;
                }
                if (!PowerService.isTargetBlock(world.getBlockState(target).getBlock())) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
                    yield State.SEARCHING;
                }
                if (arrived(maid, target)) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
                    PowerService.providePower(world, target, PowerService.DEFAULT_RPM);
                    yield State.POWERING;
                }
                // v79.61x 导航守护: 卡死 → 跳过集 + 重搜
                com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.track(maid, target, now);
                if (com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.isStuck(maid, now)) {
                    addSkip(maid, target, now);
                    com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.reset(maid);
                    yield State.SEARCHING;
                }
                navigateTo(maid, target);
                yield null;
            }
            case POWERING -> {
                BlockPos target = readTarget(maid);
                if (target == null) { stopPower(maid); yield State.SEARCHING; }
                if (!PowerService.isTargetBlock(world.getBlockState(target).getBlock())) {
                    stopPower(maid); yield State.SEARCHING;
                }
                if (!arrived(maid, target)) { stopPower(maid); yield State.NAVIGATING; }
                PowerService.providePower(world, target, PowerService.DEFAULT_RPM);
                com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.MaidSwing.onInterval(maid, 20);
                yield null;
            }
        };
    }

    private void stopPower(EntityMaid maid) {
        BlockPos pos = readTarget(maid);
        if (pos != null) PowerService.stopPower((ServerLevel) maid.level(), pos);
    }
}
