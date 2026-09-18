package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.MoveToBlockStateMachine;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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
 *
 * <p>v79.61x (用户裁定): ① 转换图补边 (NAVIGATING→SEARCHING / CRANKING→NAVIGATING —
 * 修图-码矛盾 latch + warn 刷屏 + 远距离空摇); ② implements TaskConfigurable
 * (跳过集/NavGuard 状态存 pl, 随终结自动清理); ③ 导航卡死 → 目标进跳过集 (60t) 换目标
 * (NavProgressGuard, 对齐 ChainHarvest SKIP_TTL); ④ 无目标静默 → 600t 节流气泡。
 */
public final class CrankPipeline extends MoveToBlockStateMachine<CrankPipeline.State> implements TaskConfigurable {

    enum State { SEARCHING, NAVIGATING, CRANKING }

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "crank"; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
            State.SEARCHING,  Set.of(State.NAVIGATING),
            State.NAVIGATING, Set.of(State.CRANKING, State.SEARCHING),
            State.CRANKING,   Set.of(State.SEARCHING, State.NAVIGATING)
        );
    }

    @Override
    public List<TaskStep> steps() {
    // ⚠ 改相位/状态时必须同步本步骤声明 — steps 是**用户可见的粗粒度语义**, 与内部状态枚举**不同层**;
    //    二者无自动校验 (6 态→4 步这类多对一是正常的), 详见错题 #291。
            
        return List.of(new TaskStep("turn", "摇动曲柄", StepType.INTERACT, List.of()));
    }

    @Override
    public PipelineResult validate(ServerLevel l, EntityMaid m, PipelineContext c) {
        return PipelineResult.ok("");
    }

    // ── 状态业务逻辑 ──

    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        long now = world.getGameTime();
        return switch (s) {
            case SEARCHING -> {
                // 多目标收集 (近→远) + 跳过集过滤 — 卡死目标 60t 内不重选
                BlockPos target = CrankService.findCranks(world, maid.blockPosition(), 3, 8)
                        .stream().filter(p -> !isSkipped(maid, p, now)).findFirst().orElse(null);
                if (target == null) {
                    // v79.61x: 无目标静默 → 600t 节流气泡
                    if (com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.ThrottleUtil
                            .shouldFire(maid, "crank_no_target", 600)) {
                        com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi
                                .showFail(maid, "附近没有可用的曲柄");
                    }
                    yield null;
                }
                com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
                navigateTo(maid, target);
                yield State.NAVIGATING;
            }
            case NAVIGATING -> {
                BlockPos target = CrankService.findCranks(world, maid.blockPosition(), 3, 8)
                        .stream().filter(p -> !isSkipped(maid, p, now)).findFirst().orElse(null);
                if (target == null) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
                    yield State.SEARCHING;
                }
                if (arrived(maid, target)) {
                    com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.clear(maid);
                    yield State.CRANKING;
                }
                // v79.61x 导航守护: 目标有效但走不到 → 跳过集 (60t) + 重搜 (换目标)
                com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.track(maid, target, now);
                if (com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.isStuck(maid, now)) {
                    addSkip(maid, target, now);
                    com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.NavProgressGuard.reset(maid);
                    yield State.SEARCHING;
                }
                navigateTo(maid, target);
                yield null;
            }
            case CRANKING -> {
                BlockPos target = CrankService.findCranks(world, maid.blockPosition(), 3, 8)
                        .stream().filter(p -> !isSkipped(maid, p, now)).findFirst().orElse(null);
                if (target == null) yield State.SEARCHING;
                if (!arrived(maid, target)) yield State.NAVIGATING;
                CrankService.crank(world, target);
                com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.output.maid.MaidSwing.onInterval(maid, 20);
                yield null;
            }
        };
    }}
